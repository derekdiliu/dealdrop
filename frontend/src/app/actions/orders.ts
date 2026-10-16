"use server";

import { redirect } from "next/navigation";

import type { FormState } from "@/app/actions/auth";
import { apiFetch, problemMessage } from "@/lib/api";
import { submittedValues } from "@/lib/forms";
import { requireToken } from "@/lib/session";

/** Asks flash-sale for the deal. Success means stock is held; the order is created moments later. */
export async function purchase(dealId: string, _state: FormState, form: FormData): Promise<FormState> {
  const token = await requireToken(`/deals/${dealId}/checkout`);
  const text = (name: string) => {
    const value = form.get(name);
    return typeof value === "string" && value.trim() !== "" ? value.trim() : null;
  };
  const response = await apiFetch(`/api/flash-sale/deals/${encodeURIComponent(dealId)}/purchases`, {
    method: "POST",
    token,
    body: JSON.stringify({
      quantity: Number(form.get("quantity")),
      shippingAddress: {
        recipientName: text("recipientName"),
        phone: text("phone"),
        line1: text("line1"),
        line2: text("line2"),
        suburb: text("suburb"),
        city: text("city"),
        postcode: text("postcode"),
        ruralDelivery: form.get("ruralDelivery") === "on",
      },
    }),
  });
  if (response.status === 401) {
    redirect(`/login?next=${encodeURIComponent(`/deals/${dealId}/checkout`)}`);
  }
  if (response.status === 400) {
    return {
      error: "Please check the address: every required field filled in, a 4-digit postcode and a phone number.",
      values: submittedValues(form),
    };
  }
  if (response.status !== 202) {
    return { error: await problemMessage(response), values: submittedValues(form) };
  }
  const { purchaseId } = await response.json();
  redirect(`/purchases/${purchaseId}`);
}

export async function cancelOrder(orderId: string) {
  const token = await requireToken(`/orders/${orderId}`);
  await apiFetch(`/api/order/orders/${encodeURIComponent(orderId)}/cancel`, { method: "POST", token });
  // Whatever the outcome, the order page shows the current state.
  redirect(`/orders/${orderId}`);
}
