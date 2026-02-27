# VoiceMind AI — Feature & Product Requirements Document (PRD)

> Mobile-first “record-and-route” voice capture + AI workflow assistant that turns spoken thoughts into **organized knowledge, tasks, emails, calendar events, and journal entries**.

---

## 1) Product vision

### Vision statement
VoiceMind AI is your “always-available” voice companion that captures thoughts in seconds and **pushes them forward** into your workflow—without you needing to rewrite, reorganize, or remember follow‑ups.

### Core principles
- **Frictionless capture:** one-tap recording, minimal UI, reliable offline capture.
- **From unstructured to actionable:** summaries + tasks + drafts + scheduling suggestions.
- **Workflow-friendly by default:** everything can become a task, email, calendar invite, CRM note, or project update.
- **Journaling-first optionality:** structured reflection, mood tags, prompts, and private spaces.
- **Trust & control:** privacy-first, transparent AI, easy review/approve before anything is sent or created.

---

## 2) Target users & key jobs-to-be-done

### Primary personas
- **Builder/Developer:** idea capture, debugging out loud, decision logs, daily notes.
- **PM/Program Lead:** post-meeting dumps, action extraction, stakeholder follow-ups.
- **People Manager:** 1:1 notes, feedback, performance evidence, coaching plans.
- **Founder/Operator:** ops tracking, vendor/client follow-ups, finance reminders.
- **Student/Researcher:** lecture notes, reading reflections, thesis progress tracking.
- **Therapy/Journaling user:** private thought capture, guided reflection, habit tracking.

### Jobs-to-be-done
- “When I have a thought, I want to capture it instantly so I don’t lose it.”
- “After I talk, I want the app to **do the organizing and drafting**.”
- “I want to retrieve what I said weeks ago using natural language.”
- “I want a private journaling system that helps me reflect, not just archive.”

---

## 3) Core workflows (end-to-end)

### A) Quick thought capture → structured note
1. Tap record (lock screen / widget / headphones).
2. Speak freely.
3. Stop → instant “Smart Save” sheet:
   - Auto-title
   - Suggested destination (folder/project)
   - Suggested outputs (tasks, email draft, calendar, journal)
4. Processing → summary, transcript, entities, tasks, suggested next steps.

### B) Meeting / conversation capture → follow-ups
- Capture (phone mic / call capture where permitted / external upload)
- Speaker separation + topics + decisions
- “Follow-up pack” output:
  - action list
  - recap email drafts
  - calendar holds / reminders
  - agenda for next meeting

### C) Journaling capture → reflection + insights
- Daily prompt or freeform voice journal
- Mood + themes extraction
- Weekly review rollup + highlights + patterns

---

## 4) Feature catalog (extensive)

### 4.0 Current implementation (shipped)

The following is implemented and live in the app. It represents a first slice of the vision above.

**Capture & recording**
- **One-tap record** from Home via a fixed mic button (FAB). Recording starts immediately with default title (e.g. date/time).
- **Recording drawer** (bottom sheet) while recording: editable title, elapsed timer, **Pause / Resume**, **Stop and save**, **Delete**. Drawer sits above the mic so controls are usable.
- **Save flow:** On stop, audio is uploaded (WebM), stored under `data/audio/`, and a recording row is created. No offline queue yet—recording is saved when the user stops.

**Transcription & AI**
- **Transcription** via OpenAI (gpt-4o-transcribe) after save. Transcript is stored on the recording and shown in the app.
- **Auto-title** from transcript (short phrase, max 25 chars) using GPT; user can edit title in the drawer before stop.
- **Action-item extraction** from transcript at save time: LLM extracts explicit tasks (e.g. “remind me to…”, “I need to…”) and creates checklist items (max 5 per recording). Stored in `data/action-items.json` with optional `recordingId` link.

**Organization**
- **Manual folders:** List, create, rename, delete. Default folders (Work, Meetings, Ideas, Personal, Journal, Archive, Unfiled) on first run. Recordings can be moved to a folder (from Recordings list context menu). Folder detail page shows recordings in that folder.
- **No AI-suggested folder yet** — new recordings go to Unfiled unless moved later.

**Checklist (in-app task list)**
- **Checklist** screen in sidebar: two sections, **To-do** and **Done**, each in its own card (no vertical gap per style guide). Items from recordings appear in To-do; user can mark complete (moves to Done), uncheck (moves back to To-do), or delete. “From recording” link shows the **recording title** (darker blue) and links to Recordings. List refreshes when new recordings are saved (action-items-updated event).

