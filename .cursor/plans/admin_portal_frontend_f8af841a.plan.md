---
name: Admin Portal Frontend
overview: Build the complete VoiceMind Admin Portal frontend in the voiceadmin/ directory using Next.js App Router, shadcn/ui, and Tailwind CSS. The plan covers all pages, components, and capabilities defined in adminportal.md, organized into 4 development phases.
todos:
  - id: setup
    content: "Phase 0: Initialize shadcn/ui, install all components and dependencies (recharts, lucide-react, date-fns, react-hook-form, zod)"
    status: completed
  - id: shell-layout
    content: "Phase 1a: Build app shell — collapsible sidebar with nav groups, topbar with breadcrumbs/avatar/theme toggle, role-aware navigation"
    status: in_progress
  - id: auth
    content: "Phase 1b: Build login page with Firebase Auth (email + Google), admin role check, redirect logic"
    status: pending
  - id: shared-components
    content: Build reusable shared components — DataTable, AudioPlayer, MetricCard, DateRangePicker, ConfirmDialog, StatusBadge, ExpandableText, PageHeader, EmptyState
    status: pending
  - id: dashboard
    content: "Phase 2a: Dashboard page — metrics grid, storage overview, system health indicators, signup trend chart"
    status: pending
  - id: user-list
    content: "Phase 2b: User list page — searchable/filterable/sortable table with pagination, bulk select, CSV export"
    status: pending
  - id: user-detail
    content: "Phase 2c: User detail page — tabbed view (profile, recordings, folders, action items, storage), admin action buttons with confirmation dialogs"
    status: pending
  - id: recordings-browser
    content: "Phase 3a: Recordings browser — cross-user table with filters, inline expand for transcripts/summaries, audio playback"
    status: pending
  - id: tasks-browser
    content: "Phase 3b: Action items browser — cross-user table with filters and sorting"
    status: pending
  - id: feature-flags
    content: "Phase 4a: Global feature flags page — toggle switches, number inputs, maintenance mode config"
    status: pending
  - id: user-feature-overrides
    content: "Phase 4b: Per-user feature overrides in user detail — three-state toggles, tier selector"
    status: pending
  - id: admin-management
    content: "Phase 4c: Admin management page (super admin only) — admin table, invite dialog, role change, remove"
    status: pending
  - id: analytics
    content: "Phase 5a: Analytics page — user growth, recording activity, action item metrics, feature adoption charts, top users leaderboard"
    status: pending
  - id: system-monitoring
    content: "Phase 5b: System monitoring page — function health charts, pipeline health, calendar sync, storage monitoring, cost estimation"
    status: pending
  - id: audit-log
    content: "Phase 5c: Audit log page — filterable admin action history table"
    status: pending
  - id: view-as-user
    content: "Phase 6: View-as-user mode — read-only simulation of user's app experience with prominent banner"
    status: pending
  - id: moderation
    content: "Phase 6: Content moderation page — flag/unflag queue, admin notes, bulk actions"
    status: pending
isProject: false
---

# VoiceMind Admin Portal — Frontend Plan

All code resides in `voiceadmin/`. The frontend is built with **Next.js 16 App Router**, **React 19**, **Tailwind CSS v4**, **shadcn/ui**, and **TypeScript**. Charts use **Recharts** (shadcn has built-in chart components wrapping Recharts).

---

## Phase 0: Project Setup

Initialize shadcn/ui in the existing Next.js project and install all required dependencies.

- Run `npx shadcn@latest init` inside `voiceadmin/`
- Install core shadcn components: `button`, `input`, `label`, `select`, `switch`, `table`, `dialog`, `alert-dialog`, `card`, `tabs`, `avatar`, `badge`, `dropdown-menu`, `sheet`, `command`, `calendar`, `chart`, `sonner`, `pagination`, `skeleton`, `breadcrumb`, `tooltip`, `sidebar`, `separator`, `popover`, `form`, `textarea`, `checkbox`
- Install additional deps: `recharts`, `lucide-react`, `date-fns`, `react-hook-form`, `zod`, `@hookform/resolvers`
- Set up a consistent dark/light theme toggle using shadcn's theme provider

---

## Phase 1: Shell Layout and Auth

### App Shell (`app/(admin)/layout.tsx`)

A persistent sidebar + topbar layout wrapping all authenticated pages.

- **Sidebar** (shadcn `Sidebar`): collapsible, with nav groups:
  - **Main**: Dashboard, Users, Recordings, Action Items
  - **Settings**: Feature Flags, Admin Management
  - **Insights**: Analytics, System Health, Audit Log
- **Topbar**: breadcrumbs (shadcn `Breadcrumb`), theme toggle, admin avatar dropdown (shadcn `DropdownMenu`) with sign-out
- **Role-aware nav**: hide "Admin Management" for non-super-admins; disable write actions for viewers

