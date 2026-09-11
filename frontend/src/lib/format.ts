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
