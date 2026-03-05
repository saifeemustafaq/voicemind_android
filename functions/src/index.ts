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
    try {
      const items = await extractActionItems(transcription, tz);
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

    return { success: true };
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

function parseActionItems(raw: string): ExtractedActionItem[] {
  let text = raw.trim();

  text = text.replace(/^```(?:json)?\s*\n?/i, "").replace(/\n?```\s*$/i, "");
  text = text.trim();

  const tryParse = (json: string): ExtractedActionItem[] | null => {
    try {
      const parsed = JSON.parse(json);
      if (!Array.isArray(parsed)) return null;
      return parsed
        .map((item: unknown) => {
          if (typeof item === "string" && item.length > 0) {
            return { title: item };
          }
          if (
            typeof item === "object" &&
            item !== null &&
            typeof (item as Record<string, unknown>).title === "string"
          ) {
            const obj = item as Record<string, unknown>;
            const result: ExtractedActionItem = {
              title: obj.title as string,
            };
            if (typeof obj.dueDate === "string" && obj.dueDate)
              result.dueDate = obj.dueDate;
            if (typeof obj.deadline === "string" && obj.deadline)
              result.deadline = obj.deadline;
            if (typeof obj.notes === "string" && obj.notes)
              result.notes = obj.notes;
            return result;
          }
          return null;
        })
        .filter((x): x is ExtractedActionItem => x !== null && x.title.length > 0);
    } catch {
      return null;
    }
  };

  if (text.startsWith("[")) {
    const result = tryParse(text);
    if (result) return result;
  }

  const start = text.indexOf("[");
  const end = text.lastIndexOf("]");
  if (start !== -1 && end > start) {
    const result = tryParse(text.substring(start, end + 1));
    if (result) return result;
  }

  console.error("Failed to parse action items JSON:", raw);
  return [];
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
  const truncated = transcript.substring(0, 3000);
  const now = new Date();
  const today = now.toLocaleDateString("en-CA", { timeZone: timezone });
  const dayOfWeek = now.toLocaleDateString("en-US", {
    weekday: "long",
    timeZone: timezone,
  });

  const systemPrompt = `You are a smart personal assistant that extracts action items from voice transcripts. Think like a human assistant who deeply understands intent.

Today is ${dayOfWeek}, ${today}. The user's timezone is ${timezone}. Use this to resolve relative dates like "this Friday", "next Monday", "tomorrow", "end of week", etc.

Respond with ONLY a JSON array of objects. No markdown, no explanation, no code fences.

Each object has:
- "title" (string, required): a short phrase describing the task.
- "notes" (string, optional): 1-3 concise sentences of context from the transcript explaining WHY this task exists — the reason, background, or details behind it. Paraphrase naturally; do not quote verbatim. Omit if there is no meaningful context beyond the title itself.
- "deadline" (string, optional): ISO 8601 date YYYY-MM-DD. Use when the speaker indicates a task must be COMPLETED, FINISHED, or DELIVERED by a certain date. This is the "finish by" date.
- "dueDate" (string, optional): Local datetime in format "YYYY-MM-DDTHH:MM:SS" with NO timezone offset (e.g. "2026-02-27T17:00:00"). Use when the speaker indicates they will WORK ON, ATTEND, or DO something at a specific date AND time. Output the clock time the user stated, exactly as a local time — do NOT convert to UTC or append any offset.

DEADLINE — the date something must be finished by. Trigger phrases:
- "complete this by Friday" → deadline = that Friday
- "deliver the report by March 10" → deadline = March 10
- "needs to be done before next Monday" → deadline = next Monday
- "submit before the 15th" → deadline = the 15th of this/next month
- "due on Thursday" → deadline = that Thursday
- "have it ready by end of week" → deadline = that Friday
- "deadline is March 5" → deadline = March 5
- "no later than Tuesday" → deadline = that Tuesday
- "finish by tomorrow" → deadline = tomorrow's date
- "I need to get this done by next week" → deadline = next Friday
- Any "by [date]", "before [date]", "due [date]", "no later than [date]" pattern → deadline

DUE DATE — when you will work on it or attend it (requires a specific time). Trigger phrases:
- "I'll work on this Tuesday at 3pm" → dueDate = that Tuesday 15:00
- "meeting at 2pm on Wednesday" → dueDate = that Wednesday 14:00
- "let's do this Monday morning" → dueDate = that Monday 09:00
- "schedule a call for Friday at 10" → dueDate = that Friday 10:00
- "working on it this Saturday afternoon" → dueDate = this Saturday 14:00
- "appointment on March 3rd at 4:30" → dueDate = March 3 16:30
- "I have a thing at noon tomorrow" → dueDate = tomorrow 12:00
- Any "on [date] at [time]" or "at [time] on [date]" pattern with a scheduled activity → dueDate

KEY RULES:
1. If the context is about completion/delivery and only a date is mentioned (no specific time), use "deadline" (date-only).
2. If the context is about scheduling/attending and a specific time is mentioned, use "dueDate" (datetime).
3. If a date is mentioned but the context is ambiguous, prefer "deadline" since most spoken tasks are about getting things done.
4. A single task can have BOTH a deadline and a dueDate if the speaker mentions both (e.g., "work on the presentation Tuesday at 2pm, it's due by Friday").
5. If no date or time is mentioned at all, omit both fields entirely.
6. Do NOT invent dates that the speaker did not mention or imply.
7. When the speaker says vague time references like "morning", "afternoon", "evening", map them to 09:00, 14:00, 21:00 respectively.
8. "End of day" = deadline for today. "End of week" = deadline for this Friday. "End of month" = deadline for the last day of the current month.

Example output:
[{"title":"Buy groceries","notes":"Need to restock for the dinner party on Saturday.","deadline":"2026-03-01"},{"title":"Call dentist","notes":"Need to reschedule the cleaning appointment that was missed last week.","dueDate":"2026-03-02T14:00:00"},{"title":"Prepare presentation for client meeting","dueDate":"2026-03-04T10:00:00","deadline":"2026-03-05"}]`;

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
            content: `Identify anything the speaker intends to do, needs to do, or wants to remember to do. Use your best judgement — if something sounds like a task, action item, reminder, or to-do, include it even if it is not phrased with exact keywords. Look for intent, not just specific phrases. One short phrase per item. If there are genuinely no tasks, return [].\n\nTranscript:\n\n${truncated}`,
          },
        ],
        max_tokens: 1024,
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

  return parseActionItems(content);
}

