import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";
import { google } from "googleapis";

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

const openaiApiKey = defineSecret("OPENAI_API_KEY");
const googleClientSecret = defineSecret("GOOGLE_CLIENT_SECRET");

const db = admin.firestore();
const storage = admin.storage();

const WEB_CLIENT_ID =
  "685270102033-tupn4a0mm03k7pdrnd1lhlv53gbq605t.apps.googleusercontent.com";

interface TranscribeRequest {
  recordingId: string;
  timezone?: string;
}

/**
 * Single entry point called by the Android app after audio upload.
 * Pipeline: transcribe -> auto-title -> extract action items.
 * All three steps run server-side; the client just waits for completion.
 */
export const processRecording = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 120 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { recordingId, timezone } = request.data as TranscribeRequest;
    const tz = timezone || "America/Los_Angeles";

    if (!recordingId) {
      throw new HttpsError("invalid-argument", "recordingId is required");
    }

    const recordingRef = db
      .collection("users")
      .doc(uid)
      .collection("recordings")
      .doc(recordingId);

    const recordingDoc = await recordingRef.get();
    if (!recordingDoc.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }

    const recording = recordingDoc.data()!;
    const audioPath = recording.audioPath as string;

    // Step 1: Transcribe
    let transcription: string;
    try {
      transcription = await transcribeAudio(audioPath);
      await recordingRef.update({ transcription });
    } catch (err) {
      console.error("Transcription failed:", err);
      return { success: false, error: "Transcription failed" };
    }

    // Step 2: Auto-title
    try {
      const title = await generateTitle(transcription);
      if (title) {
        await recordingRef.update({ title });
      }
    } catch (err) {
      console.error("Title generation failed:", err);
    }

    // Step 3: Extract action items (with optional date/deadline)
    let actionItemCount = 0;
    try {
      const items = await extractActionItems(transcription, tz);
      actionItemCount = items.length;
      if (items.length > 0) {
        const batch = db.batch();
        const actionItemsRef = db
          .collection("users")
          .doc(uid)
          .collection("actionItems");

        for (const item of items) {
          const docRef = actionItemsRef.doc();
          const doc: Record<string, unknown> = {
            title: item.title.substring(0, 200),
            completed: false,
            recordingId: recordingId,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          };
          if (item.notes) {
            doc.notes = item.notes.substring(0, 500);
          }
          if (item.dueDate) {
            // Use DST-aware parsing: interpret the local clock time in the user's
            // actual timezone at the event date, not the current offset.
            const d = parseLocalDateTimeInTimezone(item.dueDate, tz);
            if (d) {
              doc.dueDate = admin.firestore.Timestamp.fromDate(d);
            }
          }
          if (item.deadline) {
            const d = parseDateAsNoonUtc(item.deadline);
            if (d && !isNaN(d.getTime())) {
              doc.deadline = admin.firestore.Timestamp.fromDate(d);
            }
          }
          batch.set(docRef, doc);
        }
        await batch.commit();
      }
    } catch (err) {
      console.error("Action item extraction failed:", err);
    }

    return { success: true, actionItemCount };
  }
);

interface SummaryRequest {
  recordingId: string;
}

/**
 * On-demand summary generation. Called when the user taps the Summary tab
 * for the first time. Returns the existing summary if already generated
 * (idempotency guard) or creates one via GPT-4o-mini and persists it.
 */
