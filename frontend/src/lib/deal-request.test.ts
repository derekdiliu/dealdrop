import { describe, expect, it } from "vitest";

import { parseDeal } from "@/lib/deal-request";

/** A valid submission of the deal form, with some fields replaced. */
function form(fields: Record<string, string> = {}): FormData {
  const data = new FormData();
  const values = {
    title: "Merino throw",
    description: "100% NZ merino.",
    imageKey: "deals/throw.jpg",
    rrp: "159",
    price: "79.99",
    stock: "50",
    perCustomerLimit: "2",
    startsAt: "2026-11-20T12:00",
    endsAt: "2026-11-22T12:00",
    ...fields,
  };
  Object.entries(values).forEach(([name, value]) => data.set(name, value));
  return data;
}

describe("parseDeal", () => {
  it("builds the catalog request: cents, trimmed text, NZ times with their offset", () => {
    expect(parseDeal(form({ title: "  Merino throw  ", description: "100% NZ merino.\n" }))).toEqual({
      deal: {
        title: "Merino throw",
        description: "100% NZ merino.",
        imageKey: "deals/throw.jpg",
        rrpCents: 15900,
        priceCents: 7999,
        stock: 50,
        perCustomerLimit: 2,
        startsAt: "2026-11-20T12:00:00+13:00",
        endsAt: "2026-11-22T12:00:00+13:00",
      },
    });
  });

  it("converts dollars to cents without floating-point drift", () => {
    // 0.29 * 100 is 28.999999999999996 in floating point.
    expect(parseDeal(form({ rrp: "0.29", price: "0.29" }))).toMatchObject({ deal: { rrpCents: 29, priceCents: 29 } });
    expect(parseDeal(form({ rrp: "19.9", price: "1.1" }))).toMatchObject({ deal: { rrpCents: 1990, priceCents: 110 } });
  });

  it("allows a price equal to the RRP, and a limit equal to the stock", () => {
    expect(parseDeal(form({ price: "159", perCustomerLimit: "50" }))).toHaveProperty("deal");
  });

  it("sends no image key when nothing was uploaded", () => {
    expect(parseDeal(form({ imageKey: "" }))).toMatchObject({ deal: { imageKey: null } });
  });

  it.each([
    [{ title: "" }, "Enter a title of up to 120 characters."],
    [{ title: "x".repeat(121) }, "Enter a title of up to 120 characters."],
    [{ description: "  " }, "Enter a description of up to 4,000 characters."],
    [{ price: "79.999" }, "Enter prices in dollars, e.g. 49.99."],
    [{ price: "-5" }, "Enter prices in dollars, e.g. 49.99."],
    [{ rrp: "0" }, "Enter prices in dollars, e.g. 49.99."],
    [{ rrp: "$159" }, "Enter prices in dollars, e.g. 49.99."],
    [{ price: "159.01" }, "The deal price can't be more than the RRP."],
    [{ stock: "0" }, "Stock must be a whole number from 1 to 100,000."],
    [{ stock: "100001" }, "Stock must be a whole number from 1 to 100,000."],
    [{ stock: "2.5" }, "Stock must be a whole number from 1 to 100,000."],
    [{ perCustomerLimit: "0" }, "The per-customer limit must be at least 1 and no more than the stock."],
    [{ perCustomerLimit: "51" }, "The per-customer limit must be at least 1 and no more than the stock."],
    [{ endsAt: "" }, "Enter when the deal starts and ends."],
    [{ endsAt: "2026-11-20T12:00" }, "The deal must end after it starts."],
  ])("rejects %j", (fields, error) => {
    expect(parseDeal(form(fields))).toEqual({ error });
  });
});
