import { onCall, HttpsError } from "firebase-functions/v2/https";
import { openaiApiKey } from "./lib/config.js";
import {
  db,
  storage,
  buildAndCommitActionItems,
  ExtractedActionItem,
} from "./lib/firestore.js";
import { callOpenAI } from "./lib/openai.js";

// ── Schema & constants ───────────────────────────────────────────────────────

// Structured output JSON schema — strict: true guarantees the model always returns this shape.
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

// ── Private helpers ──────────────────────────────────────────────────────────

async function transcribeAudio(audioPath: string): Promise<string> {
  const [fileBuffer] = await storage.bucket().file(audioPath).download();
  const blob = new Blob([fileBuffer], { type: "audio/m4a" });
  const formData = new FormData();
  formData.append("file", blob, "audio.m4a");
  formData.append("model", "gpt-4o-mini-transcribe");
  formData.append("response_format", "text");
  const response = await fetch("https://api.openai.com/v1/audio/transcriptions", {
    method: "POST",
    headers: { Authorization: `Bearer ${openaiApiKey.value()}` },
    body: formData,
  });
  if (!response.ok) {
    throw new Error(`OpenAI transcription error: ${response.status}`);
  }
  return (await response.text()).trim();
}

async function generateTitle(transcript: string): Promise<string | null> {
  try {
    const data = await callOpenAI(
      [{
        role: "user",
        content: `Based on this transcript, reply with a single short title that identifies the content. Use at most 75 characters. Output only the title, no quotes or punctuation. Transcript:\n\n${transcript.substring(0, 2000)}`,
      }],
      { maxTokens: 60 }
    );
    const title = data.choices[0]?.message?.content?.trim();
    return title ? title.substring(0, 75) : null;
  } catch {
    return null;
  }
}

/**
 * Parses the structured JSON response `{ items: [...] }` returned by the model.
 * Validates date formats and logs warnings for malformed values.
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
        // Safety guard: drop deadline if it equals the dueDate date
        // (AI hallucination — treating appointment time as both fields)
        if (result.deadline && result.dueDate && result.dueDate.startsWith(result.deadline)) {
          console.warn(`Dropping redundant same-day deadline "${result.deadline}" (equals dueDate date)`);
          delete result.deadline;
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

/** Builds the timezone/date context string used in both extraction system prompts. */
function buildDateContext(tz: string): { today: string; dayOfWeek: string; currentTime: string } {
  const now = new Date();
  return {
    today: now.toLocaleDateString("en-CA", { timeZone: tz }),
    dayOfWeek: now.toLocaleDateString("en-US", { weekday: "long", timeZone: tz }),
    currentTime: now.toLocaleTimeString("en-US", {
      timeZone: tz,
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
    }),
  };
}

/**
 * Shared extraction call. Callers build their unique prompts; this handles
 * the OpenAI call and response parsing — eliminating the duplicated fetch/parse tail.
 */
async function callExtractionModel(
  systemPrompt: string,
  userMessage: string,
  truncatedLen: number
): Promise<ExtractedActionItem[]> {
  try {
    const data = await callOpenAI(
      [{ role: "system", content: systemPrompt }, { role: "user", content: userMessage }],
      { maxTokens: 2048, responseFormat: ACTION_ITEMS_SCHEMA }
    );
    const content = data.choices[0]?.message?.content?.trim();
    if (!content) return [];
    return parseActionItems(content, truncatedLen);
  } catch (err) {
    console.error("Extraction model call failed:", err);
    return [];
  }
}

async function extractActionItems(
  transcript: string,
  timezone: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 8000);
  const { today, dayOfWeek, currentTime } = buildDateContext(timezone);

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
4. One topic, both fields: when a SINGLE topic mentions a scheduled work time AND a separate final due date, output ONE task with both dueDate and deadline. Do NOT split into two tasks.
5. NEVER set deadline to the same date as dueDate. deadline must be a DIFFERENT, LATER date explicitly stated using trigger words ("by", "before", "due", "deadline is", "needs to be done by", "have it ready by"). A clock time alone never creates a deadline.
6. Appointments, meetings, and calls never have a deadline unless separately stated. "Dentist at 2:30 PM" → dueDate only, deadline null.
7. Set both date fields to null if no date/time is mentioned.
8. Never invent dates the speaker did not mention or imply.`;

  const userMessage = `Extract every task, action item, reminder, and to-do from this transcript. Include anything the speaker intends to do, needs to do, or wants to remember — even if phrased indirectly. Each task needs a complete, self-contained title (5–15 words). Return an empty items array only if there are genuinely no tasks.\n\nTranscript:\n\n${truncated}`;

  return callExtractionModel(systemPrompt, userMessage, truncated.length);
}

async function extractActionItemsAggressive(
  transcript: string,
  timezone: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 8000);
  const { today, dayOfWeek, currentTime } = buildDateContext(timezone);

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

DATE RULES:
- NEVER set deadline to the same date as dueDate. deadline is only for a SEPARATELY stated final due date using trigger words ("by [date]", "before [date]", "due [date]", "deadline is", "needs to be done by"). A clock time alone never creates a deadline.
- When one topic has both a work time ("Saturday at 9 AM") AND a deadline ("by Wednesday"), output ONE task with both fields — do NOT split into two tasks.
- Appointments, meetings, and calls only get dueDate (the scheduled time). Never add a deadline unless explicitly stated in the transcript.
- Set both to null if no date/time is mentioned.

DATE RESOLUTION:
- If today is ${dayOfWeek} and the speaker says "${dayOfWeek}", that means TODAY.
- "Next [weekday]" = at least 7 days away. "This [weekday]" = within the current week.
- "Morning" = 09:00, "afternoon" = 14:00, "evening" = 19:00, "tonight" = 20:00.
- "In X hours" = current time ${currentTime} + X hours.`;

  const userMessage = `Extract every task, intention, reminder, feature, improvement, or implementation item. Treat "we" as the speaker. Treat passive voice and feature descriptions as tasks. Be very inclusive — the user can delete unwanted tasks. Write complete, self-contained titles (5–15 words).\n\nTranscript:\n\n${truncated}`;

  return callExtractionModel(systemPrompt, userMessage, truncated.length);
}

// ── Exported Cloud Functions ─────────────────────────────────────────────────

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
    const tz = timezone || "UTC";

    if (!recordingId) {
      throw new HttpsError("invalid-argument", "recordingId is required");
    }

    const recordingRef = db
      .collection("users").doc(uid)
      .collection("recordings").doc(recordingId);

    const recordingDoc = await recordingRef.get();
    if (!recordingDoc.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }

    const audioPath = recordingDoc.data()!.audioPath as string;

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
      if (title) await recordingRef.update({ title });
    } catch (err) {
      console.error("Title generation failed:", err);
    }

    // Step 3: Extract action items
    let actionItemCount = 0;
    try {
      const items = await extractActionItems(transcription, tz);
      actionItemCount = items.length;
      await buildAndCommitActionItems(uid, recordingId, items, tz);
    } catch (err) {
      console.error("Action item extraction failed:", err);
    }

    return { success: true, actionItemCount };
  }
);

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

    const recordingSnap = await db
      .collection("users").doc(uid)
      .collection("recordings").doc(recordingId)
      .get();
    if (!recordingSnap.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }
    const transcript = (recordingSnap.data()?.transcription as string | undefined)?.trim();
    if (!transcript) {
      throw new HttpsError("failed-precondition", "Recording has no transcript");
    }

    const items = await extractActionItemsAggressive(transcript, tz);
    await buildAndCommitActionItems(uid, recordingId, items, tz);

    return { count: items.length };
  }
);