export const generateSummary = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 60 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { recordingId } = request.data as SummaryRequest;

    if (!recordingId) {
      throw new HttpsError("invalid-argument", "recordingId is required");
    }

    const recordingRef = db
      .collection("users")
      .doc(uid)
      .collection("recordings")
      .doc(recordingId);

    const recordingDoc = await recordingRef.get();
    if (!recordingDoc.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }

    const recording = recordingDoc.data()!;

    if (recording.summary) {
      return { success: true, summary: recording.summary as string };
    }

    const transcription = recording.transcription as string | undefined;
    if (!transcription) {
      throw new HttpsError(
        "failed-precondition",
        "Recording has no transcript to summarize"
      );
    }

    const truncated = transcription.substring(0, 4000);
    const response = await fetch(
      "https://api.openai.com/v1/chat/completions",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${openaiApiKey.value()}`,
        },
        body: JSON.stringify({
          model: "gpt-4o-mini",
          messages: [
            {
              role: "user",
              content: `Summarize the following transcript concisely in 3-5 sentences. Output only the summary, nothing else.\n\nTranscript:\n${truncated}`,
            },
          ],
          max_tokens: 300,
        }),
      }
    );

    if (!response.ok) {
      const errorText = await response.text();
      throw new HttpsError(
        "internal",
        `Summary generation failed: ${response.status} ${errorText}`
      );
    }

    const data = (await response.json()) as Record<string, any>;
    const summary = data.choices?.[0]?.message?.content?.trim();

    if (!summary) {
      throw new HttpsError("internal", "Empty summary returned from OpenAI");
    }

    await recordingRef.update({ summary });

    return { success: true, summary };
  }
);

interface CollectiveSummaryRequest {
  recordingIds: string[];
}

/**
 * Generates a collective summary from the combined transcripts of multiple recordings.
 * Stores the result in users/{uid}/collectiveSummaries.
 */
export const generateCollectiveSummary = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 120 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { recordingIds } = request.data as CollectiveSummaryRequest;

    if (!recordingIds || recordingIds.length === 0) {
      throw new HttpsError("invalid-argument", "recordingIds is required and must not be empty");
    }

    // Fetch all recording documents
    const recordingsRef = db.collection("users").doc(uid).collection("recordings");
    const recordingDocs = await Promise.all(recordingIds.map((id) => recordingsRef.doc(id).get()));

    const recordingsWithTranscripts: Array<{ id: string; title: string; transcription: string; createdAt: admin.firestore.Timestamp | null }> = [];
    const skippedCount = recordingDocs.reduce((count, doc) => {
      if (!doc.exists) return count + 1;
      const data = doc.data()!;
      if (!data.transcription) return count + 1;
      recordingsWithTranscripts.push({
        id: doc.id,
        title: (data.title as string) || "Untitled",
        transcription: data.transcription as string,
        createdAt: (data.createdAt as admin.firestore.Timestamp) || null,
      });
      return count;
    }, 0);

    if (recordingsWithTranscripts.length === 0) {
      throw new HttpsError(
        "failed-precondition",
        "None of the selected recordings have transcripts to summarize"
      );
    }

    // Sort by createdAt ascending
    recordingsWithTranscripts.sort((a, b) => {
      const aMs = a.createdAt?.toMillis() ?? 0;
      const bMs = b.createdAt?.toMillis() ?? 0;
      return aMs - bMs;
    });

    // Concatenate transcripts with separator
    const combined = recordingsWithTranscripts
      .map((r) => r.transcription)
      .join("\n---\n");

    // Truncate to ~12000 chars for gpt-4o-mini context limit
    const truncated = combined.substring(0, 12000);

    const response = await fetch(
      "https://api.openai.com/v1/chat/completions",
      {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${openaiApiKey.value()}`,
        },
        body: JSON.stringify({
          model: "gpt-4o-mini",
          messages: [
            {
              role: "user",
              content: `Summarize the following combined transcripts from multiple voice recordings concisely. Highlight key themes, decisions, and action items across all recordings. Output only the summary.\n\n${truncated}`,
            },
          ],
          max_tokens: 500,
        }),
      }
    );

    if (!response.ok) {
      const errorText = await response.text();
      throw new HttpsError(
        "internal",
        `Collective summary generation failed: ${response.status} ${errorText}`
      );
    }

    const data = (await response.json()) as Record<string, any>;
    const summary = data.choices?.[0]?.message?.content?.trim();

    if (!summary) {
      throw new HttpsError("internal", "Empty summary returned from OpenAI");
    }

    // Store in collectiveSummaries collection
    const summaryRef = db.collection("users").doc(uid).collection("collectiveSummaries").doc();
    await summaryRef.set({
      summary,
      recordingIds: recordingsWithTranscripts.map((r) => r.id),
      recordingTitles: recordingsWithTranscripts.map((r) => r.title),
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    return {
      success: true,
      summaryId: summaryRef.id,
      summary,
      skippedCount,
    };
  }
);

async function transcribeAudio(audioPath: string): Promise<string> {
  const bucket = storage.bucket();
  const file = bucket.file(audioPath);

  const [fileBuffer] = await file.download();

  const blob = new Blob([fileBuffer], { type: "audio/m4a" });
  const formData = new FormData();
  formData.append("file", blob, "audio.m4a");
  formData.append("model", "gpt-4o-mini-transcribe");
  formData.append("response_format", "text");

  const response = await fetch(
    "https://api.openai.com/v1/audio/transcriptions",
    {
      method: "POST",
      headers: {
        Authorization: `Bearer ${openaiApiKey.value()}`,
      },
      body: formData,
    }
  );

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(`OpenAI transcription error: ${response.status} ${errorText}`);
  }

  return (await response.text()).trim();
}

