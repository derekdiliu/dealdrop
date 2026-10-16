import { Suspense } from "react";

import { PurchaseStatus } from "@/components/purchase-status";

export default function PurchasePage({ params }: PageProps<"/purchases/[id]">) {
  return (
    <main className="mx-auto w-full max-w-md flex-1 px-4 py-16">
      <Suspense>
        <Purchase params={params} />
      </Suspense>
    </main>
  );
}

async function Purchase({ params }: Pick<PageProps<"/purchases/[id]">, "params">) {
  const { id } = await params;
  return <PurchaseStatus purchaseId={id} />;
}
