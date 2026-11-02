"use client";

import { useState } from "react";
import { useFormStatus } from "react-dom";

import { cancelOrder } from "@/app/actions/orders";
import { dangerButtonClass, secondaryButtonClass } from "@/components/styles";

/** Asks before cancelling, since the held items go back on sale and may sell out. */
export function CancelOrder({ orderId }: { orderId: string }) {
  const [confirming, setConfirming] = useState(false);
  if (!confirming) {
    return (
      <button type="button" onClick={() => setConfirming(true)} className="text-sm text-zinc-500 hover:underline">
        Cancel order
      </button>
    );
  }
  return (
    <form action={cancelOrder.bind(null, orderId)} className="space-y-3">
      <p className="text-sm">Cancel this order? Your items go back on sale and may sell out.</p>
      <div className="flex flex-wrap gap-2">
        <CancelButton />
        <button type="button" onClick={() => setConfirming(false)} className={secondaryButtonClass}>
          Keep order
        </button>
      </div>
    </form>
  );
}

function CancelButton() {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} className={dangerButtonClass}>
      {pending ? "Cancelling…" : "Cancel order"}
    </button>
  );
}
