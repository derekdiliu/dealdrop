import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { cancelOrder } from "@/app/actions/orders";
import { DeliveryAddress, OrderStatusBadge, OrderSummary } from "@/components/order-bits";
import { PaymentConfirming } from "@/components/payment-confirming";
import { PaymentCountdown } from "@/components/payment-countdown";
import { PaymentPanel } from "@/components/payment-panel";
import { formatNzDateTime } from "@/lib/format";
import { getOrder, type Order } from "@/lib/orders";
import { requireToken } from "@/lib/session";

export default function OrderPage({ params, searchParams }: PageProps<"/orders/[id]">) {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
      <Link href="/orders" className="text-sm text-zinc-500 hover:underline">
        ← My orders
      </Link>
      <Suspense fallback={<p className="mt-6 text-zinc-500">Loading order…</p>}>
        <OrderDetail params={params} searchParams={searchParams} />
      </Suspense>
    </main>
  );
}

async function OrderDetail({ params, searchParams }: Pick<PageProps<"/orders/[id]">, "params" | "searchParams">) {
  const { id } = await params;
  const order = await getOrder(await requireToken(`/orders/${id}`), id);
  if (!order) {
    notFound();
  }
  // Stripe appends redirect_status when it sends the customer back after paying.
  const returnedFromStripe = (await searchParams).redirect_status === "succeeded";

  return (
    <div className="mt-6 grid gap-8 md:grid-cols-[1fr_20rem]">
      <section className="space-y-6">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Order</h1>
          <OrderStatusBadge status={order.status} />
        </div>

        {order.status === "PENDING_PAYMENT" && (
          <div className="space-y-4 rounded-lg border border-amber-300 p-4 dark:border-amber-700">
            {returnedFromStripe ? (
              <PaymentConfirming />
            ) : (
              <>
                <PaymentCountdown expiresAt={order.expiresAt} />
                <PaymentPanel orderId={order.id} />
                <form action={cancelOrder.bind(null, order.id)}>
                  <button type="submit" className="text-sm text-zinc-500 hover:underline">
                    Cancel order
                  </button>
                </form>
              </>
            )}
          </div>
        )}
        {order.status === "PAID" && order.paidAt && (
          <p>Paid {formatNzDateTime(order.paidAt)}. Thanks! We&apos;ll let you know when it ships.</p>
        )}
        {order.status === "CANCELLED" && (
          <p>{cancelMessages[order.cancelReason ?? "CUSTOMER"]}</p>
        )}

        <div>
          <h2 className="font-medium">Delivery to</h2>
          <DeliveryAddress address={order.shippingAddress} />
        </div>
      </section>

      <OrderSummary order={order} dealHref={`/deals/${order.dealId}`} />
    </div>
  );
}

const cancelMessages: Record<NonNullable<Order["cancelReason"]>, string> = {
  CUSTOMER: "You cancelled this order.",
  PAYMENT_TIMEOUT: "This order was cancelled because it wasn't paid in time.",
  DEAL_WITHDRAWN: "Sorry, this deal was withdrawn, so we cancelled your order. If you paid, it's been refunded in full.",
};
