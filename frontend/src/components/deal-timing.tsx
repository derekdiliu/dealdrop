import type { Deal } from "@/lib/deals";
import { formatNzDateTime } from "@/lib/format";

const badgeStyles: Record<Deal["state"], string> = {
  LIVE: "bg-emerald-600 text-white",
  UPCOMING: "bg-amber-400 text-zinc-900",
  ENDED: "bg-zinc-300 text-zinc-700",
};

const badgeLabels: Record<Deal["state"], string> = {
  LIVE: "Live now",
  UPCOMING: "Coming soon",
  ENDED: "Ended",
};

export function StateBadge({ state }: { state: Deal["state"] }) {
  return (
    <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${badgeStyles[state]}`}>
      {badgeLabels[state]}
    </span>
  );
}

export function TimingLine({ deal }: { deal: Deal }) {
  switch (deal.state) {
    case "UPCOMING":
      return <>Starts {formatNzDateTime(deal.startsAt)}</>;
    case "LIVE":
      return <>Ends {formatNzDateTime(deal.endsAt)}</>;
    case "ENDED":
      return <>Ended {formatNzDateTime(deal.endsAt)}</>;
  }
}
