import "server-only";

import { apiFetch } from "@/lib/api";

// Server-side access to the catalog API through the gateway.

export type DealState = "UPCOMING" | "LIVE" | "ENDED";

export type Deal = {
  id: string;
  title: string;
  description: string;
  imageUrl: string;
  currency: "NZD";
  priceCents: number;
  rrpCents: number;
  gstCents: number;
  stock: number;
  perCustomerLimit: number;
  startsAt: string;
  endsAt: string;
  state: DealState;
};

export async function getDeals(): Promise<Deal[]> {
  const response = await apiFetch("/api/catalog/deals");
  if (!response.ok) {
    throw new Error(`Failed to load deals: HTTP ${response.status}`);
  }
  return response.json();
}

/** Units still available while a deal is live; null if the sale service can't be reached. */
export async function getRemainingStock(id: string): Promise<number | null> {
  try {
    const response = await apiFetch(`/api/flash-sale/deals/${encodeURIComponent(id)}/stock`);
    if (!response.ok) {
      return null;
    }
    const body: { remaining: number } = await response.json();
    return body.remaining;
  } catch {
    return null;
  }
}

/** Returns null when the deal does not exist or is not published. */
export async function getDeal(id: string): Promise<Deal | null> {
  const response = await apiFetch(`/api/catalog/deals/${encodeURIComponent(id)}`);
  // 400 means the id is not a valid UUID, which is just another unknown deal.
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load deal ${id}: HTTP ${response.status}`);
  }
  return response.json();
}
