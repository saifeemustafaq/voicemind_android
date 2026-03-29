import { onDocumentCreated } from "firebase-functions/v2/firestore";
import * as admin from "firebase-admin";
import { db } from "./lib/firestore.js";
import { getLocalComponents, localToUtc } from "./lib/dateUtils.js";

/**
 * Firestore trigger that auto-assigns a dueDate to newly created action items
 * that have no date, when the user has Natural Time Selection enabled.
 */
export const autoScheduleActionItem = onDocumentCreated(
  { document: "users/{uid}/actionItems/{itemId}" },
  async (event) => {
    const uid = event.params.uid;
    const itemId = event.params.itemId;
    const data = event.data?.data();

    if (!data) return;
    if (data.dueDate || data.deadline) return;

    const userDoc = await db.collection("users").doc(uid).get();
    const userData = userDoc.data();
    if (!userData || userData.ntsEnabled !== true) return;

    const startHour: number =
      typeof userData.ntsStartHour === "number" ? userData.ntsStartHour : 22;
    const startMinute: number =
      typeof userData.ntsStartMinute === "number" ? userData.ntsStartMinute : 0;
    const intervalMinutes: number =
      typeof userData.ntsIntervalMinutes === "number" ? userData.ntsIntervalMinutes : 30;
    const tz: string = (userData.timezone as string) || "UTC";

    const now = new Date();
    const local = getLocalComponents(now, tz);

    // Decide target date: today if before start time, tomorrow otherwise
    const currentMinuteOfDay = local.hour * 60 + local.minute;
    const startMinuteOfDay = startHour * 60 + startMinute;

    let targetYear = local.year;
    let targetMonth = local.month;
    let targetDay = local.day;

    if (currentMinuteOfDay >= startMinuteOfDay) {
      // Already past the start time — advance target to tomorrow in local TZ
      const nextDayUtc = new Date(Date.UTC(local.year, local.month, local.day + 1));
      const nextLocal = getLocalComponents(nextDayUtc, tz);
      targetYear = nextLocal.year;
      targetMonth = nextLocal.month;
      targetDay = nextLocal.day;
    }

    // Window: target day at startHour:startMinute → next day at startHour:startMinute
    // Passing day+1 is safe — Date.UTC normalises month/day overflow.
    const windowStartUtc = localToUtc(targetYear, targetMonth, targetDay, startHour, startMinute, tz);
    const windowEndUtc = localToUtc(targetYear, targetMonth, targetDay + 1, startHour, startMinute, tz);

    // Atomically claim the next sequential slot index for this target date.
    // Prevents the race condition where concurrent triggers all query the
    // same occupied set and land on the same slot.
    const targetDateStr = `${targetYear}-${String(targetMonth + 1).padStart(2, "0")}-${String(targetDay).padStart(2, "0")}`;
    const counterRef = db
      .collection("users").doc(uid)
      .collection("ntsCounters").doc(targetDateStr);

    let slotIndex = 0;
    await db.runTransaction(async (tx) => {
      const counterDoc = await tx.get(counterRef);
      slotIndex = counterDoc.exists ? (counterDoc.data()?.nextIndex ?? 0) : 0;
      tx.set(counterRef, { nextIndex: slotIndex + 1 }, { merge: true });
    });

    const intervalMs = intervalMinutes * 60_000;
    let slotUtc = new Date(windowStartUtc.getTime() + slotIndex * intervalMs);

    // All slots exhausted — fall back to window end (start of next day's window)
    if (slotUtc >= windowEndUtc) slotUtc = windowEndUtc;

    const assignedTs = admin.firestore.Timestamp.fromDate(slotUtc);
    await event.data?.ref.update({ dueDate: assignedTs, autoScheduled: true });
    console.log(`NTS: auto-scheduled item ${itemId} for user ${uid} at ${slotUtc.toISOString()}`);
  }
);
