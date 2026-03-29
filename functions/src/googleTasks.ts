import { onCall, HttpsError } from "firebase-functions/v2/https";
import { onDocumentWritten } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { google } from "googleapis";
import { googleClientSecret, WEB_CLIENT_ID } from "./lib/config.js";
import { db } from "./lib/firestore.js";

// ── OAuth helper ─────────────────────────────────────────────────────────────

function createOAuth2Client(refreshToken?: string) {
  const client = new google.auth.OAuth2(
    WEB_CLIENT_ID,
    googleClientSecret.value(),
    "" // redirect_uri not needed for token exchange
  );
  if (refreshToken) {
    client.setCredentials({ refresh_token: refreshToken });
  }
  return client;
}

// ── Token lifecycle ──────────────────────────────────────────────────────────

async function handleTokenExpired(uid: string) {
  console.warn(`Google token expired/revoked for user ${uid}, disconnecting`);
  await db.collection("tasksTokens").doc(uid).delete();
  await db.collection("users").doc(uid).set({ tasksConnected: false }, { merge: true });
}

// ── Task/Calendar builders ───────────────────────────────────────────────────

/**
 * Builds the Google Tasks requestBody from action item fields.
 * - deadline takes priority over dueDate for the Tasks due field (date-only).
 *   The exact time from dueDate is preserved separately via a Calendar event.
 * - completed maps to status: "completed" | "needsAction".
 */
function buildGoogleTask(
  title: string,
  dueDate?: admin.firestore.Timestamp,
  deadline?: admin.firestore.Timestamp,
  notes?: string,
  completed?: boolean
): Record<string, unknown> {
  const task: Record<string, unknown> = {
    title,
    notes: notes || "",
    status: completed ? "completed" : "needsAction",
  };
  if (deadline) {
    const dateStr = deadline.toDate().toISOString().split("T")[0];
    task.due = `${dateStr}T12:00:00.000Z`;
  } else if (dueDate) {
    const dateStr = dueDate.toDate().toISOString().split("T")[0];
    task.due = `${dateStr}T12:00:00.000Z`;
  }
  return task;
}

/**
 * Builds a Google Calendar event body for a dueDate action item.
 * Creates a 30-minute event at the exact time recorded.
 */
function buildCalendarEvent(
  title: string,
  dueDate: admin.firestore.Timestamp,
  notes?: string
): Record<string, unknown> {
  const start = dueDate.toDate();
  const end = new Date(start.getTime() + 30 * 60 * 1000);
  return {
    summary: title,
    description: notes || "",
    start: { dateTime: start.toISOString() },
    end: { dateTime: end.toISOString() },
  };
}

// ── Google Tasks write helpers ───────────────────────────────────────────────

async function createAndStoreTask(
  tasks: ReturnType<typeof google.tasks>,
  taskBody: Record<string, unknown>,
  event: Parameters<Parameters<typeof onDocumentWritten>[1]>[0],
  itemId: string
) {
  try {
    const created = await tasks.tasks.insert({
      tasklist: "@default",
      requestBody: taskBody,
    });
    if (created.data.id) {
      await event.data?.after?.ref.update({ googleTaskId: created.data.id });
    }
  } catch (err: unknown) {
    const e = err as { code?: number; status?: number };
    if (e.code === 401 || e.status === 401) {
      await handleTokenExpired(event.params.uid);
    } else {
      console.error(`Failed to create Google Task for item ${itemId}:`, err);
    }
  }
}

async function deleteGoogleTask(
  tasks: ReturnType<typeof google.tasks>,
  taskId: string,
  uid: string
) {
  try {
    await tasks.tasks.delete({ tasklist: "@default", task: taskId });
  } catch (err: unknown) {
    const e = err as { code?: number; status?: number };
    if (e.code === 404 || e.status === 404) {
      console.warn(`Google Task ${taskId} already deleted`);
    } else if (e.code === 401 || e.status === 401) {
      await handleTokenExpired(uid);
    } else {
      console.error(`Failed to delete Google Task ${taskId}:`, err);
    }
  }
}

// ── Google Calendar write helpers ────────────────────────────────────────────

