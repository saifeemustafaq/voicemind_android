import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db } from "./lib/firestore.js";

const BATCH_LIMIT = 500;
const SUBCOLLECTIONS = [
  "recordings",
  "actionItems",
  "collectiveSummaries",
  "folders",
  "sharedWithMe",
  "myShares",
] as const;

/**
 * One-time admin callable that backfills `isDeleted: false` on every document
 * across all 6 soft-deletable subcollections for every user.
 *
 * Required before deploying Phase 2 query changes: Firestore's
 * `whereEqualTo("isDeleted", false)` does NOT match documents where the field
 * is absent, so without this migration existing data would vanish from the UI.
 *
 * Safe to call multiple times — only documents missing the field are updated.
 */
export const backfillIsDeleted = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "Caller must be authenticated.");
  }

  let totalUpdated = 0;
  const usersSnap = await db.collection("users").get();

  for (const userDoc of usersSnap.docs) {
    for (const sub of SUBCOLLECTIONS) {
      const snap = await userDoc.ref.collection(sub).get();

      let batch = db.batch();
      let batchCount = 0;

      for (const doc of snap.docs) {
        const data = doc.data();
        const updates: Record<string, unknown> = {};
        if (data.isDeleted === undefined) updates.isDeleted = false;
        if (sub === "sharedWithMe" && data.ownerItemDeleted === undefined) {
          updates.ownerItemDeleted = false;
        }
        if (Object.keys(updates).length > 0) {
          batch.update(doc.ref, updates);
          batchCount++;
          totalUpdated++;

          if (batchCount >= BATCH_LIMIT) {
            await batch.commit();
            batch = db.batch();
            batchCount = 0;
          }
        }
      }

      if (batchCount > 0) await batch.commit();
    }
  }

  return { totalUpdated };
});
