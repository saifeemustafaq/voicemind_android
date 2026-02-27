import { setGlobalOptions } from "firebase-functions";
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { defineSecret } from "firebase-functions/params";
import * as admin from "firebase-admin";

admin.initializeApp();

setGlobalOptions({ maxInstances: 10 });

const openaiApiKey = defineSecret("OPENAI_API_KEY");

const db = admin.firestore();
const storage = admin.storage();

interface TranscribeRequest {
  recordingId: string;
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
    const { recordingId } = request.data as TranscribeRequest;

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
      const items = await extractActionItems(transcription);
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
          if (item.dueDate) {
            const d = new Date(item.dueDate);
            if (!isNaN(d.getTime())) {
              doc.dueDate = admin.firestore.Timestamp.fromDate(d);
            }
          }
          if (item.deadline) {
            const d = new Date(item.deadline);
            if (!isNaN(d.getTime())) {
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
            content: `Based on this transcript, reply with a single short title that identifies the content. Use at most 25 characters. Output only the title, no quotes or punctuation. Transcript:\n\n${truncated}`,
          },
        ],
        max_tokens: 30,
      }),
    }
  );

  if (!response.ok) return null;

  const data = (await response.json()) as Record<string, any>;
  const title = data.choices?.[0]?.message?.content?.trim();
  return title ? title.substring(0, 25) : null;
}

interface ExtractedActionItem {
  title: string;
  dueDate?: string;
  deadline?: string;
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

async function extractActionItems(
  transcript: string
): Promise<ExtractedActionItem[]> {
  const truncated = transcript.substring(0, 3000);
  const today = new Date().toISOString().split("T")[0];
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
            content: `You extract action items from voice transcripts. Today's date is ${today}. Respond with ONLY a JSON array of objects. Each object has: "title" (string, required), "dueDate" (ISO 8601 datetime string if a specific date AND time is mentioned, omit otherwise), "deadline" (ISO 8601 date string YYYY-MM-DD if a date-only deadline is mentioned, omit otherwise). When the speaker says things like "by Friday" or "before March 10", that is a deadline. When they say "at 3pm on Tuesday", that is a dueDate. If no date is mentioned, omit both fields. No markdown, no explanation. Example: [{"title":"Buy groceries","deadline":"2026-03-01"},{"title":"Call dentist at 2pm","dueDate":"2026-03-02T14:00:00"}]`,
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