async function createAndStoreCalendarEvent(
  calendar: ReturnType<typeof google.calendar>,
  eventBody: Record<string, unknown>,
  event: Parameters<Parameters<typeof onDocumentWritten>[1]>[0],
  itemId: string
) {
  try {
    const created = await calendar.events.insert({
      calendarId: "primary",
      requestBody: eventBody,
    });
    if (created.data.id) {
      await event.data?.after?.ref.update({ calendarEventId: created.data.id });
    }
  } catch (err: unknown) {
    const e = err as { code?: number; status?: number };
    if (e.code === 401 || e.status === 401 || e.code === 403 || e.status === 403) {
      console.warn(`Calendar scope not available for user ${event.params.uid}, skipping calendar event creation`);
    } else {
      console.error(`Failed to create Calendar event for item ${itemId}:`, err);
    }
  }
}

async function deleteCalendarEvent(
  calendar: ReturnType<typeof google.calendar>,
  eventId: string,
  uid: string
) {
  try {
    await calendar.events.delete({ calendarId: "primary", eventId });
  } catch (err: unknown) {
    const e = err as { code?: number; status?: number };
    if (e.code === 404 || e.status === 404) {
      console.warn(`Calendar event ${eventId} already deleted`);
    } else if (e.code === 401 || e.status === 401 || e.code === 403 || e.status === 403) {
      console.warn(`Calendar scope not available for user ${uid}, cannot delete calendar event`);
    } else {
      console.error(`Failed to delete Calendar event ${eventId}:`, err);
    }
  }
}

// ── Exported Cloud Functions ─────────────────────────────────────────────────

/**
 * Exchanges a Google authorization code (with tasks scope)
 * for a refresh token and stores it securely for server-side Tasks sync.
 */
export const exchangeTasksAuthCode = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const { authCode } = request.data as { authCode?: string };

    if (!authCode) {
      throw new HttpsError("invalid-argument", "authCode is required");
    }

    const oauth2Client = createOAuth2Client();
    let tokens;
    try {
      const response = await oauth2Client.getToken(authCode);
      tokens = response.tokens;
    } catch (err) {
      console.error("Token exchange failed:", err);
      throw new HttpsError("internal", "Failed to exchange authorization code");
    }

    if (!tokens.refresh_token) {
      throw new HttpsError(
        "internal",
        "No refresh token received. The user may need to revoke access and reconnect."
      );
    }

    await db.collection("tasksTokens").doc(uid).set({
      refreshToken: tokens.refresh_token,
      connectedAt: admin.firestore.FieldValue.serverTimestamp(),
    });
    await db.collection("users").doc(uid).set({ tasksConnected: true }, { merge: true });

    return { success: true };
  }
);

/**
 * Revokes the stored Google Tasks refresh token and cleans up.
 */
export const disconnectTasks = onCall(
  { secrets: [googleClientSecret] },
  async (request) => {
    if (!request.auth) {
      throw new HttpsError("unauthenticated", "User must be signed in");
    }

    const uid = request.auth.uid;
    const tokenDoc = await db.collection("tasksTokens").doc(uid).get();

    if (tokenDoc.exists) {
      const refreshToken = tokenDoc.data()?.refreshToken as string | undefined;
      if (refreshToken) {
        try {
          await createOAuth2Client(refreshToken).revokeToken(refreshToken);
        } catch (err) {
          console.warn("Token revocation failed (may already be revoked):", err);
        }
      }
      await db.collection("tasksTokens").doc(uid).delete();
    }

    await db.collection("users").doc(uid).set({ tasksConnected: false }, { merge: true });
    return { success: true };
  }
);

/**
 * Firestore trigger that syncs actionItem changes to Google Tasks and Calendar.
 * Creates, updates, or deletes tasks when title/dueDate/deadline/completed changes.
 */
