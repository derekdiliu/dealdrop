import Link from "next/link";

import { StateBadge } from "@/components/deal-timing";
import type { AdminDeal } from "@/lib/admin-deals";

/** Drafts and withdrawn deals aren't on sale whatever their dates, so only published deals show where they are in the sale. */
export function AdminDealBadge({ deal }: { deal: Pick<AdminDeal, "status" | "state"> }) {
  if (deal.status === "PUBLISHED") {
    return <StateBadge state={deal.state} />;
  }
  if (deal.status === "WITHDRAWN") {
    return <span className="rounded-full bg-red-600 px-2.5 py-0.5 text-xs font-semibold text-white">Withdrawn</span>;
  }
  return (
    <span className="rounded-full px-2.5 py-0.5 text-xs font-semibold text-zinc-700 ring-1 ring-zinc-300 ring-inset dark:text-zinc-300 dark:ring-zinc-700">
      Draft
    </span>
  );
}

/** Switches between the back office's two lists. Rendered only after the admin check passes. */
export function AdminTabs({ current }: { current: "deals" | "orders" }) {
  const tab = (name: typeof current, href: string, label: string) => (
    <Link
      href={href}
      aria-current={current === name ? "page" : undefined}
      className={`border-b-2 pb-2 ${current === name ? "border-zinc-900 font-medium dark:border-zinc-100" : "border-transparent text-zinc-500 hover:text-zinc-900 dark:hover:text-zinc-100"}`}
    >
      {label}
    </Link>
  );
  return (
    <nav className="mb-6 flex gap-6 border-b border-zinc-200 text-sm dark:border-zinc-800">
      {tab("deals", "/admin", "Deals")}
      {tab("orders", "/admin/orders", "Orders")}
    </nav>
  );
}
