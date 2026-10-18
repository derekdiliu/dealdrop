import { fromNzInputValue } from "@/lib/format";

// The back-office deal form's fields turned into the catalog's request. Kept out of the "use server"
// actions file, where every export would become a callable server action.

export type DealRequest = {
  title: string;
  description: string;
  imageKey: string | null;
  rrpCents: number;
  priceCents: number;
  stock: number;
  perCustomerLimit: number;
  startsAt: string;
  endsAt: string;
};

/** Checks the same rules as the backend, so a mistake gets a specific message. */
export function parseDeal(form: FormData): { deal: DealRequest } | { error: string } {
  const text = (name: string) => {
    const value = form.get(name);
    return typeof value === "string" ? value.trim() : "";
  };
  const title = text("title");
  const description = text("description");
  if (!title || title.length > 120) {
    return { error: "Enter a title of up to 120 characters." };
  }
  if (!description || description.length > 4000) {
    return { error: "Enter a description of up to 4,000 characters." };
  }
  const rrpCents = cents(text("rrp"));
  const priceCents = cents(text("price"));
  if (rrpCents === null || priceCents === null) {
    return { error: "Enter prices in dollars, e.g. 49.99." };
  }
  if (priceCents > rrpCents) {
    return { error: "The deal price can't be more than the RRP." };
  }
  const stock = wholeNumber(text("stock"));
  const perCustomerLimit = wholeNumber(text("perCustomerLimit"));
  if (stock === null || stock < 1 || stock > 100_000) {
    return { error: "Stock must be a whole number from 1 to 100,000." };
  }
  if (perCustomerLimit === null || perCustomerLimit < 1 || perCustomerLimit > stock) {
    return { error: "The per-customer limit must be at least 1 and no more than the stock." };
  }
  const startsAt = fromNzInputValue(text("startsAt"));
  const endsAt = fromNzInputValue(text("endsAt"));
  if (!startsAt || !endsAt) {
    return { error: "Enter when the deal starts and ends." };
  }
  if (Date.parse(endsAt) <= Date.parse(startsAt)) {
    return { error: "The deal must end after it starts." };
  }
  return {
    deal: { title, description, imageKey: text("imageKey") || null, rrpCents, priceCents, stock, perCustomerLimit, startsAt, endsAt },
  };
}

function cents(dollars: string): number | null {
  return /^\d{1,7}(\.\d{1,2})?$/.test(dollars) && Number(dollars) > 0 ? Math.round(Number(dollars) * 100) : null;
}

function wholeNumber(value: string): number | null {
  return /^\d{1,9}$/.test(value) ? Number(value) : null;
}
