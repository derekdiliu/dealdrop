import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { DeliveryAddress, OrderStatusBadge, OrderSummary } from "@/components/order-bits";
import { type AdminPayment, getAdminOrder, getAdminPayment } from "@/lib/admin-orders";
import { formatNzDateTime, formatNzd } from "@/lib/format";
import type { Order } from "@/lib/orders";
import { requireAdminToken } from "@/lib/session";

export default function AdminOrderPage({ params }: PageProps<"/admin/orders/[id]">) {
  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading order…</p>}>
        <AdminOrderDetail params={params} />
      </Suspense>
    </main>
  );
}

async function AdminOrderDetail({ params }: Pick<PageProps<"/admin/orders/[id]">, "params">) {
  const { id } = await params;
  const token = await requireAdminToken(`/admin/orders/${id}`);
  const [order, payment] = await Promise.all([getAdminOrder(token, id), getAdminPayment(token, id)]);
  if (!order) {
    notFound();
  }

  return (
    <>
      <Link href="/admin/orders" className="text-sm text-zinc-500 hover:underline">
        ← All orders
      </Link>
      <div className="mt-6 grid gap-8 md:grid-cols-[1fr_20rem]">
        <section className="space-y-6">
          <div className="flex items-center gap-3">
            <h1 className="text-2xl font-semibold tracking-tight">Order</h1>
            <OrderStatusBadge status={order.status} />
          </div>
          <p>
            <OrderOutcome order={order} />
          </p>
          <div>
            <h2 className="font-medium">Delivery to</h2>
            <DeliveryAddress address={order.shippingAddress} />
          </div>
          <div>
            <h2 className="font-medium">Payment</h2>
            <PaymentDetails payment={payment} />
          </div>
          <p className="font-mono text-xs text-zinc-500">Order {order.id}</p>
        </section>
        <OrderSummary order={order} dealHref={`/admin/deals/${order.dealId}`} />
      </div>
    </>
  );
}

function OrderOutcome({ order }: { order: Order }) {
  switch (order.status) {
    case "PENDING_PAYMENT":
      return <>Stock is held until {formatNzDateTime(order.expiresAt)}; the order is cancelled if it isn&apos;t paid by then.</>;
    case "PAID":
      return <>Paid {order.paidAt && formatNzDateTime(order.paidAt)}.</>;
    case "CANCELLED":
      switch (order.cancelReason) {
        case "PAYMENT_TIMEOUT":
          return <>Cancelled because it wasn&apos;t paid in time. The stock went back on sale.</>;
        case "DEAL_WITHDRAWN":
          return <>Cancelled because the deal was withdrawn. Any payment is refunded in full.</>;
        default:
          return <>Cancelled by the customer. The stock went back on sale.</>;
      }
  }
}

const paymentLabels: Record<AdminPayment["status"], string> = {
  PENDING: "Started, not paid",
  SUCCEEDED: "Paid",
  CANCELLED: "Cancelled before payment",
  REFUNDED: "Refunded",
};

function PaymentDetails({ payment }: { payment: AdminPayment | null }) {
  if (!payment) {
    return <p className="mt-1 text-zinc-600 dark:text-zinc-400">Not started: the customer hasn&apos;t opened the payment page.</p>;
  }
  return (
    <div className="mt-1 space-y-1 text-zinc-600 dark:text-zinc-400">
      <p>
        <span className="font-medium text-zinc-900 dark:text-zinc-100">{paymentLabels[payment.status]}</span>
        {" · "}
        {formatNzd(payment.amountCents)} · updated {formatNzDateTime(payment.updatedAt)}
      </p>
      {payment.status === "REFUNDED" && (
        <p>The payment arrived after the order could no longer accept it, so it was refunded automatically.</p>
      )}
      <p className="font-mono text-xs">Stripe {payment.paymentIntentId}</p>
    </div>
  );
}
