import Image from "next/image";
import Link from "next/link";
import { Suspense } from "react";

import { AdminDealBadge } from "@/components/admin-bits";
import { secondaryButtonClass } from "@/components/styles";
import { getAdminDeals } from "@/lib/admin-deals";
import { formatNzDateTime, formatNzd } from "@/lib/format";
import { requireAdminToken } from "@/lib/session";

export default function AdminPage({ searchParams }: PageProps<"/admin">) {
  return (
    <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading deals…</p>}>
        <DealTable searchParams={searchParams} />
      </Suspense>
    </main>
  );
}

async function DealTable({ searchParams }: Pick<PageProps<"/admin">, "searchParams">) {
  const token = await requireAdminToken("/admin");
  const { page } = await searchParams;
  // Pages are numbered from 1 in the URL and from 0 by the backend.
  const pageNumber = typeof page === "string" && /^\d+$/.test(page) ? Math.max(Number(page), 1) : 1;
  const { content: deals, page: info } = await getAdminDeals(token, pageNumber - 1);

  return (
    <>
      <div className="flex items-center justify-between gap-4">
        <h1 className="text-2xl font-semibold tracking-tight">Deals</h1>
        <Link href="/admin/deals/new" className={secondaryButtonClass}>
          New deal
        </Link>
      </div>
      {deals.length === 0 ? (
        <p className="mt-6">{info.totalElements === 0 ? "No deals yet." : "No deals on this page."}</p>
      ) : (
        <div className="mt-6 overflow-x-auto rounded-lg border border-zinc-200 dark:border-zinc-800">
          <table className="w-full text-left text-sm">
            <thead className="bg-zinc-50 text-zinc-500 dark:bg-zinc-900">
              <tr>
                <th className="px-4 py-2 font-medium">Deal</th>
                <th className="px-4 py-2 font-medium">Status</th>
                <th className="px-4 py-2 font-medium">Sale (NZ time)</th>
                <th className="px-4 py-2 text-right font-medium">Price</th>
                <th className="px-4 py-2 text-right font-medium">Stock</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-zinc-200 dark:divide-zinc-800">
              {deals.map((deal) => (
                <tr key={deal.id}>
                  <td className="px-4 py-3">
                    <Link href={`/admin/deals/${deal.id}`} className="flex items-center gap-3 font-medium hover:underline">
                      <span className="relative aspect-[4/3] w-16 shrink-0 overflow-hidden rounded bg-zinc-100 dark:bg-zinc-900">
                        {deal.imageUrl && <Image src={deal.imageUrl} alt="" fill sizes="64px" className="object-cover" />}
                      </span>
                      {deal.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">
                    <AdminDealBadge deal={deal} />
                  </td>
                  <td className="px-4 py-3 whitespace-nowrap text-zinc-600 dark:text-zinc-400">
                    {formatNzDateTime(deal.startsAt)} – {formatNzDateTime(deal.endsAt)}
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {formatNzd(deal.priceCents)}
                    <span className="block text-zinc-500 line-through">{formatNzd(deal.rrpCents)}</span>
                  </td>
                  <td className="px-4 py-3 text-right whitespace-nowrap">
                    {deal.stock}
                    <span className="block text-zinc-500">limit {deal.perCustomerLimit}</span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {info.totalPages > 1 && (
        <nav className="mt-4 flex items-center justify-between text-sm">
          {pageNumber > 1 ? (
            <Link href={`/admin?page=${pageNumber - 1}`} className="hover:underline">
              ← Newer
            </Link>
          ) : (
            <span />
          )}
          <span className="text-zinc-500">
            Page {pageNumber} of {info.totalPages}
          </span>
          {pageNumber < info.totalPages ? (
            <Link href={`/admin?page=${pageNumber + 1}`} className="hover:underline">
              Older →
            </Link>
          ) : (
            <span />
          )}
        </nav>
      )}
    </>
  );
}
