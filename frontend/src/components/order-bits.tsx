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