**Recordings list & playback**
- **Recordings** page: list all recordings (or filter by folder). Per row: play/pause, view transcript (modal), rename, move to folder, delete, share audio (Web API share with WebM file), copy transcript, share transcript. Long-press or context menu for actions. Audio served from `GET /api/recordings/[id]/audio`.

**Home**
- **Home:** Collapsible folder list (navigate into folders) and recent recordings list. Fixed record button at bottom.

**Settings**
- **Settings** page exists; placeholder copy (“Settings will be available here”). No integration or privacy toggles yet.

**APIs & data**
- **Recordings:** GET (list, optional `folderId`), POST (multipart: file + title + folderId; or JSON for create-only). PATCH/DELETE by id. GET `[id]/audio` for playback.
- **Folders:** GET list, POST create, PATCH/DELETE by id. Deleting a folder reassigns its recordings to Unfiled.
- **Action items:** GET list, PATCH by id (completed, title), DELETE by id. No client POST—items are created server-side when a recording is saved.
- **Transcribe:** POST multipart (standalone transcription); main flow uses internal `transcribeAudio` + title + action extraction in recordings route.
- **Storage:** `data/recordings.json`, `data/folders.json`, `data/action-items.json`, `data/audio/*.webm`.

**Not yet implemented (from MVP vision)**
- Offline capture queue and background upload.
- Clean summary (we have transcript + title only).
- Search (keyword or semantic).
- Journaling mode (prompts, private folder behavior).
- Smart Save sheet with suggested destination and output toggles.

---

### 4.1 Capture & recording
- **One-tap record** (home, lock screen, widget, watch companion).
- **Pause / resume**, insert markers (“highlight moment”).
- **Hands-free modes:** Bluetooth button, headphones, voice command (“Start note”).
- **Background recording** with clear mic indicator.
- **Offline capture queue** with automatic upload/processing later.
- **Multi-source input:**
  - audio import (voice memos, WhatsApp voice notes, calls recordings where allowed)
  - video import (extract audio)
  - text quick-capture (“type or paste”)
- **Quality controls:** noise suppression, automatic gain, mic selection.
- **Consent & compliance UI:** on-screen “recording” indicator; optional consent reminder.

### 4.2 Transcription
- **OpenAI Speech-to-Text** — same technology that powers ChatGPT’s mic input. Use the **Audio API** (`/v1/audio/transcriptions`) with **Whisper** (`whisper-1`) or **gpt-4o-transcribe** / **gpt-4o-mini-transcribe** for file-based transcription; optionally the **Realtime API** (WebSocket) for live microphone streaming and incremental transcripts.
- **Fast transcription** + “final” accurate pass (streaming supported for gpt-4o-transcribe models).
- **Speaker labeling** (where multi-speaker audio exists; **gpt-4o-transcribe-diarize** for diarized output).
- **Language detection** + multilingual transcription (supported by all above models).
- **Custom vocabulary:** names, acronyms, product terms (via `prompt` parameter or post-processing).
- **Timestamped transcript** + tap-to-play audio segments (e.g. `verbose_json` / `timestamp_granularities` with Whisper; segment events in Realtime API).
- **Redaction options:** auto-detect PII and mask in transcript/export.

### 4.3 “Rambling-to-structure” summarization
- **Clean summary** (remove filler, reorder thoughts).
- **Multiple views:**
  - TL;DR
  - bullets
  - narrative
  - “meeting minutes”
  - “journal reflection”
- **Role-based templates:** developer/PM/manager/founder modes.
- **Topic segmentation** + headings.
- **Decisions & rationale extraction** (decision log).

### 4.4 Action & workflow outputs (the differentiator)
#### Tasks
- **Implemented:** In-app **Checklist** (see 4.0): action items auto-extracted from transcripts at save time; To-do vs Done; complete, delete, link to recording. Task normalization (“remind me” / “need to” → actionable items) is done via LLM extraction.
- **Planned:** Verbs + owners + due dates; subtasks, priority, tags, effort estimate; task review screen with approve/edit; export targets (Apple Reminders, Google Tasks, Todoist, Asana, Jira/Linear via integrations).