// ---------------------------------------------------------------------------
// Retry action-item extraction (user-initiated, more aggressive prompt)
// ---------------------------------------------------------------------------

async function extractActionItemsAggressive(
  transcript: string,
  timezone: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 3000);
  const now = new Date();
  const today = now.toLocaleDateString("en-CA", { timeZone: timezone });
  const dayOfWeek = now.toLocaleDateString("en-US", {
    weekday: "long",
    timeZone: timezone,
  });

  const aggressivePrefix = `The user has explicitly requested task extraction from this transcript. Be more liberal and inclusive in identifying potential tasks. Look for:
- Direct tasks ("call the bank", "send the email")
- Implied intentions ("I should probably...", "I need to think about...")
- Soft reminders ("don't forget to...", "I want to eventually...")
- Future plans ("next week I'll...", "at some point I have to...")
- Anything that sounds like it could be actionable
Err on the side of inclusion — the user can always delete tasks they don't want.

`;

  const systemPrompt = `${aggressivePrefix}You are a smart personal assistant that extracts action items from voice transcripts. Think like a human assistant who deeply understands intent.

Today is ${dayOfWeek}, ${today}. The user's timezone is ${timezone}. Use this to resolve relative dates like "this Friday", "next Monday", "tomorrow", "end of week", etc.

Respond with ONLY a JSON array of objects. No markdown, no explanation, no code fences.

Each object has:
- "title" (string, required): a short phrase describing the task.
- "notes" (string, optional): 1-3 concise sentences of context from the transcript explaining WHY this task exists — the reason, background, or details behind it. Paraphrase naturally; do not quote verbatim. Omit if there is no meaningful context beyond the title itself.
- "deadline" (string, optional): ISO 8601 date YYYY-MM-DD. Use when the speaker indicates a task must be COMPLETED, FINISHED, or DELIVERED by a certain date.
- "dueDate" (string, optional): Local datetime in format "YYYY-MM-DDTHH:MM:SS" with NO timezone offset. Use when the speaker indicates they will WORK ON, ATTEND, or DO something at a specific date AND time.

If there are genuinely zero actionable items, return [].`;

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
            content: `Extract all tasks, intentions, reminders, and to-dos from this transcript. Be inclusive — if something could reasonably be a task, include it.\n\nTranscript:\n\n${truncated}`,
          },
        ],
        max_tokens: 1024,
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

  return parseActionItems(content);
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
    const transcript = recordingSnap.data()?.transcription as string | undefined;
    if (!transcript) {
      throw new HttpsError("failed-precondition", "Recording has no transcript");
    }

    // Idempotency: if items already exist for this recording, return count
    const existingSnap = await db
      .collection("users")
      .doc(uid)
      .collection("actionItems")
      .where("recordingId", "==", recordingId)
      .limit(1)
      .get();
    if (!existingSnap.empty) {
      return { count: existingSnap.size };
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
// Google Calendar integration
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

interface CalendarAuthRequest {
  authCode: string;
}

/**
 * Exchanges a Google authorization code (with calendar.events scope)
 * for a refresh token and stores it securely for server-side calendar sync.
 */
export const exchangeCalendarAuthCode = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { authCode } = request.data as CalendarAuthRequest;

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

    await db.collection("calendarTokens").doc(uid).set({
      refreshToken: tokens.refresh_token,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    });

    await db.collection("users").doc(uid).set(
      { calendarConnected: true },
      { merge: true }
    );

    return { success: true };
  }
);

