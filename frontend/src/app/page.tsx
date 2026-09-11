import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";

import { StateBadge, TimingLine } from "@/components/deal-timing";
import { getDeals } from "@/lib/deals";
import { discountPercent, formatNzd } from "@/lib/format";

export default function Home() {
  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
      <h1 className="text-3xl font-semibold tracking-tight">Today&apos;s deals</h1>
      <p className="mt-1 text-zinc-600 dark:text-zinc-400">
        Limited stock, limited time. All prices in NZD including GST.
      </p>
      <Suspense fallback={<p className="mt-8 text-zinc-500">Loading deals…</p>}>
        <DealGrid />
      </Suspense>
    </main>
  );
}

// Not cached: deal state and availability must be current on every request.
async function DealGrid() {
  let deals;
  try {
    deals = await getDeals();
  } catch {
    return <p className="mt-8">Deals are unavailable right now. Please try again shortly.</p>;
  }

  if (deals.length === 0) {
    return <p className="mt-8">No deals right now. Check back soon.</p>;
  }

  return (
    <ul className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      {deals.map((deal) => (
        <li key={deal.id}>
          <Link
            href={`/deals/${deal.id}`}
            className="block overflow-hidden rounded-lg border border-zinc-200 transition hover:shadow-md dark:border-zinc-800"
          >
            <div className="relative aspect-[4/3] bg-zinc-100 dark:bg-zinc-900">
              <Image
                src={deal.imageUrl}
                alt={deal.title}
                fill
                sizes="(min-width: 1024px) 33vw, (min-width: 640px) 50vw, 100vw"
                className="object-cover"
              />
            </div>
            <div className="space-y-2 p-4">
              <div className="flex items-center justify-between gap-2">
                <StateBadge state={deal.state} />
                <span className="text-sm text-zinc-500">
                  <TimingLine deal={deal} />
                </span>
              </div>
              <h2 className="font-medium">{deal.title}</h2>
              <p className="flex items-baseline gap-2">
                <span className="text-xl font-semibold">{formatNzd(deal.priceCents)}</span>
                <span className="text-sm text-zinc-500 line-through">{formatNzd(deal.rrpCents)}</span>
                <span className="text-sm font-medium text-emerald-700 dark:text-emerald-400">
                  {discountPercent(deal.priceCents, deal.rrpCents)}% off
                </span>
              </p>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  );
}
