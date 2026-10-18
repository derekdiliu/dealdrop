"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";

import type { FormState } from "@/app/actions/auth";
import { signInAgainIfExpired } from "@/lib/admin-deals";
import { apiFetch, problemMessage } from "@/lib/api";
import { fromNzInputValue } from "@/lib/format";
import { submittedValues } from "@/lib/forms";
import { requireToken } from "@/lib/session";

// Back-office actions. The backend rejects anyone who isn't an admin.

/** Creates a draft when dealId is null, otherwise saves changes to that draft. */
export async function saveDeal(dealId: string | null, _state: FormState, form: FormData): Promise<FormState> {
  const returnTo = dealId ? `/admin/deals/${dealId}` : "/admin/deals/new";
  const token = await requireToken(returnTo);
  const parsed = parseDeal(form);
  if ("error" in parsed) {
    return { error: parsed.error, values: submittedValues(form) };
  }
  const response = await apiFetch(dealId ? `/api/catalog/admin/deals/${encodeURIComponent(dealId)}` : "/api/catalog/admin/deals", {
    method: dealId ? "PUT" : "POST",
    token,
    body: JSON.stringify(parsed.deal),
  });
  signInAgainIfExpired(response, returnTo);
  if (!response.ok) {
    return { error: await problemMessage(response), values: submittedValues(form) };
  }
  if (dealId) {
    refresh();
    return {};
  }
  const { id } = await response.json();
  redirect(`/admin/deals/${id}`);
}

export type ImageUpload = { key: string; uploadUrl: string; headers: Record<string, string> } | { error: string };

/** A short-lived URL the browser uploads one image to, straight to storage. */
export async function requestImageUpload(contentType: string, contentLength: number): Promise<ImageUpload> {
  const token = await requireToken("/admin");
  const response = await apiFetch("/api/catalog/admin/images", {
    method: "POST",
    token,
    body: JSON.stringify({ contentType, contentLength }),
  });
  if (!response.ok) {
    return { error: response.status === 401 ? "Please sign in again." : await problemMessage(response) };
  }
  const { key, uploadUrl, headers } = await response.json();
  return { key, uploadUrl, headers };
}

export async function publishDeal(dealId: string): Promise<FormState> {
  const returnTo = `/admin/deals/${dealId}`;
  const token = await requireToken(returnTo);
  const response = await apiFetch(`/api/catalog/admin/deals/${encodeURIComponent(dealId)}/publish`, { method: "POST", token });
  signInAgainIfExpired(response, returnTo);
  if (!response.ok) {
    return { error: await problemMessage(response) };
  }
  refresh();
  return {};
}

export async function deleteDeal(dealId: string): Promise<FormState> {
  const returnTo = `/admin/deals/${dealId}`;
  const token = await requireToken(returnTo);
  const response = await apiFetch(`/api/catalog/admin/deals/${encodeURIComponent(dealId)}`, { method: "DELETE", token });
  signInAgainIfExpired(response, returnTo);
  if (!response.ok) {
    return { error: await problemMessage(response) };
  }
  redirect("/admin");
}

type DealRequest = {
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
function parseDeal(form: FormData): { deal: DealRequest } | { error: string } {
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