#### Email & message drafts
- Generate **email drafts** from notes:
  - follow-up email
  - recap + next steps
  - request for info
  - escalation / status update
- **Tone controls:** concise, friendly, formal, assertive.
- **Recipient suggestions** from contacts (optional).
- **Slack/Teams message drafts** (shorter formats).

#### Calendar items
- Detect scheduling intent (“set up a sync next week”).
- Suggest time windows and durations.
- Draft event title, agenda, attendees.
- Create “tentative hold” vs “confirmed” (requires approval).
- Support: Google Calendar / Outlook / Apple Calendar (via integration).

#### Documents & artifacts
- Generate:
  - meeting notes doc
  - PRD / spec outline
  - interview summary
  - research notes
- Export to Google Docs / Notion / Confluence / Markdown / PDF.

#### “Follow-up Pack” (one-click)
A bundled output for meetings:
- summary
- decisions
- action list
- follow-up email
- next meeting agenda
- calendar holds
- stakeholder update message

### 4.5 Smart organization (“Evolving Folders” / knowledge graph)
- **AI-suggested folder/project** at save time.
- **Evolving folder descriptions** (hidden semantic metadata that updates as content is added).
- **Manual override:** user can edit folder definition to steer AI.
- **Auto-tagging:** people, projects, themes, mood, urgency.
- **Cross-linking:** one note can belong to multiple contexts (folder + tags).
- **Knowledge graph view:** people ↔ projects ↔ topics ↔ tasks.
- **“Smart collections”:** saved searches like “All decisions last month”.

### 4.6 Retrieval & AI search
- **Global semantic search** (“supplier issue from last week”).
- **Ask-your-notes chat** with citations back to exact audio timestamps.
- **Filters:** time range, folder, person, tag, “only tasks”, “only decisions”.
- **Highlights & key quotes** (user can pin).
- **Memory lane / resurfacing:** “On this day”, “you promised X 2 weeks ago”.

### 4.7 Journaling & reflection (first-class)
- **Private journal spaces** (not mixed with work notes if desired).
- **Daily prompts** (gratitude, wins, worries, priorities).
- **Mood tracking** (voice-inferred + user-confirmed).
- **Weekly/monthly review**:
  - themes
  - recurring stressors
  - accomplishments
  - relationship/people mentions
- **Goals & habits:** link notes to goals; habit reminders.
- **Therapy mode:** session capture → insights → questions for next session (opt-in).

### 4.8 Sharing, exporting, collaboration
- **Implemented:** Share **transcript** (copy or system share) and **audio** (Web API share; file is WebM from MediaRecorder) from the Recordings list.
- **Planned:** Share summary, follow-up pack; export formats (Markdown, PDF, DOCX, TXT).
- Shareable links (access control, expiry).
- Collaborative folders (team spaces) — optional/enterprise.

### 4.9 Integrations (workflow-friendly)
- Calendar: Google/Outlook/Apple
- Tasks: Reminders/Google Tasks/Todoist/Asana/Jira/Linear
- Notes/Docs: Notion/Google Docs/Confluence/OneNote
- Communication: Gmail/Outlook email drafts; Slack/Teams drafts
- Storage: Google Drive/Dropbox/OneDrive
- CRM (future): HubSpot/Salesforce follow-ups

> Integration philosophy: **draft-first, approve-always** (avoid surprise actions).

### 4.10 Personalization & controls
- Custom templates per folder:
  - “Meeting → minutes + tasks + email”
  - “Journal → reflective rewrite”
- Writing style presets (tone, length).
- “Always ask before creating tasks/events” toggles.
- Vocabulary list, project glossary, preferred people names.
- Safety mode: disable sensitive inferences (mood, sentiment).

### 4.11 Privacy, security, and compliance (must-have)
- End-to-end encrypted storage options (where feasible).
- Encryption at rest + in transit.
- Device-level lock (FaceID/TouchID).
- Private mode: local-only notes (no cloud/AI) for sensitive entries.
- Data controls:
  - delete audio/transcript/embeddings separately
  - retention policies
  - export all data
- Consent tooling for recordings.
- Enterprise options: SSO, admin controls, audit logs, DLP policies.

### 4.12 Quality & reliability (non-functional)
- Cold-start record latency target (<300ms ideal).
- Robust offline queueing & retries.
- Clear processing states + failure recovery.
- Accuracy feedback loop: “correct transcript” and “fix summary” training signals.
- Cost controls: per-minute processing budgets; on-device pre-processing.