### Auth Pages (`app/(auth)/login/page.tsx`)

- Standalone layout (no sidebar)
- Firebase Auth sign-in form (Email/Password + Google)
- After sign-in, check `admins/{uid}` doc server-side; deny if missing/invalid role
- Redirect to `/` on success
- shadcn components: `Card`, `Button`, `Input`, `Label`, `Separator`

---

## Phase 2: Core Pages (Phase 1 from adminportal.md)

### 2.1 Dashboard (`app/(admin)/page.tsx`)

Top-level overview with 3 sections:

- **Metrics grid**: `Card` components showing:
  - Total users, DAU/WAU/MAU, new signups, total recordings, recordings with transcripts/summaries, total action items (completed vs pending), calendar-connected users
- **Storage overview cards**: total storage, avg per user, top 10 users by storage
- **System health indicators**: status badges for Cloud Functions, Auth, Firestore, Storage, OpenAI API (`Badge` with green/yellow/red)
- **Signup trend chart**: line chart using shadcn `ChartContainer` + Recharts

### 2.2 User List (`app/(admin)/users/page.tsx`)

Full-featured data table with:

- shadcn `Table` with sortable column headers
- Search bar (`Input` + `Command` for search suggestions)
- Filters: sign-in method (`Select`), date range (`Calendar` + `Popover`), account status (`Select`), calendar connected toggle
- Columns: email, display name, sign-in method, created, last sign-in, recordings count, action items count, calendar connected, status
- `Pagination` component at bottom
- Bulk selection via `Checkbox` in rows
- CSV export button
- Row click navigates to user detail

### 2.3 User Detail (`app/(admin)/users/[uid]/page.tsx`)

Tabbed view using shadcn `Tabs`:

- **Profile tab**: user info card (UID, email, name, photo via `Avatar`, providers, dates, status `Badge`)
- **Recordings tab**: `Table` of recordings with title, transcript preview (expandable), summary preview, folder, date, audio playback button. Inline expand for full text.
- **Folders tab**: `Table` of folders with name and recording count
- **Action Items tab**: `Table` with title, status `Badge` (to-do/done), due date, deadline, notes (expandable), calendar sync status, source recording link
- **Storage tab**: total storage `Card`, `Table` of audio files with sizes
- **Feature Access tab** (Phase 2 content, but UI scaffold now): three-state toggles per feature flag — "Global default" / "Enabled" / "Disabled"

**Admin Actions panel** (right side or sticky footer):

- Disable/Enable account (`Button` + `AlertDialog` confirmation)
- Delete user (`AlertDialog` with email-type-to-confirm `Input`)
- Reset user data (`AlertDialog`)
- Force sign-out (`AlertDialog`)
- Actions gated by role: viewers see none; admins see disable/enable/force-sign-out; super admins see all

### 2.4 Audio Playback Component

- Custom `<AudioPlayer />` component using native `<audio>` with styled controls
- Play/pause, progress bar, duration display
- Accepts a signed URL prop (fetched from server action)

---

## Phase 3: Content Inspection

### 3.1 Recordings Browser (`app/(admin)/recordings/page.tsx`)

- Same `Table` pattern as user list but cross-user
- Columns: user email (link to user detail), title, transcript preview, summary preview, folder, created at, has audio, action item count
- Filters: user email, date range, has transcript, has summary, folder name
- Sort: creation date, user, title
- Inline expand: click row to reveal full transcript + summary + extracted action items
- Audio playback inline

### 3.2 Action Items Browser (`app/(admin)/tasks/page.tsx`)

- Cross-user table of action items
- Columns: user email, title, status (`Badge`), due date, deadline, notes (expandable), calendar synced, source recording link, created at
- Filters: user, status, has due date, has deadline, calendar synced, date range
- Sort: creation date, due date, deadline, status

### 3.3 Content Moderation (Phase 4, scaffold only)

- Flag/unflag button on recordings and action items
- Flagged content queue page (`app/(admin)/moderation/page.tsx`)
- Admin notes `Textarea` on flagged items
- Bulk flag/unflag via checkbox selection

---

## Phase 4: Feature Flags & Admin Management

### 4.1 Global Feature Flags (`app/(admin)/settings/features/page.tsx`)

- `Card` per feature flag with:
  - `Switch` for booleans (transcription, auto-title, action item extraction, summary, calendar sync, maintenance mode)
  - `Input` (number) for limits (max recordings, max storage MB)
  - `Input` (text) for maintenance message, minimum app version
- Changes saved via server action, toast confirmation (`Sonner`)
- Read-only for viewers

### 4.2 Per-User Feature Overrides

- Located within User Detail > Feature Access tab
- Each flag shown with three-state control:
  - Radio group or `Select` with options: "Global Default", "Enabled", "Disabled"
  - Number overrides: `Input` with "Use global default" checkbox
