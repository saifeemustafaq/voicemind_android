import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentUpdated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { randomBytes } from "crypto";
import { RATE_LIMIT_WINDOW_MS, RATE_LIMIT_MAX, openaiApiKey } from "./lib/config.js";
import { db, storage, buildAndCommitActionItems } from "./lib/firestore.js";
import { extractActionItems } from "./transcription.js";

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
    .where("isDeleted", "==", false)
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
    isDeleted: false,
    ownerItemDeleted: false,
  });

  batch.set(db.doc(`users/${callerUid}/myShares/${shareId}`), {
    recipientUid,
    recipientName: recipientData.displayName || "",
    recipientEmail: recipientData.email || "",
    itemType,
    itemId,
    sharedAt: admin.firestore.FieldValue.serverTimestamp(),
    isDeleted: false,
  });

  await batch.commit();

  // Send push notification to recipient (fire-and-forget; does not block the share)
  try {
    const tokensSnap = await db.collection(`users/${recipientUid}/deviceTokens`).get();
    if (!tokensSnap.empty) {
      const tokens = tokensSnap.docs.map((d) => (d.data() as { token: string }).token);
      const itemData = itemDoc.data() as Record<string, unknown>;
      const rawTitle =
        (itemData.title as string | undefined) ||
        (itemData.summary as string | undefined) ||
        "";
      const senderName = (callerData.displayName as string | undefined) || "Someone";
      const itemLabel = itemType === "collectiveSummary" ? "summary" : itemType!;
      const response = await admin.messaging().sendEachForMulticast({
        tokens,
        data: {
          title: `${senderName} shared a ${itemLabel} with you`,
          body: rawTitle.substring(0, 100),
          type: "shared_item",
          shareId,
          itemType: itemType!,
        },
      });
      const invalidIndices = response.responses
        .map((r, i) =>
          !r.success &&
          (r.error?.code === "messaging/invalid-registration-token" ||
            r.error?.code === "messaging/registration-token-not-registered")
            ? i
            : -1
        )
        .filter((i) => i >= 0);
      if (invalidIndices.length > 0) {
        const cleanBatch = db.batch();
        invalidIndices.forEach((i) => cleanBatch.delete(tokensSnap.docs[i].ref));
        await cleanBatch.commit();
      }
    }
  } catch (fcmErr) {
    console.error("FCM notification failed (non-blocking):", fcmErr);
  }

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

  const { recipientUid: storedRecipientUid } = myShareDoc.data() as {
    recipientUid: string;
  };

  if (storedRecipientUid !== recipientUid) {
    throw new HttpsError("invalid-argument", "recipientUid does not match share record");
  }

  const softDelete = {
    isDeleted: true,
    deletedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  const batch = db.batch();
  batch.update(db.doc(`users/${storedRecipientUid}/sharedWithMe/${shareId}`), softDelete);
  batch.update(myShareRef, softDelete);
  await batch.commit();

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

  const { ownerUid } = inboxDoc.data() as { ownerUid: string };

  const softDelete = {
    isDeleted: true,
    deletedAt: admin.firestore.FieldValue.serverTimestamp(),
  };
  const batch = db.batch();
  batch.update(inboxRef, softDelete);
  batch.update(db.doc(`users/${ownerUid}/myShares/${shareId}`), softDelete);
  await batch.commit();

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

export const duplicateSharedRecording = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { ownerUid, recordingId, destinationFolderId } = request.data as {
    ownerUid?: string;
    recordingId?: string;
    destinationFolderId?: string;
  };

  if (!ownerUid || !recordingId || !destinationFolderId) {
    throw new HttpsError("invalid-argument", "ownerUid, recordingId, and destinationFolderId are required");
  }

  // Verify caller has access to the shared recording
  const recordingDoc = await db.doc(`users/${ownerUid}/recordings/${recordingId}`).get();
  if (!recordingDoc.exists) {
    throw new HttpsError("not-found", "Recording not found");
  }

  const recordingData = recordingDoc.data()!;
  const sharedWith = (recordingData.sharedWith as string[]) ?? [];
  if (!sharedWith.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Not shared with you");
  }

  const newId = `rec-${Date.now()}-${randomBytes(4).toString("hex")}`;
  const newAudioPath = `users/${callerUid}/audio/${newId}.m4a`;
  const originalAudioPath = recordingData.audioPath as string;

  // Copy recording document (no sharedWith, no original folderId)
  const newRecordingRef = db.doc(`users/${callerUid}/recordings/${newId}`);
  await newRecordingRef.set({
    title: recordingData.title ?? "",
    transcription: recordingData.transcription ?? null,
    summary: recordingData.summary ?? null,
    durationSeconds: recordingData.durationSeconds ?? 0,
    folderId: destinationFolderId,
    audioPath: newAudioPath,
    isDeleted: false,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  // Copy audio file in Cloud Storage — clean up recording doc on failure
  try {
    await storage.bucket().file(originalAudioPath).copy(newAudioPath);
  } catch (err) {
    await newRecordingRef.delete();
    throw new HttpsError("internal", "Failed to copy audio file");
  }

  // Copy action items; clean up all partial writes on failure
  const actionItemsSnap = await db
    .collection(`users/${ownerUid}/actionItems`)
    .where("recordingId", "==", recordingId)
    .get();

  const copiedActionItemRefs: admin.firestore.DocumentReference[] = [];
  try {
    for (let i = 0; i < actionItemsSnap.docs.length; i += 500) {
      const batch = db.batch();
      actionItemsSnap.docs.slice(i, i + 500).forEach((doc) => {
        const d = doc.data();
        const newRef = db.collection(`users/${callerUid}/actionItems`).doc();
        copiedActionItemRefs.push(newRef);
        batch.set(newRef, {
          title: d.title ?? "",
          completed: d.completed ?? false,
          isDeleted: false,
          recordingId: newId,
          createdAt: d.createdAt ?? admin.firestore.FieldValue.serverTimestamp(),
          dueDate: d.dueDate ?? null,
          deadline: d.deadline ?? null,
          notes: d.notes ?? null,
          // googleTaskId, calendarEventId, sharedWith intentionally omitted
        });
      });
      await batch.commit();
    }
  } catch (err) {
    await newRecordingRef.delete().catch(() => {});
    await storage.bucket().file(newAudioPath).delete().catch(() => {});
    for (let i = 0; i < copiedActionItemRefs.length; i += 500) {
      const cleanupBatch = db.batch();
      copiedActionItemRefs.slice(i, i + 500).forEach((ref) => cleanupBatch.delete(ref));
      await cleanupBatch.commit().catch(() => {});
    }
    throw new HttpsError("internal", "Failed to copy action items");
  }

  return { success: true, newRecordingId: newId };
});

export const shareTask = onCall(async (request) => {
  if (!request.auth) {
    throw new HttpsError("unauthenticated", "User must be signed in");
  }

  const callerUid = request.auth.uid;
  const { taskId, recipientUid } = request.data as {
    taskId?: string;
    recipientUid?: string;
  };

  if (!taskId || !recipientUid) {
    throw new HttpsError("invalid-argument", "taskId and recipientUid are required");
  }
  if (recipientUid === callerUid) {
    throw new HttpsError("invalid-argument", "Cannot share with yourself");
  }

  const taskRef = db.doc(`users/${callerUid}/actionItems/${taskId}`);
  const taskDoc = await taskRef.get();
  if (!taskDoc.exists) {
    throw new HttpsError("not-found", "Task not found");
  }

  const recipientDoc = await db.doc(`users/${recipientUid}`).get();
  if (!recipientDoc.exists) {
    throw new HttpsError("not-found", "Recipient not found");
  }

  const callerDoc = await db.doc(`users/${callerUid}`).get();
  const callerDisplayName = callerDoc.data()?.displayName || "";

  const taskData = taskDoc.data()!;
  const docId = `shared-${callerUid}-${taskId}`;
  const targetRef = db.doc(`users/${recipientUid}/actionItems/${docId}`);

  const existing = await targetRef.get();
  if (existing.exists && existing.data()?.isDeleted !== true) {
    throw new HttpsError("already-exists", "Already shared with this user");
  }

  await targetRef.set({
    title: taskData.title ?? "",
    notes: taskData.notes ?? null,
    dueDate: taskData.dueDate ?? null,
    deadline: taskData.deadline ?? null,
    completed: false,
    isDeleted: false,
    sharedFromUid: callerUid,
    sharedFromName: callerDisplayName,
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });

  return { success: true };
});

export const generateTasksFromSharedRecording = onCall(
  { secrets: [openaiApiKey], timeoutSeconds: 120 },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const callerUid = request.auth.uid;
    const { ownerUid, recordingId, timezone } = request.data as {
      ownerUid?: string;
      recordingId?: string;
      timezone?: string;
    };

    if (!ownerUid || !recordingId) {
      throw new HttpsError("invalid-argument", "ownerUid and recordingId are required");
    }

    const recordingDoc = await db.doc(`users/${ownerUid}/recordings/${recordingId}`).get();
    if (!recordingDoc.exists) {
      throw new HttpsError("not-found", "Recording not found");
    }

    const recordingData = recordingDoc.data()!;
    const sharedWith = (recordingData.sharedWith as string[]) ?? [];
    if (!sharedWith.includes(callerUid)) {
      throw new HttpsError("permission-denied", "Not shared with you");
    }

    const transcription = recordingData.transcription as string | undefined;
    if (!transcription || transcription.trim().length === 0) {
      throw new HttpsError("invalid-argument", "Recording has no transcription");
    }

    const syntheticRecordingId = `shared:${ownerUid}:${recordingId}`;
    const existing = await db
      .collection(`users/${callerUid}/actionItems`)
      .where("recordingId", "==", syntheticRecordingId)
      .limit(1)
      .get();
    if (!existing.empty) {
      return { success: true, count: 0, alreadyGenerated: true };
    }

    const tz = timezone || "UTC";
    const items = await extractActionItems(transcription, tz);

    const ownerDoc = await db.doc(`users/${ownerUid}`).get();
    const ownerName = (ownerDoc.data()?.displayName as string | undefined) || "";

    await buildAndCommitActionItems(callerUid, syntheticRecordingId, items, tz, {
      sharedFromUid: ownerUid,
      sharedFromName: ownerName,
    });

    return { success: true, count: items.length };
  }
);