### 4.13 Accessibility & inclusivity
- Captions & readable transcript modes (dyslexia-friendly fonts optional).
- VoiceOver / TalkBack support.
- Large controls for one-tap capture.
- Multi-language UI.

---

## 5) MVP vs Phase rollout

### MVP (0 → 1) — status
- **Done:** One-tap record (FAB + recording drawer with pause/stop/delete); transcription (OpenAI); **task extraction (in-app Checklist with To-do/Done)**; **manual folders** (CRUD, move recordings, folder detail).
- **Not yet:** Offline capture queue; clean summary (we have transcript + auto-title only); smart folders (AI-suggested destination); search (keyword + basic semantic); journaling mode (simple prompts + private folder).

### V1 (workflow superpowers)
- Email draft generator (with tone controls)
- Calendar draft creator (approval workflow)
- Follow-up pack for meetings
- Evolving folder descriptions + better routing
- Ask-your-notes chat with timestamp citations
- Export/share as Markdown/PDF

### V2+ (differentiation moat)
- Deep integrations (tasks/calendar/docs/slack)
- Knowledge graph & smart collections
- Team spaces + enterprise controls
- On-device models for privacy/cost
- Multi-modal capture (images, screenshots) + “Ask about this”

---

## 6) Key screens (suggested IA)

**Implemented**
- **Home:** Record button (FAB), collapsible folders, recent recordings.
- **Recording drawer:** Editable title, timer, Pause / Resume / Stop / Delete (replaces “Smart Save” for MVP).
- **Recordings:** List with play, transcript modal, rename, move, delete, share; context menu.
- **Checklist:** To-do and Done sections; items from recordings; complete / delete; link to recording title.
- **Folders:** List, create, rename, delete; folder detail with recordings in folder.
- **Settings:** Placeholder; integrations/privacy/templates not yet.

**Planned (from vision)**
- **Smart Save:** title, folder suggestions, output toggles (tasks/email/calendar/journal).
- **Note view:** audio player, transcript, summary, tasks, drafts, metadata.
- **Tasks:** export targets (today: in-app Checklist only).
- **Calendar drafts:** pending approvals.
- **Search / Ask:** chat + filters + results with timestamps.
- **Folders / Graph:** evolving descriptions + collections.
- **Journal:** daily prompt, streaks, reviews.

---

## 7) AI system behaviors (product-level)

### Confidence & safety
- Show confidence for extracted tasks/dates/people.
- Ask clarification when uncertain (“Did you mean next Tuesday or this Tuesday?”).
- Always require user approval for external actions (send/create).

### Grounded outputs
- Every generated item links back to source segments (audio timestamps + transcript lines).

### Continuous improvement loop
- “Was this helpful?” + inline edits as training signals.
- Folder steering: “belongs elsewhere” teaches routing.

---

## 8) Metrics & success criteria

### Activation
- % who create first note in 2 minutes
- time-to-first-summary
- % who accept folder suggestion

### Workflow impact
- tasks created per note
- calendar drafts created
- drafts exported/shared
- weekly active notes + retrieval searches

### Quality
- transcript edit rate
- task precision/recall proxy (user deletes vs completes)
- satisfaction on summaries (thumbs up/down)

### Retention
- D7 / D30 retention
- journaling streaks
- “resurfaced note” engagement

---

## 9) Risks & mitigations

- **Privacy concern:** offer private/local-only mode; transparent policies.
- **Hallucinated tasks/dates:** confidence UI + approvals + source links.
- **Integration complexity:** draft-first; start with exports, then API actions.
- **Cost blow-ups:** tiered processing, caching, on-device options later.
- **Legal compliance for recording:** explicit consent reminders + region settings.

---

## 10) Competitive positioning (summary)
- Meeting-first tools increasingly add integrations/agents; VoiceMind should win by being **personal-first and workflow-forward**: capture anything, then produce the exact artifacts users need (tasks/emails/calendar/journal) with minimal friction.

---

## Appendix A — Prompt/template examples (starter)
- “Convert this voice dump into: (1) clean summary (2) tasks with due dates (3) follow-up email draft (4) calendar holds. Keep each item grounded with quotes and timestamps.”
- “Rewrite as a private journal entry: preserve intent, remove filler, keep my voice.”

