import "server-only";

import { apiFetch } from "@/lib/api";

export type OrderStatus = "PENDING_PAYMENT" | "PAID" | "CANCELLED";

export type Order = {
  id: string;
  dealId: string;
  dealTitle: string;
  quantity: number;
  currency: "NZD";
  unitPriceCents: number;
  shippingCents: number;
  totalCents: number;
  gstCents: number;
  status: OrderStatus;
  cancelReason: "CUSTOMER" | "PAYMENT_TIMEOUT" | null;
  shippingAddress: {
    recipientName: string;
    phone: string;
    line1: string;
    line2: string | null;
    suburb: string | null;
    city: string;
    postcode: string;
    ruralDelivery: boolean;
  };
  shippingZone: "NORTH_ISLAND" | "SOUTH_ISLAND";
  createdAt: string;
  expiresAt: string;
  paidAt: string | null;
};

export async function getOrders(token: string): Promise<Order[]> {
  const response = await apiFetch("/api/order/orders", { token });
  if (!response.ok) {
    throw new Error(`Failed to load orders: HTTP ${response.status}`);
  }
  return response.json();
}

/** Null if the order doesn't exist or isn't this customer's. */
export async function getOrder(token: string, id: string): Promise<Order | null> {
  const response = await apiFetch(`/api/order/orders/${encodeURIComponent(id)}`, { token });
  if (response.status === 404 || response.status === 400) {
    return null;
  }
  if (!response.ok) {
    throw new Error(`Failed to load order ${id}: HTTP ${response.status}`);
  }
  return response.json();
}
