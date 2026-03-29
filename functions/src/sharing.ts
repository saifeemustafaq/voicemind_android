import { onCall, HttpsError } from "firebase-functions/v2/https";
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