async function generateTitle(transcript: string): Promise<string | null> {
  const truncated = transcript.substring(0, 2000);
  const response = await fetch(
    "https://api.openai.com/v1/chat/completions",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${openaiApiKey.value()}`,
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: [
          {
            role: "user",
            content: `Based on this transcript, reply with a single short title that identifies the content. Use at most 75 characters. Output only the title, no quotes or punctuation. Transcript:\n\n${truncated}`,
          },
        ],
        max_tokens: 60,
      }),
    }
  );

  if (!response.ok) return null;

  const data = (await response.json()) as Record<string, any>;
  const title = data.choices?.[0]?.message?.content?.trim();
  return title ? title.substring(0, 75) : null;
}

interface ExtractedActionItem {
  title: string;
  dueDate?: string;
  deadline?: string;
  notes?: string;
}

// Structured output JSON schema — used in both extraction functions.
// strict: true guarantees the model always returns this exact shape.
const ACTION_ITEMS_SCHEMA = {
  type: "json_schema",
  json_schema: {
    name: "action_items",
    strict: true,
    schema: {
      type: "object",
      properties: {
        items: {
          type: "array",
          items: {
            type: "object",
            properties: {
              title: { type: "string" },
              notes: { anyOf: [{ type: "string" }, { type: "null" }] },
              deadline: { anyOf: [{ type: "string" }, { type: "null" }] },
              dueDate: { anyOf: [{ type: "string" }, { type: "null" }] },
            },
            required: ["title", "notes", "deadline", "dueDate"],
            additionalProperties: false,
          },
        },
      },
      required: ["items"],
      additionalProperties: false,
    },
  },
};

const DEADLINE_RE = /^\d{4}-\d{2}-\d{2}$/;
const DUE_DATE_RE = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}$/;

/**
 * Parses the structured JSON response `{ items: [...] }` returned by the model.
 * Validates date formats and logs warnings for malformed values.
 * @param raw        The raw JSON string from the model.
 * @param transcriptLen  Used only for the zero-task warning heuristic.
 */
function parseActionItems(raw: string, transcriptLen: number): ExtractedActionItem[] {
  try {
    const parsed = JSON.parse(raw);
    const arr = parsed?.items;
    if (!Array.isArray(arr)) {
      console.error("Structured output missing items array:", raw.substring(0, 300));
      return [];
    }

    const results = arr
      .map((item: unknown): ExtractedActionItem | null => {
        if (typeof item !== "object" || item === null) return null;
        const obj = item as Record<string, unknown>;
        if (typeof obj.title !== "string" || !obj.title.trim()) return null;

        const result: ExtractedActionItem = { title: obj.title.trim() };

        if (typeof obj.notes === "string" && obj.notes.trim()) {
          result.notes = obj.notes.trim();
        }

        if (typeof obj.deadline === "string" && obj.deadline) {
          if (DEADLINE_RE.test(obj.deadline)) {
            result.deadline = obj.deadline;
          } else {
            console.warn(`Invalid deadline format skipped: "${obj.deadline}"`);
          }
        }

        if (typeof obj.dueDate === "string" && obj.dueDate) {
          if (DUE_DATE_RE.test(obj.dueDate)) {
            result.dueDate = obj.dueDate;
          } else {
            console.warn(`Invalid dueDate format skipped: "${obj.dueDate}"`);
          }
        }

        return result;
      })
      .filter((x): x is ExtractedActionItem => x !== null);

    if (results.length === 0 && transcriptLen > 100) {
      console.warn(
        `Zero tasks extracted from ${transcriptLen}-char transcript. ` +
        `Raw response snippet: ${raw.substring(0, 500)}`
      );
    }

    return results;
  } catch (err) {
    console.error("Failed to parse structured action items JSON:", err, raw.substring(0, 300));
    return [];
  }
}

/**
 * Converts a local datetime string (YYYY-MM-DDTHH:MM:SS, no offset) to a UTC Date
 * using the given IANA timezone, correctly accounting for DST at the event date.
 *
 * Why: If we bake the current UTC offset into the datetime string (e.g. -05:00)
 * but the event falls after a DST boundary (e.g. March 8 spring-forward), the
 * stored UTC timestamp ends up 1 hour wrong and the calendar event appears shifted.
 */
function parseLocalDateTimeInTimezone(localStr: string, timezone: string): Date | null {
  if (!localStr) return null;

  // Strip any trailing offset that GPT might still include (e.g. "+05:00", "-08:00", "Z")
  const stripped = localStr.replace(/([+-]\d{2}:\d{2}|Z)$/, "").trim();
  if (!stripped.includes("T")) return null;

  const [datePart, timePart] = stripped.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const timeSplit = (timePart || "00:00:00").split(":");
  const hour = parseInt(timeSplit[0] ?? "0", 10);
  const minute = parseInt(timeSplit[1] ?? "0", 10);

  if ([year, month, day, hour].some(isNaN)) return null;

  // Step 1: treat local components as UTC (will be off by the timezone offset)
  const approxUtc = new Date(Date.UTC(year, month - 1, day, hour, minute, 0));

  // Step 2: find what local time the target timezone shows at approxUtc
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(approxUtc);
  const get = (type: string) =>
    parseInt(parts.find((p) => p.type === type)?.value ?? "0", 10);

  const tzHour = get("hour") % 24; // "24" can appear for midnight in some locales
  const actualLocalMs = Date.UTC(get("year"), get("month") - 1, get("day"), tzHour, get("minute"), 0);
  const desiredLocalMs = Date.UTC(year, month - 1, day, hour, minute, 0);

  // Step 3: shift approxUtc by the delta so the result, when viewed in `timezone`, shows the desired local time
  const result = new Date(approxUtc.getTime() + (desiredLocalMs - actualLocalMs));
  return isNaN(result.getTime()) ? null : result;
}

/**
 * Parses a date-only string (YYYY-MM-DD) as noon UTC so the date
 * is always correct regardless of which timezone displays it.
 */
function parseDateAsNoonUtc(dateStr: string): Date | null {
  const d = new Date(`${dateStr}T12:00:00Z`);
  return isNaN(d.getTime()) ? null : d;
}

async function extractActionItems(
  transcript: string,
  timezone: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 8000);
  const now = new Date();
  const today = now.toLocaleDateString("en-CA", { timeZone: timezone });
  const dayOfWeek = now.toLocaleDateString("en-US", {
    weekday: "long",
    timeZone: timezone,
  });
  const currentTime = now.toLocaleTimeString("en-US", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });

  const systemPrompt = `You are a smart personal assistant that extracts action items from voice transcripts. Think like a human assistant who deeply understands intent.

Right now it is ${dayOfWeek}, ${today} at ${currentTime} in the ${timezone} timezone. Use this to resolve relative references like "this Friday", "next Monday", "tomorrow", "in 2 hours", "this afternoon", "later tonight", "end of week", etc.

TITLE RULES — write imperative, self-contained titles of 5–15 words:
- Include the WHO, WHAT, and WHERE/WHY when mentioned in the transcript.
- Preserve specific names, companies, phone numbers, and places from the transcript.
- Bad: "Call dentist" | Good: "Call Dr. Patel's office to reschedule Thursday cleaning"
- Bad: "Send report" | Good: "Send Q1 sales report to Sarah by email"
- Bad: "Buy stuff" | Good: "Buy 2 gallons of milk and eggs from Trader Joe's"
- The title must make sense standalone without reading the transcript.

NOTES RULES — capture actionable details that belong in a Google Tasks description:
- Include phone numbers, addresses, URLs, account numbers, reference codes mentioned.
- Include the reason/context: why this task exists, what depends on it.
- Include constraints: budget limits, specific requirements, who to contact.
- Keep to 1–4 sentences. Paraphrase naturally; do not quote verbatim.
- Set to null if no meaningful detail exists beyond what the title already says.

DATE FIELDS:
- "deadline" (YYYY-MM-DD): the date something must be FINISHED/DELIVERED/COMPLETED by.
- "dueDate" (YYYY-MM-DDTHH:MM:SS): when you will WORK ON, ATTEND, or DO it — only when a specific clock time is mentioned. Output the user's local time exactly as stated, no UTC conversion, no timezone offset.

DEADLINE triggers: "by [date]", "before [date]", "due [date]", "no later than", "deadline is", "finish by", "have it ready by", "needs to be done by"

DUE DATE triggers: "meeting at [time]", "appointment at [time]", "call at [time]", "I'll do it at [time]", "scheduled for [time]", any activity pinned to a specific clock time

DATE RESOLUTION:
- If today is Tuesday and speaker says "Tuesday", that means TODAY (not next week).
- "Next [weekday]" always means the upcoming occurrence at least 7 days away.
- "This [weekday]" means the nearest upcoming occurrence within the current week.
- "This weekend" = nearest Saturday/Sunday. "This weekend" on Friday = tomorrow.
- "Morning" = 09:00, "afternoon" = 14:00, "evening" = 19:00, "tonight" = 20:00, "noon" = 12:00.
- "In X hours" = current time + X hours (current time is ${currentTime}).
- "End of day" = today at 17:00. "End of week" = this Friday. "End of month" = last day of current month.

KEY RULES:
1. Date-only context (no specific time) → use "deadline".
2. Specific clock time mentioned → use "dueDate".
3. Ambiguous date without time → prefer "deadline".
4. A task can have BOTH (e.g. "work on it Tuesday at 2pm, due Friday").
5. Set both date fields to null if no date/time is mentioned.
6. Never invent dates the speaker did not mention or imply.`;

  const response = await fetch(
    "https://api.openai.com/v1/chat/completions",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${openaiApiKey.value()}`,
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: [
          {
            role: "system",
            content: systemPrompt,
          },
          {
            role: "user",
            content: `Extract every task, action item, reminder, and to-do from this transcript. Include anything the speaker intends to do, needs to do, or wants to remember — even if phrased indirectly. Each task needs a complete, self-contained title (5–15 words). Return an empty items array only if there are genuinely no tasks.\n\nTranscript:\n\n${truncated}`,
          },
        ],
        max_tokens: 2048,
        response_format: ACTION_ITEMS_SCHEMA,
      }),
    }
  );

  if (!response.ok) {
    console.error(
      "OpenAI action-item request failed:",
      response.status,
      await response.text()
    );
    return [];
  }

  const data = (await response.json()) as Record<string, any>;
  const content = data.choices?.[0]?.message?.content?.trim();
  if (!content) return [];

  return parseActionItems(content, truncated.length);
}

