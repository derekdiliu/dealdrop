import Image from "next/image";
import Link from "next/link";
import { notFound } from "next/navigation";
import { Suspense } from "react";

import { AdminDealBadge } from "@/components/admin-bits";
import { DealForm } from "@/components/deal-form";
import { DraftActions } from "@/components/draft-actions";
import { type AdminDeal, getAdminDeal } from "@/lib/admin-deals";
import { discountPercent, formatNzDateTime, formatNzd } from "@/lib/format";
import { requireAdminToken } from "@/lib/session";

export default function AdminDealPage({ params }: PageProps<"/admin/deals/[id]">) {
  return (
    <main className="mx-auto w-full max-w-4xl flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading deal…</p>}>
        <AdminDealDetail params={params} />
      </Suspense>
    </main>
  );
}

async function AdminDealDetail({ params }: Pick<PageProps<"/admin/deals/[id]">, "params">) {
  const { id } = await params;
  const deal = await getAdminDeal(await requireAdminToken(`/admin/deals/${id}`), id);
  if (!deal) {
    notFound();
  }

  return (
    <>
      <Link href="/admin" className="text-sm text-zinc-500 hover:underline">
        ← All deals
      </Link>
      <div className="mt-4 flex items-center gap-3">
        <AdminDealBadge deal={deal} />
        <span className="text-sm text-zinc-500">
          {deal.status === "DRAFT" ? "Last saved" : "Published"} {formatNzDateTime(deal.updatedAt)}
        </span>
      </div>
      <h1 className="mt-2 text-2xl font-semibold tracking-tight">{deal.title}</h1>
      {deal.status === "DRAFT" ? (
        <div className="mt-6 grid gap-8 md:grid-cols-[1fr_14rem]">
          <DealForm deal={deal} />
          <aside className="space-y-3 text-sm">
            <p className="text-zinc-600 dark:text-zinc-400">
              Publishing puts the last saved version on the storefront. Published deals can&apos;t be edited or deleted.
            </p>
            <DraftActions dealId={deal.id} />
          </aside>
        </div>
      ) : (
        <PublishedDeal deal={deal} />
      )}
    </>
  );
}

function PublishedDeal({ deal }: { deal: AdminDeal }) {
  return (
    <div className="mt-6 grid gap-8 md:grid-cols-2">
      <div className="relative aspect-[4/3] overflow-hidden rounded-lg bg-zinc-100 dark:bg-zinc-900">
        {deal.imageUrl && <Image src={deal.imageUrl} alt={deal.title} fill sizes="(min-width: 768px) 50vw, 100vw" className="object-cover" />}
      </div>
      <div className="space-y-4 text-sm">
        <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2">
          <dt className="text-zinc-500">Price</dt>
          <dd>
            {formatNzd(deal.priceCents)} (RRP {formatNzd(deal.rrpCents)}, {discountPercent(deal.priceCents, deal.rrpCents)}% off;
            includes {formatNzd(deal.gstCents)} GST)
          </dd>
          <dt className="text-zinc-500">Stock</dt>
          <dd>
            {deal.stock}, limit {deal.perCustomerLimit} per customer
          </dd>
          <dt className="text-zinc-500">Starts</dt>
          <dd>{formatNzDateTime(deal.startsAt)}</dd>
          <dt className="text-zinc-500">Ends</dt>
          <dd>{formatNzDateTime(deal.endsAt)}</dd>
        </dl>
        <p className="whitespace-pre-line text-zinc-700 dark:text-zinc-300">{deal.description}</p>
        <Link href={`/deals/${deal.id}`} className="inline-block font-medium hover:underline">
          View on the storefront →
        </Link>
      </div>
    </div>
  );
}
