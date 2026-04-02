---
name: Phase 4 Execution
overview: "Execute Phase 4 of the code standards audit: refactor functions/src/sharing.ts by extracting three shared helpers (requireAuth, requireSharedWith, propagateOwnerItemDeleted), replacing all inline duplicates, and verifying Firestore collections match Android_Developer_Brief.md. Then mark completed items in codephase.md."
todos:
  - id: 4a-requireAuth
    content: Extract requireAuth helper and replace all 8 inline auth checks in sharing.ts
    status: done
  - id: 4a-requireSharedWith
    content: Extract requireSharedWith helper and replace 3 inline sharedWith checks
    status: done
  - id: 4a-propagate
    content: Extract propagateOwnerItemDeleted helper and replace batch logic in both soft-delete triggers
    status: done
  - id: 4a-verify
    content: Verify Firestore collections match Android_Developer_Brief.md S12 (already confirmed -- just document)
    status: done
  - id: 4a-mark
    content: Mark Phase 4 items as completed in codephase.md
    status: done
isProject: false
---

# Phase 4: Backend — `sharing.ts` Refactor

Single file: [functions/src/sharing.ts](functions/src/sharing.ts) (726 LOC). Pure refactor -- no behavior changes.

---

## 1. Extract `requireAuth` helper

**Problem:** 8 identical `if (!request.auth) throw ...` blocks across all `onCall` handlers.

Add after the existing `getItemCollection` helper (~line 37):

```typescript
function requireAuth(request: { auth?: { uid: string } }): string {
  if (!request.auth) throw new HttpsError("unauthenticated", "User must be signed in");
  return request.auth.uid;
}
```

Replace in all 8 handlers: `findUserByEmail`, `shareItem`, `revokeShare`, `dismissSharedItem`, `getSharedAudioUrl`, `duplicateSharedRecording`, `shareTask`, `generateTasksFromSharedRecording`.

Each becomes: `const callerUid = requireAuth(request);` -- removing the 3-line if-block + the separate `callerUid` assignment that follows it.

---

## 2. Extract `requireSharedWith` helper

**Problem:** 3 nearly identical blocks that read a document and verify `callerUid` is in `sharedWith` — in `getSharedAudioUrl` (lines 346-355), `duplicateSharedRecording` (lines 393-402), `generateTasksFromSharedRecording` (lines 561-570).

Add after `requireAuth`:

```typescript
async function requireSharedWith(
  ownerUid: string,
  collection: string,
  itemId: string,
  callerUid: string,
): Promise<FirebaseFirestore.DocumentData> {
  const doc = await db.doc(`users/${ownerUid}/${collection}/${itemId}`).get();
  if (!doc.exists) throw new HttpsError("not-found", "Item not found");
  const data = doc.data()!;
  if (!(data.sharedWith as string[] | undefined)?.includes(callerUid)) {
    throw new HttpsError("permission-denied", "Not shared with you");
  }
  return data;
}
```

Replace in all 3 call sites. Each becomes a single line, e.g.:

```typescript
const data = await requireSharedWith(ownerUid, "recordings", recordingId, callerUid);
```

The returned data replaces `recordingDoc.data()!` / `recordingData` in the downstream code.

---

## 3. Extract `propagateOwnerItemDeleted` helper

**Problem:** The myShares query + batched sharedWithMe update pattern is duplicated between `onCollectiveSummarySoftDeleted` (lines 622-641) and `onRecordingSoftDeleted` (lines 672-691).

Add after `requireSharedWith`:

```typescript
async function propagateOwnerItemDeleted(
  ownerUid: string,
  itemId: string,
  itemType: string,
  ownerItemDeleted: boolean,
): Promise<admin.firestore.QuerySnapshot> {
  const mySharesSnap = await db
    .collection(`users/${ownerUid}/myShares`)
    .where("itemId", "==", itemId)
    .where("itemType", "==", itemType)
    .where("isDeleted", "==", false)
    .get();

  if (!mySharesSnap.empty) {
    for (let i = 0; i < mySharesSnap.docs.length; i += 250) {
      const batch = db.batch();
      mySharesSnap.docs.slice(i, i + 250).forEach((shareDoc) => {
        const { recipientUid } = shareDoc.data() as { recipientUid: string };
        batch.update(
          db.doc(`users/${recipientUid}/sharedWithMe/${shareDoc.id}`),
          { ownerItemDeleted },
        );
      });
      await batch.commit();
    }
  }

  return mySharesSnap;
}
```

Returns the snapshot so `onRecordingSoftDeleted` can extract `recipientUids` for action item restoration.

`**onCollectiveSummarySoftDeleted**` becomes:

```typescript
const mySharesSnap = await propagateOwnerItemDeleted(uid, summaryId, "collectiveSummary", ownerItemDeleted);
if (mySharesSnap.empty) return;
```

(no more code after that -- complete replacement)

`**onRecordingSoftDeleted**` becomes:

```typescript
await propagateOwnerItemDeleted(uid, recordingId, "recording", ownerItemDeleted);
```

Followed by the existing action items logic (lines 693-723) which stays inline (recording-specific).

---

## 4. Verify Firestore collections

Verify collections used in `sharing.ts` match [Android_Developer_Brief.md](Android_Developer_Brief.md) S12:

- `recordings` under `users/{uid}/` -- S12 lists this
- `actionItems` under `users/{uid}/` -- S12 lists this
- `collectiveSummaries` under `users/{uid}/` -- S12 lists this
- `myShares` under `users/{uid}/` -- sharing infrastructure
- `sharedWithMe` under `users/{uid}/` -- sharing infrastructure
- `deviceTokens` under `users/{uid}/` -- FCM tokens
- `rateLimits/{uid}` at root level -- rate limiting

No invented collections or fields. All match existing data model.

---

## 5. Mark completed items in codephase.md

After implementation, mark all Phase 4A `[ ]` items as `[x]` in [codephase.md](codephase.md). Update the auth check count from "7" to "8" in the fix description.