// ---------------------------------------------------------------------------
// Retry action-item extraction (user-initiated, more aggressive prompt)
// ---------------------------------------------------------------------------

async function extractActionItemsAggressive(
  transcript: string,
  timezone: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 8000);
  const now = new Date();
  const today = now.toLocaleDateString("en-CA", { timeZone: timezone });
  const dayOfWeek = now.toLocaleDateString("en-US", {
    weekday: "long",
    timeZone: timezone,
  });
  const currentTime = now.toLocaleTimeString("en-US", {
    timeZone: timezone,
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });

  const systemPrompt = `The user has explicitly requested aggressive task extraction from this voice memo. You are a highly inclusive task extractor — err heavily on the side of capturing too many tasks rather than too few. The user can always delete ones they don't want.

Right now it is ${dayOfWeek}, ${today} at ${currentTime} in the ${timezone} timezone. Use this to resolve "this Friday", "next Monday", "tomorrow", "in 2 hours", "this afternoon", "later tonight", etc.

SPEAKER INTERPRETATION:
- Treat the speaker as responsible for everything discussed.
- "we need to", "we should", "we want to", "we are doing" → the speaker's personal task.
- "I", "me", "my" → straightforward personal task.
- Passive voice ("the report needs to be sent") → the speaker will do it.

WHAT TO EXTRACT — include ALL of these:
1. Direct tasks: "call the bank", "send the email", "book the flight"
2. Implied intentions: "I should probably...", "I need to think about...", "I was thinking of..."
3. Soft reminders: "don't forget to...", "I want to eventually...", "at some point..."
4. Future plans: "next week I'll...", "I have to...", "I'm planning to..."
5. Technical/engineering work: features, improvements, bug fixes, implementations
6. Feature descriptions: "when X happens, Y should occur" → task to implement it
7. Follow-ups: "I need to check on...", "follow up with...", "ask John about..."
8. Pending decisions: "we haven't decided on X yet" → task to decide on X
9. Anything a reasonable person would put on a to-do list

TITLE RULES — write imperative, self-contained titles of 5–15 words:
- Include specific names, companies, phone numbers, places from the transcript.
- Bad: "Call dentist" | Good: "Call Dr. Patel's office to reschedule Thursday cleaning"
- Bad: "Fix bug" | Good: "Fix null pointer crash on user profile screen for Android"
- The title must make sense standalone without reading the transcript.

NOTES RULES — capture actionable details useful as a task description:
- Phone numbers, addresses, URLs, account numbers, reference codes.
- The reason this task exists and what depends on it.
- Set to null if no detail exists beyond what the title already says.

DATE FIELDS:
- "deadline" (YYYY-MM-DD): must be FINISHED/DELIVERED by this date.
- "dueDate" (YYYY-MM-DDTHH:MM:SS): when you will DO/ATTEND it — only when a specific clock time is mentioned. Local time, no UTC conversion, no offset.

DATE RESOLUTION:
- If today is ${dayOfWeek} and the speaker says "${dayOfWeek}", that means TODAY.
- "Next [weekday]" = at least 7 days away. "This [weekday]" = within the current week.
- "Morning" = 09:00, "afternoon" = 14:00, "evening" = 19:00, "tonight" = 20:00.
- "In X hours" = current time ${currentTime} + X hours.
- Set both to null if no date/time is mentioned.`;

  const response = await fetch(
    "https://api.openai.com/v1/chat/completions",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${openaiApiKey.value()}`,
      },
      body: JSON.stringify({
        model: "gpt-4o-mini",
        messages: [
          { role: "system", content: systemPrompt },
          {
            role: "user",
            content: `Extract every task, intention, reminder, feature, improvement, or implementation item. Treat "we" as the speaker. Treat passive voice and feature descriptions as tasks. Be very inclusive — the user can delete unwanted tasks. Write complete, self-contained titles (5–15 words).\n\nTranscript:\n\n${truncated}`,
          },
        ],
        max_tokens: 2048,
        response_format: ACTION_ITEMS_SCHEMA,
      }),
    }
  );

  if (!response.ok) {
    console.error(
      "OpenAI aggressive action-item request failed:",
      response.status,
      await response.text()
    );
    return [];
  }

  const data = (await response.json()) as Record<string, any>;
  const content = data.choices?.[0]?.message?.content?.trim();
  if (!content) return [];

  return parseActionItems(content, truncated.length);
}

export const retryExtractActionItems = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 60 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "Not authenticated");
    }
    const uid = request.auth.uid;
    const { recordingId, timezone } = request.data as {
      recordingId: string;
      timezone?: string;
    };
    if (!recordingId) {
      throw new HttpsError("invalid-argument", "recordingId is required");
    }
    const tz = timezone ?? "UTC";

    // Read transcript from Firestore
    const recordingSnap = await db
      .collection("users")
      .doc(uid)
      .collection("recordings")
      .doc(recordingId)
      .get();
    if (!recordingSnap.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }
    const transcript = (recordingSnap.data()?.transcription as string | undefined)?.trim();
    if (!transcript) {
      throw new HttpsError("failed-precondition", "Recording has no transcript");
    }

    // Extract with aggressive prompt
    const items = await extractActionItemsAggressive(transcript, tz);

    if (items.length > 0) {
      const batch = db.batch();
      const actionItemsRef = db
        .collection("users")
        .doc(uid)
        .collection("actionItems");

      for (const item of items) {
        const docRef = actionItemsRef.doc();
        const doc: Record<string, unknown> = {
          title: item.title.substring(0, 200),
          completed: false,
          recordingId,
          createdAt: admin.firestore.FieldValue.serverTimestamp(),
        };
        if (item.notes) {
          doc.notes = item.notes.substring(0, 500);
        }
        if (item.dueDate) {
          const d = parseLocalDateTimeInTimezone(item.dueDate, tz);
          if (d) {
            doc.dueDate = admin.firestore.Timestamp.fromDate(d);
          }
        }
        if (item.deadline) {
          const d = parseDateAsNoonUtc(item.deadline);
          if (d && !isNaN(d.getTime())) {
            doc.deadline = admin.firestore.Timestamp.fromDate(d);
          }
        }
        batch.set(docRef, doc);
      }
      await batch.commit();
    }

    return { count: items.length };
  }
);

// ---------------------------------------------------------------------------
// Google Tasks integration
// ---------------------------------------------------------------------------

function createOAuth2Client(refreshToken?: string) {
  const client = new google.auth.OAuth2(
    WEB_CLIENT_ID,
    googleClientSecret.value(),
    "" // redirect_uri not needed for token exchange with auth code
  );
  if (refreshToken) {
    client.setCredentials({ refresh_token: refreshToken });
  }
  return client;
}

interface TasksAuthRequest {
  authCode: string;
}

/**
 * Exchanges a Google authorization code (with tasks scope)
 * for a refresh token and stores it securely for server-side Tasks sync.
 */
export const exchangeTasksAuthCode = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { authCode } = request.data as TasksAuthRequest;

    if (!authCode) {
      throw new HttpsError("invalid-argument", "authCode is required");
    }

    const oauth2Client = createOAuth2Client();

    let tokens;
    try {
      const response = await oauth2Client.getToken(authCode);
      tokens = response.tokens;
    } catch (err) {
      console.error("Token exchange failed:", err);
      throw new HttpsError(
        "internal",
        "Failed to exchange authorization code"
      );
    }

    if (!tokens.refresh_token) {
      throw new HttpsError(
        "internal",
        "No refresh token received. The user may need to revoke access and reconnect."
      );
    }

    await db.collection("tasksTokens").doc(uid).set({
      refreshToken: tokens.refresh_token,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await db.collection("users").doc(uid).set(
      { tasksConnected: true },
      { merge: true }
    );

    return { success: true };
  }
);

/**
 * Revokes the stored Google Tasks refresh token and cleans up.
 */
export const disconnectTasks = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const tokenDoc = await db.collection("tasksTokens").doc(uid).get();

    if (tokenDoc.exists) {
      const refreshToken = tokenDoc.data()?.refreshToken as string | undefined;
      if (refreshToken) {
        try {
          const oauth2Client = createOAuth2Client(refreshToken);
          await oauth2Client.revokeToken(refreshToken);
        } catch (err) {
          console.warn("Token revocation failed (may already be revoked):", err);
        }
      }
      await db.collection("tasksTokens").doc(uid).delete();
    }

    await db.collection("users").doc(uid).set(
      { tasksConnected: false },
      { merge: true }
    );

    return { success: true };
  }
);

/**
 * Firestore trigger that syncs actionItem changes to Google Tasks.
 * Creates, updates, or deletes tasks when title/dueDate/deadline/completed changes.
 */
export const syncActionItemToGoogleTasks = onDocumentWritten(
  {
    document: "users/{uid}/actionItems/{itemId}",
    secrets: [googleClientSecret],
  },
  async (event) => {
    const uid = event.params.uid;
    const itemId = event.params.itemId;

    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    // Guard: if the document still exists and only googleTaskId/calendarEventId changed, skip
    // (prevents infinite loop when we write back the task/event ID)
    if (before && after) {
      const beforeCopy = { ...before };
      const afterCopy = { ...after };
      delete beforeCopy.googleTaskId;
      delete afterCopy.googleTaskId;
      delete beforeCopy.calendarEventId;
      delete afterCopy.calendarEventId;
      if (JSON.stringify(beforeCopy) === JSON.stringify(afterCopy)) {
        return;
      }
    }

    const tokenDoc = await db.collection("tasksTokens").doc(uid).get();
    if (!tokenDoc.exists) return;

    const refreshToken = tokenDoc.data()?.refreshToken as string | undefined;
    if (!refreshToken) return;

    const oauth2Client = createOAuth2Client(refreshToken);
    const tasks = google.tasks({ version: "v1", auth: oauth2Client });
    const calendar = google.calendar({ version: "v3", auth: oauth2Client });

    const oldTaskId = before?.googleTaskId as string | undefined;
    const taskId = (after?.googleTaskId ?? oldTaskId) as string | undefined;
    const oldCalEventId = before?.calendarEventId as string | undefined;
    const calEventId = (after?.calendarEventId ?? oldCalEventId) as string | undefined;

    // Document deleted
    if (!after) {
      if (taskId) {
        await deleteGoogleTask(tasks, taskId, uid);
      }
      if (calEventId) {
        await deleteCalendarEvent(calendar, calEventId, uid);
      }
      return;
    }

    const dueDate = after.dueDate as admin.firestore.Timestamp | undefined;
    const deadline = after.deadline as admin.firestore.Timestamp | undefined;
    const title = (after.title as string) || "VoiceMind Task";
    const notes = (after.notes as string | undefined) || undefined;
    const completed = !!(after.completed as boolean);

    const hasDate = !!(dueDate || deadline);

    // Date removed and no existing task -> clean up calendar event if any
    if (!hasDate && !taskId) {
      if (calEventId) {
        await deleteCalendarEvent(calendar, calEventId, uid);
        await event.data?.after?.ref.update({ calendarEventId: admin.firestore.FieldValue.delete() });
      }
      return;
    }

    // Date removed -> delete task and calendar event
    if (!hasDate && taskId) {
      await deleteGoogleTask(tasks, taskId, uid);
      const removals: Record<string, unknown> = { googleTaskId: admin.firestore.FieldValue.delete() };
      if (calEventId) {
        await deleteCalendarEvent(calendar, calEventId, uid);
        removals.calendarEventId = admin.firestore.FieldValue.delete();
      }
      await event.data?.after?.ref.update(removals);
      return;
    }

    // ── Sync to Google Tasks ──────────────────────────────────────────────────
    const taskBody = buildGoogleTask(title, dueDate, deadline, notes, completed);

    if (taskId) {
      // Update existing task
      try {
        await tasks.tasks.update({
          tasklist: "@default",
          task: taskId,
          requestBody: taskBody,
        });
      } catch (err: any) {
        if (err.code === 404 || err.status === 404) {
          console.warn(`Google Task ${taskId} not found, creating new one`);
          await createAndStoreTask(tasks, taskBody, event, itemId);
        } else if (err.code === 401 || err.status === 401) {
          await handleTokenExpired(uid);
        } else {
          console.error("Google Task update failed:", err);
        }
      }
    } else {
      // Create new task
      await createAndStoreTask(tasks, taskBody, event, itemId);
    }

    // ── Sync to Google Calendar (only for dueDate items — preserves time) ────
    if (dueDate) {
      const eventBody = buildCalendarEvent(title, dueDate, notes);
      if (calEventId) {
        try {
          await calendar.events.update({
            calendarId: "primary",
            eventId: calEventId,
            requestBody: eventBody,
          });
        } catch (err: any) {
          if (err.code === 404 || err.status === 404) {
            await createAndStoreCalendarEvent(calendar, eventBody, event, itemId);
          } else if (err.code === 401 || err.status === 401 || err.code === 403 || err.status === 403) {
            console.warn(`Calendar scope not available for user ${uid}, skipping calendar sync`);
          } else {
            console.error("Calendar event update failed:", err);
          }
        }
      } else {
        await createAndStoreCalendarEvent(calendar, eventBody, event, itemId);
      }
    } else if (calEventId) {
      // dueDate was cleared but calendar event remains — delete it
      await deleteCalendarEvent(calendar, calEventId, uid);
      await event.data?.after?.ref.update({ calendarEventId: admin.firestore.FieldValue.delete() });
    }
  }
);

/**
 * Builds the Google Tasks requestBody from action item fields.
 * - deadline takes priority over dueDate for the Tasks due field (date-only).
 *   The exact time from dueDate is preserved separately via a Calendar event.
 * - completed maps to status: "completed" | "needsAction".
 */
function buildGoogleTask(
  title: string,
  dueDate?: admin.firestore.Timestamp,
  deadline?: admin.firestore.Timestamp,
  notes?: string,
  completed?: boolean
): Record<string, unknown> {
  const task: Record<string, unknown> = {
    title,
    notes: notes || "",
    status: completed ? "completed" : "needsAction",
  };

  if (deadline) {
    // Deadline takes priority — use date at noon UTC so the correct date shows
    const dateStr = deadline.toDate().toISOString().split("T")[0];
    task.due = `${dateStr}T12:00:00.000Z`;
  } else if (dueDate) {
    // No deadline: show dueDate's date in Tasks (time is stored in Calendar)
    const dateStr = dueDate.toDate().toISOString().split("T")[0];
    task.due = `${dateStr}T12:00:00.000Z`;
  }

  return task;
}

async function createAndStoreTask(
  tasks: ReturnType<typeof google.tasks>,
  taskBody: Record<string, unknown>,
  event: Parameters<Parameters<typeof onDocumentWritten>[1]>[0],
  itemId: string
) {
  try {
    const created = await tasks.tasks.insert({
      tasklist: "@default",
      requestBody: taskBody,
    });
    if (created.data.id) {
      await event.data?.after?.ref.update({ googleTaskId: created.data.id });
    }
  } catch (err: any) {
    if (err.code === 401 || err.status === 401) {
      await handleTokenExpired(event.params.uid);
    } else {
      console.error(`Failed to create Google Task for item ${itemId}:`, err);
    }
  }
}

async function deleteGoogleTask(
  tasks: ReturnType<typeof google.tasks>,
  taskId: string,
  uid: string
) {
  try {
    await tasks.tasks.delete({ tasklist: "@default", task: taskId });
  } catch (err: any) {
    if (err.code === 404 || err.status === 404) {
      console.warn(`Google Task ${taskId} already deleted`);
    } else if (err.code === 401 || err.status === 401) {
      await handleTokenExpired(uid);
    } else {
      console.error(`Failed to delete Google Task ${taskId}:`, err);
    }
  }
}

/**
 * Builds a Google Calendar event body for a dueDate action item.
 * Creates a 30-minute event at the exact time recorded.
 */
function buildCalendarEvent(
  title: string,
  dueDate: admin.firestore.Timestamp,
  notes?: string
): Record<string, unknown> {
  const start = dueDate.toDate();
  const end = new Date(start.getTime() + 30 * 60 * 1000);
  return {
    summary: title,
    description: notes || "",
    start: { dateTime: start.toISOString() },
    end: { dateTime: end.toISOString() },
  };
}

async function createAndStoreCalendarEvent(
  calendar: ReturnType<typeof google.calendar>,
  eventBody: Record<string, unknown>,
  event: Parameters<Parameters<typeof onDocumentWritten>[1]>[0],
  itemId: string
) {
  try {
    const created = await calendar.events.insert({
      calendarId: "primary",
      requestBody: eventBody,
    });
    if (created.data.id) {
      await event.data?.after?.ref.update({ calendarEventId: created.data.id });
    }
  } catch (err: any) {
    if (err.code === 401 || err.status === 401 || err.code === 403 || err.status === 403) {
      console.warn(`Calendar scope not available for user ${event.params.uid}, skipping calendar event creation`);
    } else {
      console.error(`Failed to create Calendar event for item ${itemId}:`, err);
    }
  }
}

async function deleteCalendarEvent(
  calendar: ReturnType<typeof google.calendar>,
  eventId: string,
  uid: string
) {
  try {
    await calendar.events.delete({ calendarId: "primary", eventId });
  } catch (err: any) {
    if (err.code === 404 || err.status === 404) {
      console.warn(`Calendar event ${eventId} already deleted`);
    } else if (err.code === 401 || err.status === 401 || err.code === 403 || err.status === 403) {
      console.warn(`Calendar scope not available for user ${uid}, cannot delete calendar event`);
    } else {
      console.error(`Failed to delete Calendar event ${eventId}:`, err);
    }
  }
}

async function handleTokenExpired(uid: string) {
  console.warn(`Google token expired/revoked for user ${uid}, disconnecting`);
  await db.collection("tasksTokens").doc(uid).delete();
  await db.collection("users").doc(uid).set(
    { tasksConnected: false },
    { merge: true }
  );
}
