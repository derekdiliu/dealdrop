import { forwardWithSession } from "@/lib/proxy";

/** Starts or resumes the Stripe payment for an order; returns the client secret for Stripe.js. */
export async function POST(_request: Request, { params }: RouteContext<"/api/orders/[id]/payment">) {
  const { id } = await params;
  return forwardWithSession(`/api/payment/orders/${encodeURIComponent(id)}/payment-intent`, "POST");
}
