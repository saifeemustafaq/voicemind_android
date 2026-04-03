/**
 * Returns local wall-clock components for a UTC Date in the given IANA timezone.
 * Uses Intl.DateTimeFormat.formatToParts() — the only correct zero-dep approach.
 */
export function getLocalComponents(
  utcDate: Date,
  tz: string
): { year: number; month: number; day: number; hour: number; minute: number } {
  const parts = new Intl.DateTimeFormat("en-US", {
    timeZone: tz,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  }).formatToParts(utcDate);
  const get = (type: string) =>
    parseInt(parts.find((p) => p.type === type)?.value ?? "0");
  return {
    year: get("year"),
    month: get("month") - 1, // 0-indexed for Date.UTC()
    day: get("day"),
    hour: get("hour") % 24, // Intl can return 24 for midnight in some locales
    minute: get("minute"),
  };
}

/**
 * Converts a wall-clock datetime in the given IANA timezone to a UTC Date.
 * Handles DST transitions by correcting the naive UTC guess.
 */
export function localToUtc(
  year: number,
  month0: number,
  day: number,
  hour: number,
  minute: number,
  tz: string
): Date {
  const guess = new Date(Date.UTC(year, month0, day, hour, minute, 0));
  const check = getLocalComponents(guess, tz);
  let diffMin = hour * 60 + minute - (check.hour * 60 + check.minute);
  if (diffMin > 720) diffMin -= 1440;
  if (diffMin < -720) diffMin += 1440;
  return new Date(guess.getTime() + diffMin * 60_000);
}

/**
 * Converts a local datetime string (YYYY-MM-DDTHH:MM:SS, no offset) to a UTC Date
 * using the given IANA timezone, correctly accounting for DST at the event date.
 *
 * Why: If we bake the current UTC offset into the datetime string (e.g. -05:00)
 * but the event falls after a DST boundary (e.g. March 8 spring-forward), the
 * stored UTC timestamp ends up 1 hour wrong and the calendar event appears shifted.
 */
export function parseLocalDateTimeInTimezone(localStr: string, timezone: string): Date | null {
  if (!localStr) return null;

  // Strip any trailing offset that GPT might still include (e.g. "+05:00", "-08:00", "Z")
  const stripped = localStr.replace(/([+-]\d{2}:\d{2}|Z)$/, "").trim();
  if (!stripped.includes("T")) return null;

  const [datePart, timePart] = stripped.split("T");
  const [year, month, day] = datePart.split("-").map(Number);
  const timeSplit = (timePart || "00:00:00").split(":");
  const hour = parseInt(timeSplit[0] ?? "0", 10);
  const minute = parseInt(timeSplit[1] ?? "0", 10);

  if ([year, month, day, hour].some(isNaN)) return null;

  const approxUtc = new Date(Date.UTC(year, month - 1, day, hour, minute, 0));
  const formatter = new Intl.DateTimeFormat("en-US", {
    timeZone: timezone,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  });
  const parts = formatter.formatToParts(approxUtc);
  const get = (type: string) =>
    parseInt(parts.find((p) => p.type === type)?.value ?? "0", 10);

  const tzHour = get("hour") % 24;
  const actualLocalMs = Date.UTC(get("year"), get("month") - 1, get("day"), tzHour, get("minute"), 0);
  const desiredLocalMs = Date.UTC(year, month - 1, day, hour, minute, 0);

  const result = new Date(approxUtc.getTime() + (desiredLocalMs - actualLocalMs));
  return isNaN(result.getTime()) ? null : result;
}

/**
 * Parses a date-only string (YYYY-MM-DD) as noon UTC so the date
 * is always correct regardless of which timezone displays it.
 */
export function parseDateAsNoonUtc(dateStr: string): Date | null {
  const d = new Date(`${dateStr}T12:00:00Z`);
  return isNaN(d.getTime()) ? null : d;
}
