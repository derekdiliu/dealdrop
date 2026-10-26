"use client";

import { useActionState, useState } from "react";
import { useFormStatus } from "react-dom";

import { withdrawDeal } from "@/app/actions/admin";
import { FormError } from "@/components/form";
import { dangerButtonClass, secondaryButtonClass } from "@/components/styles";

export function WithdrawAction({ dealId }: { dealId: string }) {
  const [withdrawn, withdraw] = useActionState(withdrawDeal.bind(null, dealId), {});
  const [confirming, setConfirming] = useState(false);
  return (
    <div className="space-y-3 rounded-lg border border-red-200 p-4 dark:border-red-900">
      {confirming ? (
        <form action={withdraw} className="space-y-3">
          <p>
            Take this deal off sale for good? Every order for it is cancelled, and paid orders are refunded in full. This
            can&apos;t be undone.
          </p>
          <div className="flex flex-wrap gap-2">
            <WithdrawButton />
            <button type="button" onClick={() => setConfirming(false)} className={secondaryButtonClass}>
              Keep selling
            </button>
          </div>
        </form>
      ) : (
        <>
          <p className="text-zinc-600 dark:text-zinc-400">Wrong price or a problem with the product? Stop the sale now.</p>
          <button type="button" onClick={() => setConfirming(true)} className={dangerButtonClass}>
            Withdraw deal
          </button>
        </>
      )}
      <FormError message={withdrawn.error} />
    </div>
  );
}

function WithdrawButton() {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} className={dangerButtonClass}>
      {pending ? "Withdrawing…" : "Withdraw and refund"}
    </button>
  );
}
