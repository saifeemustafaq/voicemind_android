---
name: Phase 1 Soft Delete
overview: "Complete the remaining Phase 1 tasks: create the backfillIsDeleted migration Cloud Function, register it in index.ts, and add all 10 required composite Firestore indexes to firestore.indexes.json."
todos:
  - id: create-migration
    content: Create functions/src/migration.ts with backfillIsDeleted callable Cloud Function
    status: pending
  - id: register-migration
    content: Add export for migration.ts in functions/src/index.ts
    status: pending
  - id: add-indexes
    content: Add 10 composite Firestore indexes to firestore.indexes.json
    status: pending
isProject: false
---

# Phase 1: Soft Delete — Data Models + Migration (Remaining Work)

## Status

The Android data model changes are **already complete** — all 6 models (`Recording`, `ActionItem`, `CollectiveSummary`, `Folder`, `SharedItem`, `MyShare`) already have `isDeleted: Boolean = false` and `deletedAt: Timestamp? = null`, and `SharedItem` has `ownerItemDeleted: Boolean = false`.

The two remaining tasks are the migration Cloud Function and Firestore indexes.

---

## Task 1: Create `functions/src/migration.ts`

Create a new file with an HTTPS-callable admin function `backfillIsDeleted` that:

- Validates the caller is authenticated
- Iterates all documents in the `users` collection
- For each user, iterates 6 subcollections: `recordings`, `actionItems`, `collectiveSummaries`, `folders`, `sharedWithMe`, `myShares`
- For each document where `isDeleted` field is absent (undefined), sets `isDeleted: false` via batched writes (max 500 per batch)
- Returns the total count of documents updated
- Uses the existing `db` export from `[functions/src/lib/firestore.ts](functions/src/lib/firestore.ts)` and follows the same patterns as `[functions/src/sharing.ts](functions/src/sharing.ts)` (imports `onCall`, `HttpsError` from `firebase-functions/v2/https`)

```typescript
import { onCall, HttpsError } from "firebase-functions/v2/https";
import { db } from "./lib/firestore.js";

const BATCH_LIMIT = 500;
const SUBCOLLECTIONS = [
  "recordings", "actionItems", "collectiveSummaries",
  "folders", "sharedWithMe", "myShares",
];

export const backfillIsDeleted = onCall(async (request) => {
  if (!request.auth) throw new HttpsError("unauthenticated", "...");

  let totalUpdated = 0;
  const usersSnap = await db.collection("users").get();

  for (const userDoc of usersSnap.docs) {
    for (const sub of SUBCOLLECTIONS) {
      const docs = await userDoc.ref.collection(sub).get();
      let batch = db.batch();
      let count = 0;
      for (const doc of docs.docs) {
        if (doc.data().isDeleted === undefined) {
          batch.update(doc.ref, { isDeleted: false });
          count++;
          totalUpdated++;
          if (count >= BATCH_LIMIT) {
            await batch.commit();
            batch = db.batch();
            count = 0;
          }
        }
      }
      if (count > 0) await batch.commit();
    }
  }
  return { totalUpdated };
});
```

---

## Task 2: Register in `functions/src/index.ts`

Add this line to [functions/src/index.ts](functions/src/index.ts):

```typescript
export * from "./migration.js";
```

---

## Task 3: Add composite Firestore indexes to `firestore.indexes.json`

Update [firestore.indexes.json](firestore.indexes.json) to add 10 new composite indexes (keeping the existing `actionItems` index). The indexes are required for queries that combine `isDeleted` equality filters with other fields/ordering:

- `recordings`: `isDeleted` + `createdAt DESC`
- `recordings`: `isDeleted` + `folderId` + `createdAt DESC`
- `actionItems`: `isDeleted` + `createdAt DESC`
- `actionItems`: `isDeleted` + `recordingId`
- `actionItems`: `isDeleted` + `sharedFromUid` + `createdAt DESC`
- `collectiveSummaries`: `isDeleted` + `createdAt DESC`
- `folders`: `isDeleted` + `createdAt ASC`
- `sharedWithMe`: `isDeleted` + `sharedAt DESC`
- `sharedWithMe`: `isDeleted` + `isRead`
- `myShares`: `isDeleted` + `itemId`