- Shows current effective value (resolved: per-user > global > default)
- User tier `Select`: free / premium / beta

### 4.3 Admin Management (`app/(admin)/settings/admins/page.tsx`)

- Super admin only (hidden from nav for others)
- `Table` of admin accounts: email, name, role (`Badge`), created at, created by
- Invite admin: `Dialog` with email `Input` + role `Select`
- Change role: inline `Select` dropdown
- Remove admin: `AlertDialog` confirmation

---

## Phase 5: Analytics & Insights

### 5.1 Analytics (`app/(admin)/analytics/page.tsx`)

Tabbed layout or sub-nav with sections:

- **User Growth**: signups per day/week/month (line chart), cumulative growth (area chart), signups by auth method (stacked bar), churn list
- **Recording Activity**: recordings per day/week (bar chart), avg per user, peak hours heatmap, transcription success rate, summary generation rate
- **Action Item Metrics**: created per day, completion rate over time, avg per recording, tasks with dates, calendar sync rate
- **Feature Adoption**: cards showing % of users per feature (recordings, summaries, calendar, nav mode), avg folders per user
- **Top Users Leaderboard**: `Table` with tabs — by recording count, action items, storage, login frequency

All charts use shadcn `ChartContainer` + `ChartTooltip` + Recharts components (`LineChart`, `BarChart`, `AreaChart`, `PieChart`).

### 5.2 System Monitoring (`app/(admin)/system/page.tsx`)

- **Cloud Functions health**: time-series charts for invocations, error rates, latency per function
- **Pipeline health**: `Table` of stuck recordings (no transcript >5min), failed transcriptions, title generation failures
- **Calendar sync health**: metrics cards + mismatch table
- **Storage monitoring**: total used, growth rate chart, projected quota hit, orphaned files table
- **Cost estimation**: `Card` with estimated API call counts, links to external dashboards

### 5.3 Audit Log (`app/(admin)/audit/page.tsx`)

- `Table` with columns: timestamp, admin email, action (`Badge` by category), target user, details (expandable)
- Filters: admin, action type (`Select`), target user, date range (`Calendar`)
- Sort by timestamp (default newest first)
- Also shown inline on User Detail page as "Admin actions on this user" section

---

## Phase 6: View-as-User Mode

### `app/(admin)/users/[uid]/view/page.tsx`

- Read-only simulation of the user's app experience
- Styled differently (prominent "Viewing as: [user@email.com](mailto:user@email.com)" banner)
- Sections: Home (folders + recent recordings), Recordings list, Checklist (to-do/done), Settings state
- No write operations — all interactions are display-only
- Exit button returns to user detail page

---

## Shared Components (`components/`)


| Component          | Purpose                                                                                              |
| ------------------ | ---------------------------------------------------------------------------------------------------- |
| `DataTable`        | Reusable table with sorting, filtering, pagination, bulk select, CSV export. Built on shadcn `Table` |
| `AudioPlayer`      | Custom audio playback with styled controls                                                           |
| `StatusBadge`      | Colored badge for status indicators (active/disabled, healthy/degraded, to-do/done)                  |
| `MetricCard`       | Dashboard card showing a metric with label, value, trend indicator                                   |
| `DateRangePicker`  | Composed from shadcn `Calendar` + `Popover` + `Button`                                               |
| `ConfirmDialog`    | Reusable confirmation dialog with optional typed-email confirmation                                  |
| `ThreeStateToggle` | For per-user feature overrides (Global Default / Enabled / Disabled)                                 |
| `ExpandableText`   | Truncated text with "show more" for transcripts/summaries                                            |
| `RoleBadge`        | Admin role badge (super_admin / admin / viewer)                                                      |
| `EmptyState`       | Placeholder for tables/sections with no data                                                         |
| `PageHeader`       | Page title + description + optional action buttons                                                   |


---

## Route Structure

```
app/
├── (auth)/
│   └── login/
│       └── page.tsx
├── (admin)/
│   ├── layout.tsx                    # Sidebar + topbar shell
│   ├── page.tsx                      # Dashboard
│   ├── users/
│   │   ├── page.tsx                  # User list
│   │   └── [uid]/
│   │       ├── page.tsx              # User detail
│   │       └── view/
│   │           └── page.tsx          # View-as-user
│   ├── recordings/
│   │   └── page.tsx                  # Recordings browser
│   ├── tasks/
│   │   └── page.tsx                  # Action items browser
│   ├── settings/
│   │   ├── features/
│   │   │   └── page.tsx              # Global feature flags
│   │   └── admins/
│   │       └── page.tsx              # Admin management
│   ├── analytics/
│   │   └── page.tsx                  # Analytics & insights
│   ├── system/
│   │   └── page.tsx                  # System monitoring
│   ├── moderation/
│   │   └── page.tsx                  # Content moderation
│   └── audit/
│       └── page.tsx                  # Audit log
└── globals.css
```

