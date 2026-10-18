import Link from "next/link";

import { formatNzDateTime, formatNzd } from "@/lib/format";
import type { Order } from "@/lib/orders";

const statusStyles: Record<Order["status"], string> = {
  PENDING_PAYMENT: "bg-amber-400 text-zinc-900",
  PAID: "bg-emerald-600 text-white",
  CANCELLED: "bg-zinc-300 text-zinc-700",
};

const statusLabels: Record<Order["status"], string> = {
  PENDING_PAYMENT: "Awaiting payment",
  PAID: "Paid",
  CANCELLED: "Cancelled",
};

export function OrderStatusBadge({ status }: { status: Order["status"] }) {
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${statusStyles[status]}`}>
      {statusLabels[status]}
    </span>
  );
}

export const zoneLabels: Record<Order["shippingZone"], string> = {
  NORTH_ISLAND: "North Island",
  SOUTH_ISLAND: "South Island",
};

export function DeliveryAddress({ address }: { address: Order["shippingAddress"] }) {
  return (
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
  );
}

export function OrderSummary({ order, dealHref }: { order: Order; dealHref: string }) {
  return (
    <aside className="h-fit space-y-2 rounded-lg border border-zinc-200 p-4 text-sm dark:border-zinc-800">
      <p className="font-medium">
        <Link href={dealHref} className="hover:underline">
          {order.dealTitle}
        </Link>
      </p>
      <Row label={`${order.quantity} × ${formatNzd(order.unitPriceCents)}`} value={formatNzd(order.unitPriceCents * order.quantity)} />
      <Row
        label={`Shipping (${zoneLabels[order.shippingZone]}${order.shippingAddress.ruralDelivery ? ", rural" : ""})`}
        value={formatNzd(order.shippingCents)}
      />
      <div className="border-t border-zinc-200 pt-2 dark:border-zinc-800">
        <Row label="Total" value={formatNzd(order.totalCents)} strong />
        <Row label="Includes GST" value={formatNzd(order.gstCents)} />
      </div>
      <p className="pt-2 text-zinc-500">Ordered {formatNzDateTime(order.createdAt)}</p>
    </aside>
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
