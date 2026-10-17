import Link from "next/link";
import { Suspense } from "react";

import { OrderStatusBadge } from "@/components/order-bits";
import { formatNzDateTime, formatNzd } from "@/lib/format";
import { getOrders } from "@/lib/orders";
import { requireToken } from "@/lib/session";

export default function OrdersPage() {
  return (
    <main className="mx-auto w-full max-w-3xl flex-1 px-4 py-8">
      <h1 className="text-2xl font-semibold tracking-tight">My orders</h1>
      <Suspense fallback={<p className="mt-6 text-zinc-500">Loading orders…</p>}>
        <OrderList />
      </Suspense>
    </main>
  );
}

async function OrderList() {
  const orders = await getOrders(await requireToken("/orders"));
  if (orders.length === 0) {
    return (
      <p className="mt-6">
        No orders yet.{" "}
        <Link href="/" className="underline">
          See today&apos;s deals
        </Link>
      </p>
    );
  }
  return (
    <ul className="mt-6 divide-y divide-zinc-200 rounded-lg border border-zinc-200 dark:divide-zinc-800 dark:border-zinc-800">
      {orders.map((order) => (
        <li key={order.id}>
          <Link href={`/orders/${order.id}`} className="flex items-center justify-between gap-4 p-4 hover:bg-zinc-50 dark:hover:bg-zinc-900">
            <div>
              <p className="font-medium">
                {order.dealTitle} × {order.quantity}
              </p>
              <p className="text-sm text-zinc-500">{formatNzDateTime(order.createdAt)}</p>
            </div>
            <div className="flex items-center gap-3">
              <span className="font-medium">{formatNzd(order.totalCents)}</span>
              <OrderStatusBadge status={order.status} />
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
