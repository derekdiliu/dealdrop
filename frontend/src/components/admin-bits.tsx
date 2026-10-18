import { StateBadge } from "@/components/deal-timing";
import type { AdminDeal } from "@/lib/admin-deals";

/** Drafts aren't on sale whatever their dates, so only published deals show where they are in the sale. */
export function AdminDealBadge({ deal }: { deal: Pick<AdminDeal, "status" | "state"> }) {
  if (deal.status === "PUBLISHED") {
    return <StateBadge state={deal.state} />;
  }
  return (
    <span className="rounded-full px-2.5 py-0.5 text-xs font-semibold text-zinc-700 ring-1 ring-zinc-300 ring-inset dark:text-zinc-300 dark:ring-zinc-700">
      Draft
    </span>
  );
}
