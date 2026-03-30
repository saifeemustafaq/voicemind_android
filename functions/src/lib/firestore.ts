import * as admin from "firebase-admin";
import { setGlobalOptions } from "firebase-functions";
import { parseLocalDateTimeInTimezone, parseDateAsNoonUtc } from "./dateUtils.js";

// Initialization — this module must be the first import in index.ts.
// It calls admin.initializeApp() and setGlobalOptions() before any function is registered.
admin.initializeApp();
setGlobalOptions({ maxInstances: 10 });

export { admin };
export const db = admin.firestore();
export const storage = admin.storage();

export interface ExtractedActionItem {
  title: string;
  dueDate?: string;
  deadline?: string;
  notes?: string;
}

/**
 * Builds Firestore action item documents from extracted items and commits them in a batch.
 * Centralises the document-building logic that was previously duplicated in
 * processRecording and retryExtractActionItems.
 */
export async function buildAndCommitActionItems(
  uid: string,
  recordingId: string,
  items: ExtractedActionItem[],
  tz: string,
  extraFields?: Record<string, unknown>,
): Promise<void> {
  if (items.length === 0) return;
  const batch = db.batch();
  const ref = db.collection("users").doc(uid).collection("actionItems");
  for (const item of items) {
    const docRef = ref.doc();
    const doc: Record<string, unknown> = {
      title: item.title.substring(0, 200),
      completed: false,
      recordingId,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
    };
    if (item.notes) doc.notes = item.notes.substring(0, 500);
    if (item.dueDate) {
      const d = parseLocalDateTimeInTimezone(item.dueDate, tz);
      if (d) doc.dueDate = admin.firestore.Timestamp.fromDate(d);
    }
    if (item.deadline) {
      const d = parseDateAsNoonUtc(item.deadline);
      if (d && !isNaN(d.getTime())) doc.deadline = admin.firestore.Timestamp.fromDate(d);
    }
    if (extraFields) Object.assign(doc, extraFields);
    batch.set(docRef, doc);
  }
  await batch.commit();
}
