# VoiceMind AI — Admin Portal Capabilities

**Purpose:** Define everything the admin portal should be capable of before development begins. This is the internal dashboard used by a small team (2-5 people) to manage users, control feature access, inspect content, and monitor system health.

**Data source:** Existing Firebase project (Firestore, Auth, Storage). No new database required. The portal uses the **Firebase Admin SDK** (server-side) which bypasses client security rules and has full read/write access.

---

## 1. Dashboard (Overview / Home)

The landing page after admin login. A single-screen snapshot of the entire system.

### Real-time metrics

| Metric | Source | Notes |
|--------|--------|-------|
| Total registered users | Firebase Auth | All-time count |
| Active users (DAU / WAU / MAU) | Firebase Auth (`lastSignInTime`) | Users who signed in within 24h / 7d / 30d |
| New signups today / this week / this month | Firebase Auth (`creationTime`) | Trend line chart |
| Total recordings | Firestore `users/*/recordings` | Aggregate across all users |
| Recordings with transcripts | Firestore (where `transcription != null`) | Shows pipeline success rate |
| Recordings with summaries | Firestore (where `summary != null`) | On-demand summary adoption |
| Total action items | Firestore `users/*/actionItems` | Aggregate |
| Action items completed vs pending | Firestore (by `completed` field) | Ratio / bar chart |
| Calendar-connected users | Firestore `users/{uid}.calendarConnected == true` | Count |

### Storage overview

| Metric | Source |
|--------|--------|
| Total storage used | Firebase Storage aggregate |
| Avg storage per user | Total / active users |
| Top 10 users by storage | Per-user calculation |

### System health indicators

- Cloud Functions status: last error, error rate (last 24h)
- Firebase Auth: operational / degraded
- Firestore: read/write latency
- Storage: quota usage percentage
- OpenAI API: proxy via function invocation success/failure rates

---

## 2. User Management

### 2.1 User list

A searchable, filterable, sortable table of all registered users.

| Column | Source | Filterable |
|--------|--------|------------|
| Email | Firebase Auth | Yes (search) |
| Display name | Firebase Auth | Yes (search) |
| Sign-in method | Firebase Auth (provider data) | Yes (Email / Google / Both) |
| Account created | Firebase Auth `creationTime` | Yes (date range) |
| Last sign-in | Firebase Auth `lastSignInTime` | Yes (date range) |
| Recordings count | Firestore `users/{uid}/recordings` | Yes (range) |
| Action items count | Firestore `users/{uid}/actionItems` | Yes (range) |
| Calendar connected | Firestore `users/{uid}.calendarConnected` | Yes (Yes / No) |
| Account status | Firebase Auth `disabled` | Yes (Active / Disabled) |

Pagination, bulk selection, and export to CSV.

### 2.2 User detail view

Clicking a user opens a full profile page with:

**Profile info:**
- UID, email, display name, photo URL
- Sign-in provider(s): Email/Password, Google (show Google account email if different)
- Account created date, last sign-in date
- Account status (active / disabled)

**User's data (read-only browsing):**
- Recordings tab: list of all recordings with title, transcript preview, summary preview, folder, creation date. Tappable to expand full transcript/summary. Audio playback button (streams from Storage).
- Folders tab: list of folders with recording counts.
- Action items tab: list of tasks with title, status (to-do / done), due date, deadline, notes, calendar sync status, source recording link.
- Storage tab: total audio storage used, list of audio files with sizes.

**Administrative actions:**
- Disable account (prevents sign-in; does not delete data)
- Enable account (re-enables sign-in)
- Delete user: cascading delete of all data:
  1. All documents in `users/{uid}/recordings`
  2. All documents in `users/{uid}/folders`
  3. All documents in `users/{uid}/actionItems`
  4. All files in `users/{uid}/audio/*` (Storage)
  5. `calendarTokens/{uid}` document (if exists)
  6. `users/{uid}` document
  7. Firebase Auth user record
  - Requires confirmation dialog with user email typed to confirm
