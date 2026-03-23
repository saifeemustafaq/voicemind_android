---
name: Robust task extraction overhaul
overview: Overhaul the AI task extraction pipeline in `functions/src/index.ts` to use OpenAI Structured Outputs (JSON Schema mode), dramatically improve prompt quality for titles/notes/dates, increase transcript coverage, and add proper error handling — all to produce tasks that sync cleanly to Google Tasks.
todos:
  - id: structured-output
    content: Add response_format with json_schema to both extractActionItems and extractActionItemsAggressive API calls
    status: pending
  - id: rewrite-prompts
    content: "Rewrite both system prompts: better title instructions (5-15 words, imperative, self-contained, preserve names/specifics), better notes instructions (actionable details for Google Tasks description), pass current local time for date resolution"
    status: pending
  - id: increase-limits
    content: Increase transcript truncation 3000→8000 chars and max_tokens 1024→2048
    status: pending
  - id: update-parser
    content: Update parseActionItems to handle structured output format (object with items array), simplify fence-stripping, add date format validation with regex
    status: pending
  - id: error-handling
    content: Return actionItemCount from processRecording, add logging for 0-item extractions from non-trivial transcripts
    status: pending
isProject: false
---

# Robust Task Extraction Overhaul

All changes are in `**functions/src/index.ts**`.

## Problem Analysis


| Issue | Current State | Impact |
| ----- | ------------- | ------ |


- **No structured output**: Plain chat completion; model can return invalid JSON, markdown fences, or preamble text. `parseActionItems` has multiple fallback hacks and silently returns `[]` on failure.
- **Terse titles**: Prompt says "a short phrase" → produces vague titles like "Buy groceries" or "Call dentist" that lack context in Google Tasks.
- **Weak date/time extraction**: Prompt only provides today's *date* (not time), so the model can't resolve "in 2 hours" or "this afternoon." Also, no explicit examples for tricky cases like "next Tuesday" when today is Tuesday.
- **Transcript truncation at 3000 chars**: Long recordings (~5+ minutes) lose tasks mentioned in the second half.
- `**max_tokens: 1024`**: Can truncate the JSON array mid-object for recordings with many tasks, causing parse failure → `[]`.
- **Silent failures**: If extraction fails entirely, `processRecording` still returns `{ success: true }` with zero tasks and no indication to the user.

## Changes

### 1. Enable OpenAI Structured Outputs (`json_schema` mode)

Add `response_format` to both `extractActionItems` and `extractActionItemsAggressive` API calls:

```typescript
response_format: {
  type: "json_schema",
  json_schema: {
    name: "action_items",
    strict: true,
    schema: {
      type: "object",
      properties: {
        items: {
          type: "array",
          items: {
            type: "object",
            properties: {
              title: { type: "string" },
              notes: { type: ["string", "null"] },
              deadline: { type: ["string", "null"] },
              dueDate: { type: ["string", "null"] }
            },
            required: ["title", "notes", "deadline", "dueDate"],
            additionalProperties: false
          }
        }
      },
      required: ["items"],
      additionalProperties: false
    }
  }
}
```

- With `strict: true`, OpenAI guarantees the response conforms exactly to this schema — no more invalid JSON, missing fields, or wrong types.
- The response will be `{ "items": [...] }` (an object with an `items` array) instead of a bare array, since JSON Schema mode requires a top-level object.

### 2. Rewrite prompts for high-quality titles

Current: *"a short phrase describing the task"* → Replace with instructions that produce complete, standalone, actionable titles:

- Titles should be **imperative, self-contained, and 5-15 words** — enough to understand without the transcript.
- Include the **who/what/where** when mentioned (e.g., "Call Dr. Patel to reschedule Thursday cleaning" not "Call dentist").
- Specific names, places, and quantities from the transcript should be preserved in the title.

### 3. Improve notes/description quality

- Notes should capture **actionable details**: phone numbers, addresses, specific items, reasons, constraints.
- Notes will be synced as the Google Tasks description, so they should be useful standalone.

### 4. Improve date/time extraction

- **Pass current local time** (not just date) so the model can resolve "in a few hours", "this afternoon", "later tonight."
- Add more disambiguation examples for edge cases: "next Tuesday" when today is Tuesday, "this weekend" on a Friday vs Monday, etc.
- Explicitly instruct: if the speaker says a day name that is today, treat it as today (not next week).

### 5. Increase transcript limit & max_tokens

- Increase transcript truncation from **3000 → 8000 chars** (~2000 words, covers ~15 min of speech). `gpt-4o-mini` has 128k context, so this is well within limits.
- Increase `max_tokens` from **1024 → 2048** to avoid truncating large task lists.

### 6. Simplify `parseActionItems` for structured output

- With guaranteed valid JSON, remove the markdown fence-stripping and substring-hunting hacks.
- Keep a defensive `try/catch` around `JSON.parse` as a safety net.
- Parse from `response.items` (object) instead of expecting a bare array.

### 7. Add date format validation

- After parsing, validate `deadline` matches `YYYY-MM-DD` and `dueDate` matches `YYYY-MM-DDTHH:MM:SS` with a regex before passing to the date converters.
- Log a warning (not silent drop) for malformed dates.

### 8. Surface extraction failures

- When extraction produces 0 items from a non-trivial transcript (>100 chars), log a structured warning with the transcript length and raw response for debugging.
- In `processRecording`, include `actionItemCount` in the return so the client knows how many tasks were created.

