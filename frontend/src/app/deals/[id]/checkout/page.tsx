import type { Metadata } from "next";
import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { CheckoutForm } from "@/components/checkout-form";
import { getDeal, getRemainingStock } from "@/lib/deals";
import { formatNzd } from "@/lib/format";
import { requireToken } from "@/lib/session";

export const metadata: Metadata = { title: "Checkout" };

export default function CheckoutPage({ params }: PageProps<"/deals/[id]/checkout">) {
  return (
    <main className="mx-auto w-full max-w-lg flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading…</p>}>
        <Checkout params={params} />
      </Suspense>
    </main>
  );
}

async function Checkout({ params }: Pick<PageProps<"/deals/[id]/checkout">, "params">) {
  const { id } = await params;
  await requireToken(`/deals/${id}/checkout`);
  const deal = await getDeal(id);
  if (!deal) {
    notFound();
  }
  // Same check as the deal page, so a link or bookmark to checkout doesn't offer a sold-out deal.
  const remaining = deal.state === "LIVE" ? await getRemainingStock(deal.id) : null;

  return (
    <>
      <Link href={`/deals/${deal.id}`} className="text-sm text-zinc-500 hover:underline">
        ← Back to deal
      </Link>
      <h1 className="mt-4 text-2xl font-semibold tracking-tight">Checkout</h1>
      <div className="mt-4 flex items-center gap-4 rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
        <div className="relative h-16 w-20 shrink-0 overflow-hidden rounded-md bg-zinc-100 dark:bg-zinc-900">
          <Image src={deal.imageUrl} alt="" fill sizes="80px" className="object-cover" />
        </div>
        <div className="min-w-0">
          <p className="truncate font-medium">{deal.title}</p>
          <p className="text-sm text-zinc-600 dark:text-zinc-400">{formatNzd(deal.priceCents)} each, incl. GST</p>
        </div>
      </div>
      <div className="mt-6">
        {deal.state === "LIVE" && remaining === 0 ? (
          <p>
            Sorry, this deal has sold out.{" "}
            <Link href="/" className="underline">
              Browse other deals
            </Link>
          </p>
        ) : deal.state === "LIVE" ? (
          <CheckoutForm dealId={deal.id} limit={deal.perCustomerLimit} priceCents={deal.priceCents} />
        ) : (
          <p>This deal isn&apos;t on sale right now.</p>
        )}
      </div>
    </>
  );
}