- Reset user data (delete all content but keep the account)
- Force sign-out (revoke refresh tokens)

### 2.3 View-as-user mode

Read-only view that shows exactly what the user sees in their app:
- Their home screen data (folders with counts, recent recordings)
- Their recordings list with transcripts, summaries
- Their checklist (to-do and done sections)
- Their settings state (nav mode, calendar connected)

This is for support and debugging — no write operations in this mode.

---

## 3. Content Inspection

### 3.1 Recordings browser

Browse recordings across all users or filtered to a specific user.

| Column | Notes |
|--------|-------|
| User (email) | Links to user detail |
| Title | Auto-generated or user-edited |
| Transcript | Expandable preview (first 200 chars) → full text |
| Summary | Expandable preview → full text |
| Folder | Folder name |
| Created at | Timestamp |
| Has audio | Yes / No (with playback button) |
| Action items | Count of tasks extracted from this recording |

**Capabilities:**
- Full-text search across transcripts and summaries
- Filter by: user, date range, has transcript (yes/no), has summary (yes/no), folder name
- Sort by: creation date, user, title
- Expand inline to read full transcript and summary without leaving the list
- Audio playback: stream directly from Firebase Storage via Admin SDK signed URLs
- View extracted action items for a recording (inline expandable)

### 3.2 Action items browser

Browse all action items across all users.

| Column | Notes |
|--------|-------|
| User (email) | Links to user detail |
| Title | Task title |
| Status | To-do / Done |
| Due date | Datetime (if set) |
| Deadline | Date (if set) |
| Notes | Expandable |
| Calendar synced | Yes (event ID) / No |
| Source recording | Title + link to recording |
| Created at | Timestamp |

**Capabilities:**
- Filter by: user, status, has due date, has deadline, calendar synced, date range
- Sort by: creation date, due date, deadline, status
- View the source recording inline

### 3.3 Content moderation

For future use if the app ever has shared/public content:
- Flag a recording or action item (sets a `flagged: true` field)
- Add admin notes to flagged content
- Bulk flag/unflag
- View flagged content queue

---

## 4. Feature Flags and Access Control

This is the core "manage what functionalities users can use" capability. Requires new Firestore data structures.

### 4.1 Global feature flags

A single Firestore document `config/features` that controls features for ALL users.

| Flag | Type | Default | Controls |
|------|------|---------|----------|
| `transcriptionEnabled` | Boolean | `true` | Whether `processRecording` runs the transcription step |
| `autoTitleEnabled` | Boolean | `true` | Whether auto-title generation runs |
| `actionItemExtractionEnabled` | Boolean | `true` | Whether action items are extracted from transcripts |
| `summaryGenerationEnabled` | Boolean | `true` | Whether on-demand summary is available |
| `calendarSyncEnabled` | Boolean | `true` | Whether Google Calendar connect is shown in Settings |
| `maxRecordingsPerUser` | Number | `0` (unlimited) | Hard cap on recordings per user; `0` = no limit |
| `maxStorageMbPerUser` | Number | `0` (unlimited) | Storage cap in MB; `0` = no limit |
| `maintenanceMode` | Boolean | `false` | When `true`, app shows a maintenance message instead of loading |
| `maintenanceMessage` | String | `""` | Custom message shown during maintenance |
| `minimumAppVersion` | String | `""` | Minimum app version required; older versions show an "update required" message |

**Admin portal UI:** A settings page with toggles and number inputs for each flag. Changes take effect immediately (Firestore real-time).

**Android app changes needed:** On launch and at key points, the app reads `config/features` and respects the flags. For example, if `summaryGenerationEnabled` is `false`, the Summary tab in the transcript sheet is hidden or shows "Summaries are temporarily unavailable."

### 4.2 Per-user feature overrides

A field on the user document `users/{uid}` or a sub-document for per-user overrides. These take precedence over global flags.

