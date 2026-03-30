import * as functionsV1 from "firebase-functions/v1";
import { CollectionReference } from "firebase-admin/firestore";
import { admin, db, storage } from "./lib/firestore.js";

/**
 * Auth trigger (Gen 1 — auth triggers are not available in Gen 2).
 * Creates a user profile document on first sign-in.
 */
export const onUserCreated = functionsV1.auth.user().onCreate(async (user) => {
  const { uid, displayName, email, photoURL } = user;
  await db.collection("users").doc(uid).set(
    {
      displayName: displayName || "",
      email: email || "",
      photoUrl: photoURL || "",
      discoverable: true,
    },
    { merge: true }
  );
});

/**
 * Batch-deletes all documents in a collection in chunks of 500.
 */
async function deleteCollection(collectionRef: CollectionReference): Promise<void> {
  let snap = await collectionRef.limit(500).get();
  while (!snap.empty) {
    const batch = db.batch();
    snap.docs.forEach((doc) => batch.delete(doc.ref));
    await batch.commit();
    if (snap.docs.length < 500) break;
    snap = await collectionRef.limit(500).get();
  }
}

/**
 * Auth trigger (Gen 1).
 * When a user deletes their account, comprehensively cleans up all their Firestore data,
 * cross-user sharing references, and Cloud Storage files.
 *
 * Cleanup order matters: cross-user references (A, B) are removed before subcollections (C)
 * are deleted, because A/B read myShares/sharedWithMe to find those references.
 */
export const onUserDeleted = functionsV1.auth.user().onDelete(async (user) => {
  const deletedUid = user.uid;

  // ── Phase A: Outgoing shares — delete recipients' inbox entries ──────────────
  try {
    const mySharesSnap = await db.collection(`users/${deletedUid}/myShares`).get();
    for (let i = 0; i < mySharesSnap.docs.length; i += 250) {
      const batch = db.batch();
      mySharesSnap.docs.slice(i, i + 250).forEach((shareDoc) => {
        const { recipientUid } = shareDoc.data() as { recipientUid: string };
        batch.delete(db.doc(`users/${recipientUid}/sharedWithMe/${shareDoc.id}`));
        batch.delete(shareDoc.ref);
      });
      await batch.commit();
    }
  } catch (err) {
    console.error("Phase A (outgoing shares) cleanup failed:", err);
  }

  // ── Phase B: Incoming shares — remove uid from owners' sharedWith arrays ─────
  try {
    const sharedWithMeSnap = await db.collection(`users/${deletedUid}/sharedWithMe`).get();
    for (let i = 0; i < sharedWithMeSnap.docs.length; i += 166) {
      const batch = db.batch();
      sharedWithMeSnap.docs.slice(i, i + 166).forEach((inboxDoc) => {
        const { ownerUid, itemId, itemType } = inboxDoc.data() as {
          ownerUid: string;
          itemId: string;
          itemType: string;
        };
        const itemCollection = itemType === "collectiveSummary"
          ? "collectiveSummaries"
          : itemType === "recording"
          ? "recordings"
          : "actionItems";
        batch.update(
          db.doc(`users/${ownerUid}/${itemCollection}/${itemId}`),
          { sharedWith: admin.firestore.FieldValue.arrayRemove(deletedUid) }
        );
        batch.delete(db.doc(`users/${ownerUid}/myShares/${inboxDoc.id}`));
        batch.delete(inboxDoc.ref);
      });
      await batch.commit();
    }
  } catch (err) {
    console.error("Phase B (incoming shares) cleanup failed:", err);
  }

  // ── Phase C: Delete all subcollections ───────────────────────────────────────
  const subcollections = [
    "recordings", "folders", "actionItems", "collectiveSummaries",
    "sharedWithMe", "myShares", "ntsCounters", "deviceTokens",
  ];
  for (const name of subcollections) {
    try {
      await deleteCollection(db.collection(`users/${deletedUid}/${name}`));
    } catch (err) {
      console.error(`Phase C (${name}) cleanup failed:`, err);
    }
  }

  // ── Phase D: Delete root-level documents ─────────────────────────────────────
  try {
    await Promise.all([
      db.doc(`users/${deletedUid}`).delete(),
      db.doc(`tasksTokens/${deletedUid}`).delete(),
      db.doc(`rateLimits/${deletedUid}`).delete(),
      db.doc(`calendarTokens/${deletedUid}`).delete(),
    ]);
  } catch (err) {
    console.error("Phase D (root docs) cleanup failed:", err);
  }

  // ── Phase E: Delete Cloud Storage files ──────────────────────────────────────
  try {
    const [files] = await storage.bucket().getFiles({ prefix: `users/${deletedUid}/` });
    for (let i = 0; i < files.length; i += 100) {
      await Promise.all(files.slice(i, i + 100).map((f) => f.delete()));
    }
  } catch (err) {
    console.error("Phase E (storage) cleanup failed:", err);
  }
});
