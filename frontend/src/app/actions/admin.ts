"use server";

import { refresh } from "next/cache";
import { redirect } from "next/navigation";

import type { FormState } from "@/app/actions/auth";
import { signInAgainIfExpired } from "@/lib/admin-deals";
import { apiFetch, problemMessage } from "@/lib/api";
import { parseDeal } from "@/lib/deal-request";
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
