import { onCall, HttpsError } from "firebase-functions/v2/https";
import * as admin from "firebase-admin";
import { openaiApiKey } from "./lib/config.js";
import { db } from "./lib/firestore.js";
import { callOpenAI } from "./lib/openai.js";

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
    const { recordingId } = request.data as { recordingId?: string };

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

    const recording = recordingDoc.data()!;

    // Idempotency guard — return existing summary if already generated
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

    const data = await callOpenAI(
      [{
        role: "user",
        content: `Summarize the following transcript concisely in 3-5 sentences. Output only the summary, nothing else.\n\nTranscript:\n${transcription.substring(0, 4000)}`,
      }],
      { maxTokens: 300 }
    );
    const summary = data.choices[0]?.message?.content?.trim();

    if (!summary) {
      throw new HttpsError("internal", "Empty summary returned from OpenAI");
    }

    await recordingRef.update({ summary });
    return { success: true, summary };
  }
);

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
    const { recordingIds } = request.data as { recordingIds?: string[] };

    if (!recordingIds || recordingIds.length === 0) {
      throw new HttpsError("invalid-argument", "recordingIds is required and must not be empty");
    }

    const recordingsRef = db.collection("users").doc(uid).collection("recordings");
    const recordingDocs = await Promise.all(recordingIds.map((id) => recordingsRef.doc(id).get()));

    const recordingsWithTranscripts: Array<{
      id: string;
      title: string;
      transcription: string;
      createdAt: admin.firestore.Timestamp | null;
    }> = [];

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

    recordingsWithTranscripts.sort((a, b) => {
      const aMs = a.createdAt?.toMillis() ?? 0;
      const bMs = b.createdAt?.toMillis() ?? 0;
      return aMs - bMs;
    });

    const combined = recordingsWithTranscripts.map((r) => r.transcription).join("\n---\n");

    const responseData = await callOpenAI(
      [{
        role: "user",
        content: `Summarize the following combined transcripts from multiple voice recordings concisely. Highlight key themes, decisions, and action items across all recordings. Output only the summary.\n\n${combined.substring(0, 12000)}`,
      }],
      { maxTokens: 500 }
    );
    const summary = responseData.choices[0]?.message?.content?.trim();

    if (!summary) {
      throw new HttpsError("internal", "Empty summary returned from OpenAI");
    }

    const summaryRef = db.collection("users").doc(uid).collection("collectiveSummaries").doc();
    await summaryRef.set({
      summary,
      recordingIds: recordingsWithTranscripts.map((r) => r.id),
      recordingTitles: recordingsWithTranscripts.map((r) => r.title),
      isDeleted: false,
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
