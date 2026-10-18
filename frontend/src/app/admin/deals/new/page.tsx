import Link from "next/link";
import { Suspense } from "react";

import { DealForm } from "@/components/deal-form";
import { requireAdminToken } from "@/lib/session";

export default function NewDealPage() {
  return (
    <main className="mx-auto w-full max-w-2xl flex-1 px-4 py-8">
      <Suspense fallback={<p className="text-zinc-500">Loading…</p>}>
        <NewDeal />
      </Suspense>
    </main>
  );
}

async function NewDeal() {
  await requireAdminToken("/admin/deals/new");
  return (
    <>
      <Link href="/admin" className="text-sm text-zinc-500 hover:underline">
        ← All deals
      </Link>
      <h1 className="mt-4 text-2xl font-semibold tracking-tight">New deal</h1>
      <p className="mt-1 text-zinc-600 dark:text-zinc-400">
        Saved as a draft. Customers won&apos;t see it until you publish it.
      </p>
      <div className="mt-6">
        <DealForm />
      </div>
    </>
  );
}
