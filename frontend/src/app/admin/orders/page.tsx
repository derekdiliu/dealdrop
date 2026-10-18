import Link from "next/link";
import { Suspense } from "react";

import { AdminTabs } from "@/components/admin-bits";
import { OrderStatusBadge } from "@/components/order-bits";
import { getAdminOrders } from "@/lib/admin-orders";
import { formatNzDateTime, formatNzd } from "@/lib/format";
import type { OrderStatus } from "@/lib/orders";
import { requireAdminToken } from "@/lib/session";

const statusFilters: { status?: OrderStatus; label: string }[] = [
  { label: "All" },
  { status: "PENDING_PAYMENT", label: "Awaiting payment" },
  { status: "PAID", label: "Paid" },
  { status: "CANCELLED", label: "Cancelled" },
];

type Filters = { status?: OrderStatus; dealId?: string };

function ordersHref({ status, dealId }: Filters, page = 1): string {
  const query = new URLSearchParams();
  if (status) {
    query.set("status", status);
  }
  if (dealId) {
    query.set("dealId", dealId);
  }
  if (page > 1) {
    query.set("page", String(page));
  }
  const search = query.toString();
  return search ? `/admin/orders?${search}` : "/admin/orders";
}

export default function AdminOrdersPage({ searchParams }: PageProps<"/admin/orders">) {
  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading orders…</p>}>
        <OrderTable searchParams={searchParams} />
      </Suspense>
    </main>
  );
}

async function OrderTable({ searchParams }: Pick<PageProps<"/admin/orders">, "searchParams">) {
  const token = await requireAdminToken("/admin/orders");
  const params = await searchParams;
  // Unknown values are ignored rather than passed to the backend, which would reject them.
  const filters: Filters = {
    status: statusFilters.find((f) => f.status && f.status === params.status)?.status,
    dealId: typeof params.dealId === "string" && /^[0-9a-f-]{36}$/i.test(params.dealId) ? params.dealId : undefined,
  };
  const pageNumber = typeof params.page === "string" && /^\d+$/.test(params.page) ? Math.max(Number(params.page), 1) : 1;
  const { content: orders, page: info } = await getAdminOrders(token, { ...filters, page: pageNumber - 1 });

  return (
    <>
      <AdminTabs current="orders" />
      <h1 className="text-2xl font-semibold tracking-tight">Orders</h1>
      {filters.dealId && (
        <p className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">
          For {orders[0]?.dealTitle ?? "one deal"} ·{" "}
          <Link href={ordersHref({ status: filters.status })} className="underline">
            Show all deals
          </Link>
        </p>
      )}
      <nav className="mt-4 flex flex-wrap gap-2 text-sm">
        {statusFilters.map(({ status, label }) => (
          <Link
            key={label}
            href={ordersHref({ status, dealId: filters.dealId })}
            aria-current={status === filters.status ? "page" : undefined}
            className={`rounded-full px-3 py-1 ring-1 ring-inset ${status === filters.status ? "bg-zinc-900 text-white ring-zinc-900 dark:bg-zinc-100 dark:text-zinc-900 dark:ring-zinc-100" : "ring-zinc-300 hover:bg-zinc-100 dark:ring-zinc-700 dark:hover:bg-zinc-900"}`}
          >
            {label}
          </Link>
        ))}
      </nav>
      {orders.length === 0 ? (
        <p className="mt-6">{info.totalElements === 0 ? "No orders." : "No orders on this page."}</p>
      ) : (
        <div className="mt-4 overflow-x-auto rounded-lg border border-zinc-200 dark:border-zinc-800">
          <table className="w-full text-left text-sm">
            <thead className="bg-zinc-50 text-zinc-500 dark:bg-zinc-900">
              <tr>
                <th className="px-4 py-2 font-medium">Placed (NZ time)</th>
                <th className="px-4 py-2 font-medium">Deal</th>
                <th className="px-4 py-2 font-medium">Deliver to</th>
                <th className="px-4 py-2 text-right font-medium">Total</th>
                <th className="px-4 py-2 font-medium">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {orders.map((order) => (
                <tr key={order.id}>
                  <td className="px-4 py-3 whitespace-nowrap">
                    <Link href={`/admin/orders/${order.id}`} className="font-medium hover:underline">
                      {formatNzDateTime(order.createdAt)}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    {order.dealTitle} × {order.quantity}
                  </td>
                  <td className="px-4 py-3">
                    {order.shippingAddress.recipientName}
                    <span className="block text-zinc-500">{order.shippingAddress.city}</span>
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">{formatNzd(order.totalCents)}</td>
                  <td className="px-4 py-3">
                    <OrderStatusBadge status={order.status} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {info.totalPages > 1 && (
        <nav className="mt-4 flex items-center justify-between text-sm">
          {pageNumber > 1 ? (
            <Link href={ordersHref(filters, pageNumber - 1)} className="hover:underline">
              ← Newer
            </Link>
          ) : (
            <span />
          )}
          <span className="text-zinc-500">
            Page {pageNumber} of {info.totalPages}
          </span>
          {pageNumber < info.totalPages ? (
            <Link href={ordersHref(filters, pageNumber + 1)} className="hover:underline">
              Older →
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}
    </>
  );
}
