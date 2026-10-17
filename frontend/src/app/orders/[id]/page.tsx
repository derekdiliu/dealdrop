import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { cancelOrder } from "@/app/actions/orders";
import { OrderStatusBadge, zoneLabels } from "@/components/order-bits";
import { PaymentCountdown } from "@/components/payment-countdown";
import { formatNzDateTime, formatNzd } from "@/lib/format";
import { getOrder } from "@/lib/orders";
import { requireToken } from "@/lib/session";

export default function OrderPage({ params }: PageProps<"/orders/[id]">) {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
      <Link href="/orders" className="text-sm text-zinc-500 hover:underline">
        ← My orders
      </Link>
      <Suspense fallback={<p className="mt-6 text-zinc-500">Loading order…</p>}>
        <OrderDetail params={params} />
      </Suspense>
    </main>
  );
}

async function OrderDetail({ params }: Pick<PageProps<"/orders/[id]">, "params">) {
  const { id } = await params;
  const order = await getOrder(await requireToken(`/orders/${id}`), id);
  if (!order) {
    notFound();
  }
  const address = order.shippingAddress;
  const subtotal = order.unitPriceCents * order.quantity;

  return (
    <div className="mt-6 grid gap-8 md:grid-cols-[1fr_20rem]">
      <section className="space-y-6">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-semibold tracking-tight">Order</h1>
          <OrderStatusBadge status={order.status} />
        </div>

        {order.status === "PENDING_PAYMENT" && (
          <div className="space-y-4 rounded-lg border border-amber-300 p-4 dark:border-amber-700">
            <PaymentCountdown expiresAt={order.expiresAt} />
            <form action={cancelOrder.bind(null, order.id)}>
              <button type="submit" className="text-sm text-zinc-500 hover:underline">
                Cancel order
              </button>
            </form>
          </div>
        )}
        {order.status === "PAID" && order.paidAt && (
          <p>Paid {formatNzDateTime(order.paidAt)}. Thanks! We&apos;ll let you know when it ships.</p>
        )}
        {order.status === "CANCELLED" && (
          <p>
            {order.cancelReason === "PAYMENT_TIMEOUT"
              ? "This order was cancelled because it wasn't paid in time."
              : "You cancelled this order."}
          </p>
        )}

        <div>
          <h2 className="font-medium">Delivery to</h2>
          <address className="mt-1 not-italic text-zinc-600 dark:text-zinc-400">
            {address.recipientName}
            <br />
            {address.line1}
            {address.line2 && (
              <>
                <br />
                {address.line2}
              </>
            )}
            {address.suburb && (
              <>
                <br />
                {address.suburb}
              </>
            )}
            <br />
            {address.city} {address.postcode}
            <br />
            {address.phone}
          </address>
        </div>
      </section>

      <aside className="h-fit space-y-2 rounded-lg border border-zinc-200 p-4 text-sm dark:border-zinc-800">
        <p className="font-medium">
          <Link href={`/deals/${order.dealId}`} className="hover:underline">
            {order.dealTitle}
          </Link>
        </p>
        <Row label={`${order.quantity} × ${formatNzd(order.unitPriceCents)}`} value={formatNzd(subtotal)} />
        <Row
          label={`Shipping (${zoneLabels[order.shippingZone]}${address.ruralDelivery ? ", rural" : ""})`}
          value={formatNzd(order.shippingCents)}
        />
        <div className="border-t border-zinc-200 pt-2 dark:border-zinc-800">
          <Row label="Total" value={formatNzd(order.totalCents)} strong />
          <Row label="Includes GST" value={formatNzd(order.gstCents)} />
        </div>
        <p className="pt-2 text-zinc-500">Ordered {formatNzDateTime(order.createdAt)}</p>
      </aside>
    </div>
  );
}

function Row({ label, value, strong }: { label: string; value: string; strong?: boolean }) {
  return (
    <p className={`flex justify-between gap-4 ${strong ? "font-semibold" : ""}`}>
      <span>{label}</span>
      <span>{value}</span>
    </p>
  );
}