/**
 * Revokes the stored Google Calendar refresh token and cleans up.
 */
export const disconnectCalendar = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const tokenDoc = await db.collection("calendarTokens").doc(uid).get();

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
      await db.collection("calendarTokens").doc(uid).delete();
    }

    await db.collection("users").doc(uid).set(
      { calendarConnected: false },
      { merge: true }
    );

    return { success: true };
  }
);

/**
 * Firestore trigger that syncs actionItem date changes to Google Calendar.
 * Creates, updates, or deletes calendar events when dueDate/deadline changes.
 */
export const syncActionItemToCalendar = onDocumentWritten(
  {
    document: "users/{uid}/actionItems/{itemId}",
    secrets: [googleClientSecret],
  },
  async (event) => {
    const uid = event.params.uid;
    const itemId = event.params.itemId;

    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    // Guard: if the document still exists and only calendarEventId changed, skip
    if (before && after) {
      const beforeCopy = { ...before };
      const afterCopy = { ...after };
      delete beforeCopy.calendarEventId;
      delete afterCopy.calendarEventId;
      if (JSON.stringify(beforeCopy) === JSON.stringify(afterCopy)) {
        return;
      }
    }

    const tokenDoc = await db.collection("calendarTokens").doc(uid).get();
    if (!tokenDoc.exists) return;

    const refreshToken = tokenDoc.data()?.refreshToken as string | undefined;
    if (!refreshToken) return;

    const oauth2Client = createOAuth2Client(refreshToken);
    const calendar = google.calendar({ version: "v3", auth: oauth2Client });

    const oldEventId = before?.calendarEventId as string | undefined;
    const newEventId = after?.calendarEventId as string | undefined;
    const eventId = newEventId || oldEventId;

    // Document deleted
    if (!after) {
      if (eventId) {
        await deleteCalendarEvent(calendar, eventId, uid);
      }
      return;
    }

    const dueDate = after.dueDate as admin.firestore.Timestamp | undefined;
    const deadline = after.deadline as admin.firestore.Timestamp | undefined;
    const title = (after.title as string) || "VoiceMind Task";
    const notes = (after.notes as string | undefined) || undefined;

    const hasDate = dueDate || deadline;

    // Date removed -> delete calendar event
    if (!hasDate && eventId) {
      await deleteCalendarEvent(calendar, eventId, uid);
      await event.data?.after?.ref.update({ calendarEventId: admin.firestore.FieldValue.delete() });
      return;
    }

    if (!hasDate) return;

    const calendarEvent = buildCalendarEvent(title, dueDate, deadline, notes);

    if (eventId) {
      // Update existing event
      try {
        await calendar.events.update({
          calendarId: "primary",
          eventId: eventId,
          requestBody: calendarEvent,
        });
      } catch (err: any) {
        if (err.code === 404 || err.status === 404) {
          console.warn(`Calendar event ${eventId} not found, creating new one`);
          await createAndStoreEvent(calendar, calendarEvent, event, itemId);
        } else if (err.code === 401 || err.status === 401) {
          await handleTokenExpired(uid);
        } else {
          console.error("Calendar event update failed:", err);
        }
      }
    } else {
      // Create new event
      await createAndStoreEvent(calendar, calendarEvent, event, itemId);
    }
  }
);

