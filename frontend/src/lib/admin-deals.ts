import "server-only";

import { redirect } from "next/navigation";

import { apiFetch } from "@/lib/api";
import type { DealState } from "@/lib/deals";

// Server-side access to the catalog's back-office API through the gateway.

export type AdminDeal = {
  id: string;
  title: string;
  description: string;
  imageKey: string | null;
  imageUrl: string | null;
  currency: "NZD";
  priceCents: number;
  rrpCents: number;
  gstCents: number;
  stock: number;
  perCustomerLimit: number;
  startsAt: string;
  endsAt: string;
  status: "DRAFT" | "PUBLISHED";
  state: DealState;
  createdAt: string;
  updatedAt: string;
};

export type AdminDealPage = {
  content: AdminDeal[];
  page: { size: number; number: number; totalElements: number; totalPages: number };
};

/** `page` is zero-based, as the backend counts. */
export async function getAdminDeals(token: string, page: number): Promise<AdminDealPage> {
  const response = await apiFetch(`/api/catalog/admin/deals?page=${page}`, { token });
  signInAgainIfExpired(response, "/admin");
  if (!response.ok) {
    throw new Error(`Failed to load deals: HTTP ${response.status}`);
  }
  return response.json();
}

/** Null if the deal doesn't exist. */
export async function getAdminDeal(token: string, id: string): Promise<AdminDeal | null> {
  const response = await apiFetch(`/api/catalog/admin/deals/${encodeURIComponent(id)}`, { token });
  signInAgainIfExpired(response, `/admin/deals/${id}`);
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load deal ${id}: HTTP ${response.status}`);
  }
  return response.json();
}

/** The cookie can outlive a token the backend no longer accepts, e.g. after identity restarts. */
export function signInAgainIfExpired(response: Response, returnTo: string) {
  if (response.status === 401) {
    redirect(`/login?next=${encodeURIComponent(returnTo)}`);
  }
}
