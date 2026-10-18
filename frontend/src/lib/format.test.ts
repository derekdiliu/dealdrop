import { describe, expect, it } from "vitest";

import { discountPercent, formatNzDateTime, formatNzd, fromNzInputValue, toNzInputValue } from "@/lib/format";

describe("fromNzInputValue", () => {
  it("adds +13:00 in daylight saving and +12:00 outside it", () => {
    expect(fromNzInputValue("2026-11-20T12:00")).toBe("2026-11-20T12:00:00+13:00");
    expect(fromNzInputValue("2026-06-01T09:30")).toBe("2026-06-01T09:30:00+12:00");
  });

  it("changes offset when the clocks change", () => {
    // Daylight saving starts at 2am on 27 September 2026 and ends at 3am on 5 April 2026.
    expect(fromNzInputValue("2026-09-27T01:59")).toBe("2026-09-27T01:59:00+12:00");
    expect(fromNzInputValue("2026-09-27T03:00")).toBe("2026-09-27T03:00:00+13:00");
    expect(fromNzInputValue("2026-04-05T03:00")).toBe("2026-04-05T03:00:00+12:00");
  });

  it("drops seconds", () => {
    expect(fromNzInputValue("2026-11-20T12:00:45")).toBe("2026-11-20T12:00:00+13:00");
  });

  it.each(["", "2026-11-20", "20/11/2026 12:00", "2026-11-20 12:00", "soon"])("rejects %j", (value) => {
    expect(fromNzInputValue(value)).toBeNull();
  });
});

describe("toNzInputValue", () => {
  it("shows an instant as NZ wall-clock time", () => {
    expect(toNzInputValue("2026-11-19T23:00:00Z")).toBe("2026-11-20T12:00");
    expect(toNzInputValue("2026-05-31T21:30:00Z")).toBe("2026-06-01T09:30");
  });

  it.each(["2026-01-01T00:00", "2026-09-27T03:00", "2026-04-05T02:30", "2026-12-31T23:59"])(
    "round-trips %s",
    (value) => {
      expect(toNzInputValue(fromNzInputValue(value)!)).toBe(value);
    },
  );
});

describe("display formats", () => {
  it("formats cents as NZD", () => {
    expect(formatNzd(123450)).toBe("$1,234.50");
    expect(formatNzd(5)).toBe("$0.05");
  });

  it("shows times in NZ whatever the machine's time zone", () => {
    expect(formatNzDateTime("2026-11-19T23:00:00Z")).toBe("Fri, 20 Nov, 12:00 pm");
  });

  it("rounds the discount to a whole percent", () => {
    expect(discountPercent(2900, 4500)).toBe(36);
    expect(discountPercent(7999, 15900)).toBe(50);
  });
});