// ── Firestore trigger: collective summary soft delete cascade ─────────────────

/**
 * When an owner soft-deletes (or admin restores) a collective summary,
 * propagate the visibility change to all recipients' sharedWithMe entries
 * by toggling ownerItemDeleted.
 */
export const onCollectiveSummarySoftDeleted = onDocumentUpdated(
  { document: "users/{uid}/collectiveSummaries/{summaryId}" },
  async (event) => {
    const uid = event.params.uid;
    const summaryId = event.params.summaryId;
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    if (!before || !after) return;
    if (before.isDeleted === after.isDeleted) return;

    const ownerItemDeleted = after.isDeleted === true;

    const mySharesSnap = await db
      .collection(`users/${uid}/myShares`)
      .where("itemId", "==", summaryId)
      .where("itemType", "==", "collectiveSummary")
      .where("isDeleted", "==", false)
      .get();

    if (mySharesSnap.empty) return;

    for (let i = 0; i < mySharesSnap.docs.length; i += 250) {
      const batch = db.batch();
      mySharesSnap.docs.slice(i, i + 250).forEach((shareDoc) => {
        const { recipientUid } = shareDoc.data() as { recipientUid: string };
        batch.update(
          db.doc(`users/${recipientUid}/sharedWithMe/${shareDoc.id}`),
          { ownerItemDeleted }
        );
      });
      await batch.commit();
    }
  }
);

