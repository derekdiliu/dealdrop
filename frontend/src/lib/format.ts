const nzd = new Intl.NumberFormat("en-NZ", { style: "currency", currency: "NZD" });

// Always show New Zealand time, whatever the server's or visitor's time zone.
const nzDateTime = new Intl.DateTimeFormat("en-NZ", {
  timeZone: "Pacific/Auckland",
  weekday: "short",
  day: "numeric",
  month: "short",
  hour: "numeric",
  minute: "2-digit",
});

export function formatNzd(cents: number): string {
  return nzd.format(cents / 100);
}

export function formatNzDateTime(iso: string): string {
  return nzDateTime.format(new Date(iso));
}

export function discountPercent(priceCents: number, rrpCents: number): number {
  return Math.round((1 - priceCents / rrpCents) * 100);
}

// Admin forms take times as NZ wall-clock time in datetime-local inputs ("2026-11-20T12:00").

const nzInputParts = new Intl.DateTimeFormat("en-NZ", {
  timeZone: "Pacific/Auckland",
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
});

const nzOffsetName = new Intl.DateTimeFormat("en-US", {
  timeZone: "Pacific/Auckland",
  timeZoneName: "longOffset",
});

export function toNzInputValue(iso: string): string {
  const parts = Object.fromEntries(nzInputParts.formatToParts(new Date(iso)).map((p) => [p.type, p.value]));
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}`;
}

/**
 * The input's NZ time with its UTC offset (+13:00 in daylight saving, +12:00 otherwise), e.g.
 * "2026-11-20T12:00:00+13:00", or null if it isn't a date and time.
 */
export function fromNzInputValue(value: string): string | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(:\d{2})?$/.exec(value);
  if (!match) {
    return null;
  }
  const [, year, month, day, hour, minute] = match;
  const wallClockAsUtc = Date.UTC(+year, +month - 1, +day, +hour, +minute);
  // The offset near that time, then at the instant it gives: right except within the hour the
  // clocks skip or repeat, where either offset is close enough.
  const offset = nzOffsetMinutes(wallClockAsUtc - nzOffsetMinutes(wallClockAsUtc) * 60_000);
  const pad = (n: number) => String(n).padStart(2, "0");
  const sign = offset < 0 ? "-" : "+";
  return `${year}-${month}-${day}T${hour}:${minute}:00${sign}${pad(Math.floor(Math.abs(offset) / 60))}:${pad(Math.abs(offset) % 60)}`;
}

function nzOffsetMinutes(instant: number): number {
  const name = nzOffsetName.formatToParts(new Date(instant)).find((p) => p.type === "timeZoneName")?.value ?? "";
  const match = /GMT([+-])(\d{2}):(\d{2})/.exec(name);
  return match ? (match[1] === "-" ? -1 : 1) * (Number(match[2]) * 60 + Number(match[3])) : 0;
}