function buildCalendarEvent(
  title: string,
  dueDate?: admin.firestore.Timestamp,
  deadline?: admin.firestore.Timestamp,
  notes?: string
) {
  const event: Record<string, unknown> = {
    summary: title,
    description: notes || "",
  };

  if (dueDate) {
    const start = dueDate.toDate();
    const end = new Date(start.getTime() + 30 * 60 * 1000); // 30-min duration
    event.start = { dateTime: start.toISOString() };
    event.end = { dateTime: end.toISOString() };
  } else if (deadline) {
    const dateStr = deadline.toDate().toISOString().split("T")[0];
    event.start = { date: dateStr };
    event.end = { date: dateStr };
  }

  return event;
}

async function createAndStoreEvent(
  calendar: ReturnType<typeof google.calendar>,
  calendarEvent: Record<string, unknown>,
  event: Parameters<Parameters<typeof onDocumentWritten>[1]>[0],
  itemId: string
) {
  try {
    const created = await calendar.events.insert({
      calendarId: "primary",
      requestBody: calendarEvent,
    });
    if (created.data.id) {
      await event.data?.after?.ref.update({ calendarEventId: created.data.id });
    }
  } catch (err: any) {
    if (err.code === 401 || err.status === 401) {
      const uid = event.params.uid;
      await handleTokenExpired(uid);
    } else {
      console.error(`Failed to create calendar event for item ${itemId}:`, err);
    }
  }
}

async function deleteCalendarEvent(
  calendar: ReturnType<typeof google.calendar>,
  eventId: string,
  uid: string
) {
  try {
    await calendar.events.delete({
      calendarId: "primary",
      eventId: eventId,
    });
  } catch (err: any) {
    if (err.code === 404 || err.status === 404) {
      console.warn(`Calendar event ${eventId} already deleted`);
    } else if (err.code === 401 || err.status === 401) {
      await handleTokenExpired(uid);
    } else {
      console.error(`Failed to delete calendar event ${eventId}:`, err);
    }
  }
}

async function handleTokenExpired(uid: string) {
  console.warn(`Google token expired/revoked for user ${uid}, disconnecting`);
  await db.collection("calendarTokens").doc(uid).delete();
  await db.collection("users").doc(uid).set(
    { calendarConnected: false },
    { merge: true }
  );
}