// ── Firestore trigger: recording soft delete cascade ──────────────────────────

/**
 * When an owner soft-deletes (or admin restores) a recording:
 *
 * On soft delete (isDeleted: false → true):
 *  1. Set ownerItemDeleted: true on all active recipients' sharedWithMe entries.
 *  2. Remove the sharedWith field from all linked action items so the trigger
 *     does not re-notify when action items are updated.
 *
 * On restore (isDeleted: true → false):
 *  1. Set ownerItemDeleted: false on all active recipients' sharedWithMe entries.
 *  2. Re-add recipient UIDs to sharedWith on linked action items via arrayUnion.
 */
export const onRecordingSoftDeleted = onDocumentUpdated(
  { document: "users/{uid}/recordings/{recordingId}" },
  async (event) => {
    const uid = event.params.uid;
    const recordingId = event.params.recordingId;
    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    if (!before || !after) return;
    if (before.isDeleted === after.isDeleted) return;

    const ownerItemDeleted = after.isDeleted === true;

    const mySharesSnap = await db
      .collection(`users/${uid}/myShares`)
      .where("itemId", "==", recordingId)
      .where("itemType", "==", "recording")
      .where("isDeleted", "==", false)
      .get();

    if (!mySharesSnap.empty) {
      for (let i = 0; i < mySharesSnap.docs.length; i += 250) {
        const batch = db.batch();
        mySharesSnap.docs.slice(i, i + 250).forEach((shareDoc) => {
          const { recipientUid } = shareDoc.data() as { recipientUid: string };
          batch.update(
            db.doc(`users/${recipientUid}/sharedWithMe/${shareDoc.id}`),
            { ownerItemDeleted }
          );
        });
        await batch.commit();
      }
    }

    const actionItemsSnap = await db
      .collection(`users/${uid}/actionItems`)
      .where("recordingId", "==", recordingId)
      .get();

    if (actionItemsSnap.empty) return;

    if (ownerItemDeleted) {
      for (let i = 0; i < actionItemsSnap.docs.length; i += 500) {
        const batch = db.batch();
        actionItemsSnap.docs.slice(i, i + 500).forEach((doc) => {
          batch.update(doc.ref, { sharedWith: admin.firestore.FieldValue.delete() });
        });
        await batch.commit();
      }
    } else {
      // Restore: re-add all active share recipients to sharedWith
      const recipientUids = mySharesSnap.docs.map(
        (shareDoc) => (shareDoc.data() as { recipientUid: string }).recipientUid
      );
      if (recipientUids.length === 0) return;
      for (let i = 0; i < actionItemsSnap.docs.length; i += 500) {
        const batch = db.batch();
        actionItemsSnap.docs.slice(i, i + 500).forEach((doc) => {
          batch.update(doc.ref, {
            sharedWith: admin.firestore.FieldValue.arrayUnion(...recipientUids),
          });
        });
        await batch.commit();
      }
    }
  }
);
