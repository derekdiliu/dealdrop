import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { CheckoutForm } from "@/components/checkout-form";
import { getDeal } from "@/lib/deals";
import { formatNzd } from "@/lib/format";
import { requireToken } from "@/lib/session";

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

  return (
    <>
      <Link href={`/deals/${deal.id}`} className="text-sm text-zinc-500 hover:underline">
        ← Back to deal
      </Link>
      <h1 className="mt-4 text-2xl font-semibold tracking-tight">Checkout</h1>
      <p className="mt-1 text-zinc-600 dark:text-zinc-400">
        {deal.title} · {formatNzd(deal.priceCents)} each, incl. GST
      </p>
      <div className="mt-6">
        {deal.state === "LIVE" ? (
          <CheckoutForm dealId={deal.id} limit={deal.perCustomerLimit} />
        ) : (
          <p>This deal isn&apos;t on sale right now.</p>
        )}
      </div>
    </>
  );
}