export const syncActionItemToGoogleTasks = onDocumentWritten(
  {
    document: "users/{uid}/actionItems/{itemId}",
    secrets: [googleClientSecret],
  },
  async (event) => {
    const uid = event.params.uid;
    const itemId = event.params.itemId;

    const before = event.data?.before?.data();
    const after = event.data?.after?.data();

    // Guard: skip if only metadata fields changed (prevents infinite loop when
    // we write back googleTaskId / calendarEventId / autoScheduled)
    if (before && after) {
      const beforeCopy = { ...before };
      const afterCopy = { ...after };
      delete beforeCopy.googleTaskId;
      delete afterCopy.googleTaskId;
      delete beforeCopy.calendarEventId;
      delete afterCopy.calendarEventId;
      delete beforeCopy.autoScheduled;
      delete afterCopy.autoScheduled;
      if (JSON.stringify(beforeCopy) === JSON.stringify(afterCopy)) return;
    }

    const tokenDoc = await db.collection("tasksTokens").doc(uid).get();
    if (!tokenDoc.exists) return;

    const refreshToken = tokenDoc.data()?.refreshToken as string | undefined;
    if (!refreshToken) return;

    const oauth2Client = createOAuth2Client(refreshToken);
    const tasks = google.tasks({ version: "v1", auth: oauth2Client });
    const calendar = google.calendar({ version: "v3", auth: oauth2Client });

    const oldTaskId = before?.googleTaskId as string | undefined;
    const taskId = (after?.googleTaskId ?? oldTaskId) as string | undefined;
    const oldCalEventId = before?.calendarEventId as string | undefined;
    const calEventId = (after?.calendarEventId ?? oldCalEventId) as string | undefined;

    // Document deleted
    if (!after) {
      if (taskId) await deleteGoogleTask(tasks, taskId, uid);
      if (calEventId) await deleteCalendarEvent(calendar, calEventId, uid);
      return;
    }

    const dueDate = after.dueDate as admin.firestore.Timestamp | undefined;
    const deadline = after.deadline as admin.firestore.Timestamp | undefined;
    const title = (after.title as string) || "VoiceMind Task";
    const notes = (after.notes as string | undefined) || undefined;
    const completed = !!(after.completed as boolean);
    const hasDate = !!(dueDate || deadline);

    // Date removed and no existing task — clean up calendar event if any
    if (!hasDate && !taskId) {
      if (calEventId) {
        await deleteCalendarEvent(calendar, calEventId, uid);
        await event.data?.after?.ref.update({ calendarEventId: admin.firestore.FieldValue.delete() });
      }
      return;
    }

    // Date removed — delete task and calendar event
    if (!hasDate && taskId) {
      await deleteGoogleTask(tasks, taskId, uid);
      const removals: Record<string, unknown> = { googleTaskId: admin.firestore.FieldValue.delete() };
      if (calEventId) {
        await deleteCalendarEvent(calendar, calEventId, uid);
        removals.calendarEventId = admin.firestore.FieldValue.delete();
      }
      await event.data?.after?.ref.update(removals);
      return;
    }

    // ── Sync to Google Tasks ──────────────────────────────────────────────────
    const taskBody = buildGoogleTask(title, dueDate, deadline, notes, completed);

    if (taskId) {
      try {
        await tasks.tasks.update({ tasklist: "@default", task: taskId, requestBody: taskBody });
      } catch (err: unknown) {
        const e = err as { code?: number; status?: number };
        if (e.code === 404 || e.status === 404) {
          console.warn(`Google Task ${taskId} not found, creating new one`);
          await createAndStoreTask(tasks, taskBody, event, itemId);
        } else if (e.code === 401 || e.status === 401) {
          await handleTokenExpired(uid);
        } else {
          console.error("Google Task update failed:", err);
        }
      }
    } else {
      await createAndStoreTask(tasks, taskBody, event, itemId);
    }

    // ── Sync to Google Calendar (only for dueDate items — preserves time) ────
    if (dueDate) {
      const eventBody = buildCalendarEvent(title, dueDate, notes);
      if (calEventId) {
        try {
          await calendar.events.update({
            calendarId: "primary",
            eventId: calEventId,
            requestBody: eventBody,
          });
        } catch (err: unknown) {
          const e = err as { code?: number; status?: number };
          if (e.code === 404 || e.status === 404) {
            await createAndStoreCalendarEvent(calendar, eventBody, event, itemId);
          } else if (e.code === 401 || e.status === 401 || e.code === 403 || e.status === 403) {
            console.warn(`Calendar scope not available for user ${uid}, skipping calendar sync`);
          } else {
            console.error("Calendar event update failed:", err);
          }
        }
      } else {
        await createAndStoreCalendarEvent(calendar, eventBody, event, itemId);
      }
    } else if (calEventId) {
      // dueDate cleared but calendar event remains — delete it
      await deleteCalendarEvent(calendar, calEventId, uid);
      await event.data?.after?.ref.update({ calendarEventId: admin.firestore.FieldValue.delete() });
    }
  }
);
