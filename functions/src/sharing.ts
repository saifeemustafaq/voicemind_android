import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentDeleted } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { RATE_LIMIT_WINDOW_MS, RATE_LIMIT_MAX } from "./lib/config.js";
import { db, storage } from "./lib/firestore.js";

// ── Rate limiting ────────────────────────────────────────────────────────────

/**
 * Firestore-based sliding-window rate limiter for findUserByEmail.
 * Stores state in the root-level `rateLimits/{uid}` document which is
 * denied to clients in firestore.rules — only Cloud Functions can write it.
 */
async function checkRateLimit(uid: string): Promise<void> {
  const ref = db.collection("rateLimits").doc(uid);
  await db.runTransaction(async (tx) => {
    const doc = await tx.get(ref);
    const now = Date.now();
    const stored = (doc.data()?.findUserByEmail as number[] | undefined) ?? [];
    const timestamps = stored.filter((t) => now - t < RATE_LIMIT_WINDOW_MS);
    if (timestamps.length >= RATE_LIMIT_MAX) {
      throw new HttpsError("resource-exhausted", "Too many lookups. Try again later.");
    }
    timestamps.push(now);
    tx.set(ref, { findUserByEmail: timestamps }, { merge: true });
  });
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function getItemCollection(itemType: string): string {
  if (itemType === "recording") return "recordings";
  if (itemType === "collectiveSummary") return "collectiveSummaries";
  throw new HttpsError("invalid-argument", `Invalid itemType: ${itemType}`);
}

async function removeFromActionItemsSharedWith(
  ownerUid: string,
  recordingId: string,
  targetUid: string
): Promise<void> {
  const snap = await db
    .collection(`users/${ownerUid}/actionItems`)
    .where("recordingId", "==", recordingId)
    .get();
  if (snap.empty) return;
  const batch = db.batch();
  snap.docs.forEach((doc) => {
    batch.update(doc.ref, {
      sharedWith: admin.firestore.FieldValue.arrayRemove(targetUid),
    });
  });
  await batch.commit();
}

// ── Exported Cloud Functions ─────────────────────────────────────────────────

export const findUserByEmail = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  await checkRateLimit(callerUid);

  const { email } = request.data as { email?: string };
  if (!email || typeof email !== "string") {
    throw new HttpsError("invalid-argument", "email is required");
  }

  try {
    const userRecord = await admin.auth().getUserByEmail(email.trim().toLowerCase());
    const profileDoc = await db.collection("users").doc(userRecord.uid).get();
    const discoverable = profileDoc.exists ? profileDoc.data()?.discoverable ?? true : true;

    if (!discoverable) return { found: false };

    return {
      found: true,
      uid: userRecord.uid,
      displayName: profileDoc.data()?.displayName || userRecord.displayName || "",
      email: userRecord.email || "",
    };
  } catch (err: unknown) {
    const errorCode = (err as { code?: string }).code;
    if (errorCode === "auth/user-not-found") return { found: false };
    throw new HttpsError("internal", "Lookup failed");
  }
});

export const shareItem = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { itemId, itemType, recipientUid } = request.data as {
    itemId?: string;
    itemType?: string;
    recipientUid?: string;
  };

  if (!itemId || !itemType || !recipientUid) {
    throw new HttpsError("invalid-argument", "itemId, itemType, and recipientUid are required");
  }
  if (recipientUid === callerUid) {
    throw new HttpsError("invalid-argument", "Cannot share with yourself");
  }

  const collectionName = getItemCollection(itemType);
  const itemRef = db.doc(`users/${callerUid}/${collectionName}/${itemId}`);
  const itemDoc = await itemRef.get();
  if (!itemDoc.exists) {
    throw new HttpsError("not-found", "Item not found");
  }

  const recipientDoc = await db.doc(`users/${recipientUid}`).get();
  if (!recipientDoc.exists) {
    throw new HttpsError("not-found", "Recipient not found");
  }

  const existingShares = await db
    .collection(`users/${callerUid}/myShares`)
    .where("itemId", "==", itemId)
    .where("recipientUid", "==", recipientUid)
    .get();
  if (!existingShares.empty) {
    throw new HttpsError("already-exists", "Already shared with this user");
  }

  const callerDoc = await db.doc(`users/${callerUid}`).get();
  const callerData = callerDoc.data() || {};
  const recipientData = recipientDoc.data() || {};
  const shareId = db.collection("_").doc().id;
  const batch = db.batch();

  batch.update(itemRef, { sharedWith: admin.firestore.FieldValue.arrayUnion(recipientUid) });

  batch.set(db.doc(`users/${recipientUid}/sharedWithMe/${shareId}`), {
    ownerUid: callerUid,
    ownerName: callerData.displayName || "",
    ownerEmail: callerData.email || "",
    itemType,
    itemId,
    sharedAt: admin.firestore.FieldValue.serverTimestamp(),
    isRead: false,
  });

  batch.set(db.doc(`users/${callerUid}/myShares/${shareId}`), {
    recipientUid,
    recipientName: recipientData.displayName || "",
    recipientEmail: recipientData.email || "",
    itemType,
    itemId,
    sharedAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  await batch.commit();

  if (itemType === "recording") {
    const actionItemsSnap = await db
      .collection(`users/${callerUid}/actionItems`)
      .where("recordingId", "==", itemId)
      .get();
    if (!actionItemsSnap.empty) {
      const aiBatch = db.batch();
      actionItemsSnap.docs.forEach((doc) => {
        aiBatch.update(doc.ref, { sharedWith: admin.firestore.FieldValue.arrayUnion(recipientUid) });
      });
      await aiBatch.commit();
    }
  }

  return { success: true, shareId };
});

