# Code Review: `functions/src/index.ts`

**Reviewed against:** `DeveloperGuide.md`
**Date:** 2026-03-28
**File size:** 1,734 lines (entire backend in one file)

> Note: `DeveloperGuide.md` targets Android Kotlin/Compose. The general engineering principles — DRY, single-responsibility, logging hygiene, architecture, no duplicate logic — apply directly to TypeScript Cloud Functions and are the basis of this review.

---

## CRITICAL

### 1. Single 1,734-line file — violates single-responsibility throughout

Every feature lives in `index.ts`: transcription pipeline, summaries, collective summaries, NTS scheduling, Google Tasks sync, Google Calendar sync, OAuth exchange, sharing (5 functions), and all helpers.

> *"Group by feature rather than only by type"* and *"One type per file unless tightly coupled and small"*

Recommended split:

| File | Exports |
|------|---------|
| `transcription.ts` | `processRecording`, `retryExtractActionItems` |
| `summary.ts` | `generateSummary`, `generateCollectiveSummary` |
| `googleTasks.ts` | Tasks/Calendar sync, OAuth exchange, disconnect |
| `sharing.ts` | `findUserByEmail`, `shareItem`, `revokeShare`, `dismissSharedItem`, `getSharedAudioUrl` |
| `nts.ts` | `autoScheduleActionItem` |
| `lib/openai.ts` | shared fetch helper |
| `lib/dateUtils.ts` | `parseLocalDateTimeInTimezone`, `parseDateAsNoonUtc`, `getLocalComponents`, `localToUtc` |

---

### 2. Duplicate action item document-building block — copy-pasted verbatim

The 25-line block that builds and writes action item docs to Firestore exists **twice**:

- Lines 98–123 inside `processRecording`
- Lines 826–850 inside `retryExtractActionItems`

Both blocks build the same `doc` object (title, completed, recordingId, createdAt, notes, dueDate, deadline) and commit via `db.batch()`. They are functionally identical.

> *"Never duplicate logic… If you write the same code twice, extract it into a shared function."*

---

### 3. `extractActionItems` and `extractActionItemsAggressive` are near-identical

Lines 568–672 and lines 678–781 share:
- Identical timezone/date boilerplate (~20 lines each)
- Identical OpenAI fetch + parse tail (~30 lines each)
- Only the system prompt differs

> *"Copy-pasting is a code smell that leads to divergent bugs."*

A single helper `callExtractionModel(transcript, tz, systemPrompt)` would eliminate ~100 lines of duplication and ensure bug fixes apply to both paths automatically.

---

### 4. In-memory rate limiting is non-functional in production

```ts
const rateLimitMap = new Map<string, number[]>(); // line 1413
```

Cloud Functions spins up multiple instances independently — each has its own `rateLimitMap` with no shared state. A user can call `findUserByEmail` 10 times per instance × N instances with no limit enforced. The rate limit is effectively dead code in production.

**Fix:** Use Firestore (a rate limit counter doc with a TTL-based window) or Google Cloud Memorystore (Redis) for real cross-instance enforcement.

---

## HIGH

### 5. OpenAI fetch boilerplate repeated 5 times

The same pattern — `fetch(url, { method, headers: { Authorization }, body: JSON.stringify({ model, messages, max_tokens }) })` — appears in:

| Location | Function |
|----------|----------|
| Line 182 | `generateSummary` |
| Line 286 | `generateCollectiveSummary` |
| Line 373 | `generateTitle` |
| Line 632 | `extractActionItems` |
| Line 744 | `extractActionItemsAggressive` |

A single `callOpenAI(messages, options)` helper would remove ~50 lines and make model or endpoint changes a one-line edit.

---

### 6. `Record<string, any>` used for OpenAI response — untyped

```ts
const data = (await response.json()) as Record<string, any>; // lines 211, 315, 396, 667, 776
```

The guide emphasizes typed interfaces. An `OpenAIResponse` interface with `choices: Array<{ message: { content: string } }>` would eliminate all the `?.` chaining and surface failures explicitly rather than silently returning `undefined`.

---

## MEDIUM

### 7. `tasksTokens` is a root-level collection, not under `users/{uid}/`

```ts
await db.collection("tasksTokens").doc(uid).set(...) // line 915
```

> *"Scope all paths under `users/{uid}/`."*

Refresh tokens are sensitive credentials. Placing them at the root means Firestore security rules must be maintained separately from the `users` subtree, increasing the risk of misconfiguration. This is already flagged by a deny rule in `firestore.rules`, confirming it's a known outlier that hasn't been resolved.

---

### 8. Error responses may log user transcript content

```ts
console.error("OpenAI action-item request failed:", response.status, await response.text()) // line 659
```

OpenAI error responses can echo back portions of the prompt, which contains the user's transcript.

> *"Never log: raw transcripts, audio file paths with user content, user email, or any PII."*

Log only the HTTP status code and a sanitized error code, not the raw response body.

---

## LOW

### 9. Biased default timezone (`"America/Los_Angeles"`)

```ts
const tz = timezone || "America/Los_Angeles"; // line 44
```

`retryExtractActionItems` correctly uses `"UTC"` as its fallback (line 797). A US timezone default produces wrong date calculations for non-US users when the client fails to send a timezone. `"UTC"` is the correct neutral fallback.

---

### 10. `WEB_CLIENT_ID` hardcoded as a magic string

```ts
const WEB_CLIENT_ID = "685270102033-...apps.googleusercontent.com"; // line 22
```

Not a security risk (OAuth client IDs are public), but it belongs alongside the other Firebase params/config rather than as a bare string constant in source.

---

## What's Done Well

| Area | Detail |
|------|--------|
| Auth guards | `request.auth` checked on **every** exported function — no gaps |
| Secrets management | `defineSecret()` used for `OPENAI_API_KEY` and `GOOGLE_CLIENT_SECRET` — never hardcoded |
| Batch writes | `db.batch()` used correctly for all multi-doc Firestore writes |
| Schema reuse | `ACTION_ITEMS_SCHEMA` defined once, shared between both extraction functions |
| Parsing separation | `parseActionItems` is a standalone function, reused by both extraction paths |
| DST handling | `parseLocalDateTimeInTimezone` correctly accounts for DST at the event date, not current date |
| Error codes | `HttpsError` error codes are semantically accurate throughout |
| Idempotency | `generateSummary` checks for an existing summary before calling OpenAI |
| Infinite-loop guard | `syncActionItemToGoogleTasks` skips metadata-only field changes to prevent trigger loops |

---

## Summary Table

| Severity | Issue | Guide Section |
|----------|-------|---------------|
| Critical | Everything in one 1,734-line file | §5 Architecture, §8 Organization |
| Critical | Action item doc-building duplicated verbatim | §1 DRY, §14 Don't Do This |
| Critical | `extractActionItems` / `extractActionItemsAggressive` duplication (~100 lines) | §1 DRY, §14 |
| Critical | In-memory rate limiting broken across Cloud Functions instances | §3 Concurrency |
| High | OpenAI fetch pattern repeated 5× | §1 DRY |
| High | `Record<string, any>` for OpenAI response types | §2 Language |
| Medium | `tasksTokens` at root instead of `users/{uid}/` | §6 Firebase |
| Medium | Error logs may capture user transcript content | §9 Logging |
| Low | Biased default timezone (`"America/Los_Angeles"`) | §1 Core Principles |
| Low | `WEB_CLIENT_ID` as magic string in source | §6 Firebase |
