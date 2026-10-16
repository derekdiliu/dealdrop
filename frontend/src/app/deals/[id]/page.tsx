import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { StateBadge, TimingLine } from "@/components/deal-timing";
import { buttonClass } from "@/components/styles";
import { getDeal, getRemainingStock } from "@/lib/deals";
import { discountPercent, formatNzd } from "@/lib/format";

// The page itself never awaits, so its shell can be prerendered; the deal streams in per request.
export default function DealPage({ params }: PageProps<"/deals/[id]">) {
  return (
    <main className="mx-auto w-full max-w-5xl flex-1 px-4 py-8">
      <Link href="/" className="text-sm text-zinc-500 hover:underline">
        ← All deals
      </Link>
      <Suspense fallback={<p className="mt-8 text-zinc-500">Loading deal…</p>}>
        <DealDetail params={params} />
      </Suspense>
    </main>
  );
}

async function DealDetail({ params }: Pick<PageProps<"/deals/[id]">, "params">) {
  const { id } = await params;
  const deal = await getDeal(id);
  if (!deal) {
    notFound();
  }
  const remaining = deal.state === "LIVE" ? await getRemainingStock(deal.id) : null;

  return (
    <article className="mt-6 grid gap-8 md:grid-cols-2">
      <div className="relative aspect-[4/3] overflow-hidden rounded-lg bg-zinc-100 dark:bg-zinc-900">
        <Image
          src={deal.imageUrl}
          alt={deal.title}
          fill
          priority
          sizes="(min-width: 768px) 50vw, 100vw"
          className="object-cover"
        />
      </div>
      <div className="space-y-4">
        <div className="flex items-center gap-3">
          <StateBadge state={deal.state} />
          <span className="text-sm text-zinc-500">
            <TimingLine deal={deal} />
          </span>
        </div>
        <h1 className="text-3xl font-semibold tracking-tight">{deal.title}</h1>
        <div>
          <p className="flex items-baseline gap-3">
            <span className="text-3xl font-semibold">{formatNzd(deal.priceCents)}</span>
            <span className="text-zinc-500 line-through">{formatNzd(deal.rrpCents)}</span>
            <span className="font-medium text-emerald-700 dark:text-emerald-400">
              {discountPercent(deal.priceCents, deal.rrpCents)}% off
            </span>
          </p>
          <p className="mt-1 text-sm text-zinc-500">
            Includes {formatNzd(deal.gstCents)} GST
          </p>
        </div>
        <p className="text-sm">
          {remaining === null
            ? `Only ${deal.stock} available`
            : remaining === 0
              ? "Sold out"
              : `${remaining} of ${deal.stock} left`}
          {" · "}Limit {deal.perCustomerLimit} per customer
        </p>
        {deal.state === "LIVE" && remaining !== 0 && (
          // Checkout asks the customer to sign in first if needed.
          <Link href={`/deals/${deal.id}/checkout`} className={`block text-center ${buttonClass}`}>
            Buy now
          </Link>
        )}
        <p className="whitespace-pre-line leading-relaxed text-zinc-700 dark:text-zinc-300">
          {deal.description}
        </p>
      </div>
    </article>
  );
}