export const revokeShare = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { shareId, recipientUid } = request.data as {
    shareId?: string;
    recipientUid?: string;
  };

  if (!shareId || !recipientUid) {
    throw new HttpsError("invalid-argument", "shareId and recipientUid are required");
  }

  const myShareRef = db.doc(`users/${callerUid}/myShares/${shareId}`);
  const myShareDoc = await myShareRef.get();
  if (!myShareDoc.exists) {
    throw new HttpsError("not-found", "Share not found");
  }

  const { itemId, itemType, recipientUid: storedRecipientUid } = myShareDoc.data() as {
    itemId: string;
    itemType: string;
    recipientUid: string;
  };

  if (storedRecipientUid !== recipientUid) {
    throw new HttpsError("invalid-argument", "recipientUid does not match share record");
  }

  const collectionName = getItemCollection(itemType);
  const itemRef = db.doc(`users/${callerUid}/${collectionName}/${itemId}`);

  const batch = db.batch();
  batch.update(itemRef, { sharedWith: admin.firestore.FieldValue.arrayRemove(storedRecipientUid) });
  batch.delete(db.doc(`users/${storedRecipientUid}/sharedWithMe/${shareId}`));
  batch.delete(myShareRef);
  await batch.commit();

  if (itemType === "recording") {
    await removeFromActionItemsSharedWith(callerUid, itemId, storedRecipientUid);
  }

  return { success: true };
});

export const dismissSharedItem = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { shareId } = request.data as { shareId?: string };

  if (!shareId) {
    throw new HttpsError("invalid-argument", "shareId is required");
  }

  const inboxRef = db.doc(`users/${callerUid}/sharedWithMe/${shareId}`);
  const inboxDoc = await inboxRef.get();
  if (!inboxDoc.exists) {
    throw new HttpsError("not-found", "Shared item not found");
  }

  const { ownerUid, itemId, itemType } = inboxDoc.data() as {
    ownerUid: string;
    itemId: string;
    itemType: string;
  };
  const collectionName = getItemCollection(itemType);
  const itemRef = db.doc(`users/${ownerUid}/${collectionName}/${itemId}`);

  const batch = db.batch();
  batch.update(itemRef, { sharedWith: admin.firestore.FieldValue.arrayRemove(callerUid) });
  batch.delete(inboxRef);
  batch.delete(db.doc(`users/${ownerUid}/myShares/${shareId}`));
  await batch.commit();

  if (itemType === "recording") {
    await removeFromActionItemsSharedWith(ownerUid, itemId, callerUid);
  }

  return { success: true };
});

export const getSharedAudioUrl = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { ownerUid, recordingId } = request.data as {
    ownerUid?: string;
    recordingId?: string;
  };

  if (!ownerUid || !recordingId) {
    throw new HttpsError("invalid-argument", "ownerUid and recordingId are required");
  }

  const recordingDoc = await db.doc(`users/${ownerUid}/recordings/${recordingId}`).get();
  if (!recordingDoc.exists) {
    throw new HttpsError("not-found", "Recording not found");
  }

  const data = recordingDoc.data()!;
  const sharedWith = (data.sharedWith as string[]) || [];
  if (!sharedWith.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Not shared with you");
  }

  const audioPath = data.audioPath as string;
  if (!audioPath) {
    throw new HttpsError("not-found", "Audio file path not found");
  }

  const [url] = await storage.bucket().file(audioPath).getSignedUrl({
    version: "v4",
    action: "read",
    expires: Date.now() + 60 * 60 * 1000,
  });

  return { url };
});

// ── Firestore trigger: recording deletion cascade ─────────────────────────────

/**
 * When an owner deletes a recording, clean up all sharing references so the
 * item disappears from every recipient's Shared Items list in real time.
 *
 * Steps:
 *  1. Read the deleted document's sharedWith array — return early if empty.
 *  2. Query myShares for this recording and delete both myShares and
 *     sharedWithMe inbox entries in batches of 250 shares (= 500 ops/batch).
 *  3. Remove the sharedWith field from all linked actionItems.
 */
export const onRecordingDeleted = onDocumentDeleted(
  { document: "users/{uid}/recordings/{recordingId}" },
  async (event) => {
    const uid = event.params.uid;
    const recordingId = event.params.recordingId;
    const data = event.data?.data();

    if (!data) return;

    const sharedWith = (data.sharedWith as string[] | undefined) ?? [];
    if (sharedWith.length === 0) return;

    // Delete myShares + sharedWithMe entries in batches (2 deletes per share)
    const mySharesSnap = await db
      .collection(`users/${uid}/myShares`)
      .where("itemId", "==", recordingId)
      .where("itemType", "==", "recording")
      .get();

    for (let i = 0; i < mySharesSnap.docs.length; i += 250) {
      const batch = db.batch();
      mySharesSnap.docs.slice(i, i + 250).forEach((shareDoc) => {
        const { recipientUid } = shareDoc.data() as { recipientUid: string };
        batch.delete(db.doc(`users/${recipientUid}/sharedWithMe/${shareDoc.id}`));
        batch.delete(shareDoc.ref);
      });
      await batch.commit();
    }

    // Remove sharedWith field from linked actionItems (field no longer needed)
    const actionItemsSnap = await db
      .collection(`users/${uid}/actionItems`)
      .where("recordingId", "==", recordingId)
      .get();

    for (let i = 0; i < actionItemsSnap.docs.length; i += 500) {
      const batch = db.batch();
      actionItemsSnap.docs.slice(i, i + 500).forEach((doc) => {
        batch.update(doc.ref, { sharedWith: admin.firestore.FieldValue.delete() });
      });
      await batch.commit();
    }
  }
);
