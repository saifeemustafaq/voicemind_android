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

    // Step 3: Extract action items
    try {
      const items = await extractActionItems(transcription);
      if (items.length > 0) {
        const batch = db.batch();
        const actionItemsRef = db
          .collection("users")
          .doc(uid)
          .collection("actionItems");

        for (const item of items.slice(0, 5)) {
          const docRef = actionItemsRef.doc();
          batch.set(docRef, {
            title: item.substring(0, 200),
            completed: false,
            recordingId: recordingId,
            createdAt: admin.firestore.FieldValue.serverTimestamp(),
          });
        }
        await batch.commit();
      }
    } catch (err) {
      console.error("Action item extraction failed:", err);
    }

    return { success: true };
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

async function extractActionItems(transcript: string): Promise<string[]> {
  const truncated = transcript.substring(0, 3000);
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
            content: `Extract only explicit action items from this transcript: reminders, commitments, deadlines, or phrases like 'I need to', 'I should', 'don't forget', 'remind me to'. One short phrase per item. Skip vague or purely conversational content. Return a JSON array of strings only; if none, return []. No other text. Transcript:\n\n${truncated}`,
          },
        ],
        max_tokens: 150,
      }),
    }
  );

  if (!response.ok) return [];

  const data = (await response.json()) as Record<string, any>;
  const content = data.choices?.[0]?.message?.content?.trim();
  if (!content) return [];

  try {
    const parsed = JSON.parse(content);
    if (Array.isArray(parsed)) {
      return parsed.filter((item: unknown) => typeof item === "string" && item.length > 0);
    }
  } catch {
    console.error("Failed to parse action items JSON:", content);
  }

  return [];
}