| Field | Type | Notes |
|-------|------|-------|
| `features.transcriptionEnabled` | Boolean? | `null` = follow global; `true`/`false` = override |
| `features.summaryGenerationEnabled` | Boolean? | Same pattern |
| `features.calendarSyncEnabled` | Boolean? | Same pattern |
| `features.maxRecordings` | Number? | `null` = follow global; a number = override |
| `features.maxStorageMb` | Number? | Same pattern |
| `tier` | String | `"free"` / `"premium"` / `"beta"` — for future tiering |

**Resolution logic:** Per-user override > global flag > hardcoded default.

**Admin portal UI:** On the user detail page, a "Feature Access" section shows each flag with a three-state toggle: "Global default", "Enabled", "Disabled". When set to anything other than "Global default", the override is written to the user document.

### 4.3 Usage limits enforcement

The Cloud Functions and Android app check limits before allowing actions:
- Before creating a recording: check `maxRecordingsPerUser` (global or per-user override) against the user's current count.
- Before uploading audio: check `maxStorageMbPerUser` against the user's current storage usage.
- If limit is hit, return a clear error that the app shows to the user.

---

## 5. Admin Roles and Authentication

### 5.1 Admin accounts

Admins authenticate using the same Firebase Auth project but are distinguished by an `admins` collection in Firestore.

**Firestore collection: `admins/{uid}`**

| Field | Type | Notes |
|-------|------|-------|
| `email` | String | Admin's email |
| `role` | String | `"super_admin"` / `"admin"` / `"viewer"` |
| `displayName` | String | Admin's name |
| `createdAt` | Timestamp | When admin access was granted |
| `createdBy` | String | UID of the super admin who granted access |

### 5.2 Role permissions

| Capability | Super Admin | Admin | Viewer |
|------------|:-----------:|:-----:|:------:|
| View dashboard metrics | Yes | Yes | Yes |
| View user list | Yes | Yes | Yes |
| View user detail + content | Yes | Yes | Yes |
| View-as-user mode | Yes | Yes | Yes |
| Browse all recordings | Yes | Yes | Yes |
| Browse all action items | Yes | Yes | Yes |
| Audio playback | Yes | Yes | Yes |
| Search content | Yes | Yes | Yes |
| Disable / enable user | Yes | Yes | No |
| Delete user | Yes | No | No |
| Reset user data | Yes | No | No |
| Force sign-out | Yes | Yes | No |
| Edit global feature flags | Yes | Yes | No |
| Edit per-user feature overrides | Yes | Yes | No |
| Manage admin accounts | Yes | No | No |
| View system monitoring | Yes | Yes | Yes |
| Flag / unflag content | Yes | Yes | No |

### 5.3 Admin management (Super Admin only)

- Invite new admin: enter email, select role. The user must already have a Firebase Auth account (or create one). The super admin creates a document in `admins/{uid}`.
- Change role: promote / demote between viewer, admin, and super admin.
- Remove admin access: delete the `admins/{uid}` document.
- The admin portal login page checks `admins/{uid}` after Firebase Auth sign-in. If the document doesn't exist or has no valid role, access is denied.

---

## 6. Analytics and Usage Insights

Deeper analytics beyond the dashboard summary. Each section is a dedicated page or tab.

### 6.1 User growth

- Signups per day / week / month (line chart)
- Cumulative user growth (area chart)
- Signups by auth method (email vs Google, stacked bar)
- Churn proxy: users who haven't signed in for 30+ days

### 6.2 Recording activity

- Recordings created per day / week (bar chart)
- Avg recordings per user
- Peak recording hours (heatmap by hour of day / day of week)
- Transcription success rate over time
- Summary generation rate (how many recordings get a summary requested)

### 6.3 Action item metrics

- Action items created per day
- Completion rate (completed / total, over time)
- Avg action items per recording
- Tasks with due dates vs without
- Tasks with deadlines vs without
- Calendar sync rate (items with `calendarEventId` / items with dates)

### 6.4 Feature adoption

- % of users who have created a recording
- % of users who have viewed a summary
- % of users who have connected Google Calendar
- % of users using sidebar nav vs bottom nav
- Avg folders per user (beyond defaults)

