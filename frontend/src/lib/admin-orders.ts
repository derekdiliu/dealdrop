import "server-only";

import { type AdminDealPage, signInAgainIfExpired } from "@/lib/admin-deals";
import { apiFetch } from "@/lib/api";
import type { Order, OrderStatus } from "@/lib/orders";

// Server-side access to the order and payment back-office APIs through the gateway.

export type AdminOrderPage = { content: Order[]; page: AdminDealPage["page"] };

export type AdminPayment = {
  orderId: string;
  paymentIntentId: string;
  amountCents: number;
  currency: string;
  status: "PENDING" | "SUCCEEDED" | "CANCELLED" | "REFUNDED";
  createdAt: string;
  updatedAt: string;
};

/** `page` is zero-based, as the backend counts. */
export async function getAdminOrders(
  token: string,
  { page, status, dealId }: { page: number; status?: OrderStatus; dealId?: string },
): Promise<AdminOrderPage> {
  const query = new URLSearchParams({ page: String(page) });
  if (status) {
    query.set("status", status);
  }
  if (dealId) {
    query.set("dealId", dealId);
  }
  const response = await apiFetch(`/api/order/admin/orders?${query}`, { token });
  signInAgainIfExpired(response, "/admin/orders");
  if (!response.ok) {
    throw new Error(`Failed to load orders: HTTP ${response.status}`);
  }
  return response.json();
}

/** Null if the order doesn't exist. */
export async function getAdminOrder(token: string, id: string): Promise<Order | null> {
  const response = await apiFetch(`/api/order/admin/orders/${encodeURIComponent(id)}`, { token });
  signInAgainIfExpired(response, `/admin/orders/${id}`);
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load order ${id}: HTTP ${response.status}`);
  }
  return response.json();
}

/** Null until the customer opens the payment page, which is when the payment is created. */
export async function getAdminPayment(token: string, orderId: string): Promise<AdminPayment | null> {
  const response = await apiFetch(`/api/payment/admin/payments/${encodeURIComponent(orderId)}`, { token });
  signInAgainIfExpired(response, `/admin/orders/${orderId}`);
  if (response.status === 404) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load the payment for order ${orderId}: HTTP ${response.status}`);
  }
  return response.json();
}