### 6.5 Top users leaderboard

- Top 10 by recording count
- Top 10 by action items created
- Top 10 by storage used
- Top 10 by login frequency

---

## 7. System Monitoring

### 7.1 Cloud Functions health

| Metric | Source |
|--------|--------|
| `processRecording` invocations (last 24h / 7d) | Google Cloud Monitoring |
| `processRecording` error rate | Google Cloud Monitoring |
| `processRecording` avg latency | Google Cloud Monitoring |
| `generateSummary` invocations + error rate | Google Cloud Monitoring |
| `syncActionItemToCalendar` trigger count | Google Cloud Monitoring |
| `exchangeCalendarAuthCode` invocations | Google Cloud Monitoring |

Display as time-series charts with error highlights.

### 7.2 Pipeline health

- Recordings without transcripts older than 5 minutes (stuck pipeline indicator)
- List of recently failed transcriptions (from function logs)
- Recordings with transcripts but no title (title generation failure)

### 7.3 Calendar sync health

- Total users with `calendarConnected == true`
- Total `calendarTokens` documents
- Mismatches: users with `calendarConnected == true` but no `calendarTokens` document (token was cleaned up but flag wasn't updated)
- Action items with `calendarEventId` that may reference deleted events

### 7.4 Storage monitoring

- Total storage used across all users
- Growth rate (storage added per day/week)
- Projected time to hit Firebase Storage quota
- Orphaned files: audio files in Storage that don't have a corresponding Firestore recording document

### 7.5 Cost estimation

Since we can't directly query OpenAI billing from the portal, provide proxies:
- `processRecording` calls = 1 transcription + 1 title + 1 extraction = ~3 OpenAI API calls per recording
- `generateSummary` calls = 1 OpenAI API call per summary
- Show estimated API call counts for the current billing period
- Link to external dashboards: Firebase Console, Google Cloud Console, OpenAI Usage page

---

## 8. Audit Log

Track all administrative actions for accountability.

**Firestore collection: `auditLog/{autoId}`**

| Field | Type | Notes |
|-------|------|-------|
| `adminUid` | String | UID of the admin who performed the action |
| `adminEmail` | String | Email for readability |
| `action` | String | e.g. `user.disable`, `user.delete`, `feature.update`, `admin.invite` |
| `targetUid` | String? | The user affected (if applicable) |
| `details` | Map | Action-specific data (e.g. `{ flag: "summaryGenerationEnabled", from: true, to: false }`) |
| `timestamp` | Timestamp | When the action occurred |
| `ip` | String? | Admin's IP address (optional) |

**Admin portal UI:**
- Audit log page: filterable by admin, action type, target user, date range
- Shown on user detail page: "Admin actions on this user" section
- Retained indefinitely (or configurable retention period)

---

## 9. Firestore Schema Changes Required

### New collections and documents

| Path | Purpose | Created by |
|------|---------|------------|
| `config/features` | Global feature flags | Admin portal |
| `admins/{uid}` | Admin accounts and roles | Super admin via portal |
| `auditLog/{autoId}` | Administrative action log | Admin portal (auto) |
| `calendarTokens/{uid}` | Already exists (server-side calendar tokens) | Cloud Functions |

### Modified documents

| Path | New fields | Purpose |
|------|------------|---------|
| `users/{uid}` | `features` (map), `tier` (string) | Per-user feature overrides and tier |

### Security rules additions

```
match /config/{document} {
  allow read: if request.auth != null;  // all authenticated users can read feature flags
  allow write: if false;                // only Admin SDK (server-side)
}

match /admins/{uid} {
  allow read, write: if false;          // only Admin SDK (server-side)
}

match /auditLog/{document} {
  allow read, write: if false;          // only Admin SDK (server-side)
}
```

The admin portal uses the Firebase Admin SDK which bypasses these rules entirely. The rules ensure that regular app users cannot read admin data or tamper with feature flags.

### Android app changes needed

The Android app needs to read `config/features` on launch and respect the flags:

1. **Feature flag service:** A new repository (`FeatureFlagRepository`) that observes `config/features` and merges with per-user overrides from `users/{uid}.features`.
2. **Gate checks:** Before key actions:
   - Recording: check `maintenanceMode`, `maxRecordingsPerUser`
   - Summary tab: check `summaryGenerationEnabled`
   - Calendar connect: check `calendarSyncEnabled`
   - App launch: check `minimumAppVersion` against `BuildConfig.VERSION_NAME`
3. **Maintenance mode:** If `maintenanceMode == true`, show a full-screen message with `maintenanceMessage` text instead of the normal UI.

---

## 10. Tech Stack Recommendation

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Framework | Next.js (App Router) | Server-side rendering for Firebase Admin SDK calls; API routes for mutations |
| UI library | Tailwind CSS + shadcn/ui | Clean, modern components; consistent with a professional dashboard |
| Charts | Recharts or Tremor | React-native charting for analytics pages |
| Auth | Firebase Auth (same project) + `admins` collection check | No separate auth system needed |
| Firebase access | Firebase Admin SDK (Node.js) | Full server-side access, bypasses security rules |
| Deployment | Vercel or Firebase Hosting | Vercel for Next.js is simplest; Firebase Hosting with Cloud Run also works |
| State management | React Server Components + SWR or React Query for client-side | Minimize client-side complexity |

### Architecture

```
Browser (Admin)
    │
    ▼
Next.js App (Vercel / Firebase Hosting)
    │
    ├── Server Components / API Routes
    │       │
    │       ▼
    │   Firebase Admin SDK
    │       │
    │       ├── Firebase Auth (list/manage users)
    │       ├── Firestore (read/write all collections)
    │       └── Cloud Storage (signed URLs for audio)
    │
    └── Client Components
            │
            ▼
        Charts, Tables, Search UI
```

The Firebase Admin SDK runs only on the server (Next.js API routes / Server Components). No Firebase credentials are exposed to the browser. Admin authentication is verified server-side: Firebase Auth token → check `admins/{uid}` document → enforce role permissions.

---

## 11. Pages / Screens Summary

| Page | URL pattern | Description |
|------|-------------|-------------|
| Login | `/login` | Firebase Auth sign-in, redirect if not in `admins` collection |
| Dashboard | `/` | Overview metrics, charts, health indicators |
| Users | `/users` | User list with search, filter, pagination |
| User Detail | `/users/[uid]` | Profile, recordings, folders, tasks, storage, feature overrides, admin actions |
| View-as-User | `/users/[uid]/view` | Read-only simulation of the user's app experience |
| Recordings | `/recordings` | Cross-user recordings browser with search |
| Action Items | `/tasks` | Cross-user action items browser |
| Feature Flags | `/settings/features` | Global feature flag management |
| Admins | `/settings/admins` | Admin account management (super admin only) |
| Analytics | `/analytics` | User growth, recording activity, feature adoption charts |
| System | `/system` | Cloud Function health, storage monitoring, cost estimates |
| Audit Log | `/audit` | Admin action history |

---

## 12. Development Phases

### Phase 1 — Core (must-have for launch)

- Admin authentication with role check
- Dashboard with key metrics
- User list with search and filter
- User detail view with data browsing (recordings, folders, tasks)
- Disable / enable / delete user
- Audio playback from Storage

### Phase 2 — Control

- Global feature flags (config/features)
- Per-user feature overrides
- Usage limits (max recordings, max storage)
- Android app reads and respects feature flags
- Maintenance mode

### Phase 3 — Insights

- Analytics pages (user growth, recording activity, task metrics, feature adoption)
- System monitoring (function health, storage trends, cost estimates)
- Audit log

### Phase 4 — Advanced

- Content moderation (flag/unflag)
- View-as-user mode
- Full-text search across transcripts
- Orphaned file cleanup tools
- Export capabilities (CSV, data exports)

---

*Review and finalize this capabilities document before starting development. Update as priorities shift.*
