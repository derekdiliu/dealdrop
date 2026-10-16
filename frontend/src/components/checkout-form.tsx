"use client";

import { useActionState } from "react";

import { purchase } from "@/app/actions/orders";
import { Field, FormError, SubmitButton } from "@/components/form";

export function CheckoutForm({ dealId, limit }: { dealId: string; limit: number }) {
  const [state, action] = useActionState(purchase.bind(null, dealId), {});
  const typed = state.values ?? {};
  return (
    <form action={action} className="space-y-4">
      <label className="block text-sm">
        <span className="font-medium">Quantity</span>
        <select
          name="quantity"
          defaultValue={typed.quantity ?? 1}
          className="mt-1 w-full rounded-md border border-zinc-300 bg-transparent px-3 py-2 dark:border-zinc-700"
        >
          {Array.from({ length: limit }, (_, i) => i + 1).map((n) => (
            <option key={n} value={n}>
              {n}
            </option>
          ))}
        </select>
      </label>

      <fieldset className="space-y-4">
        <legend className="mb-2 font-medium">Delivery address</legend>
        <Field label="Full name" name="recipientName" autoComplete="name" required maxLength={100} defaultValue={typed.recipientName} />
        <Field label="Phone" name="phone" type="tel" autoComplete="tel" required placeholder="021 123 4567" defaultValue={typed.phone} />
        <Field label="Street address" name="line1" autoComplete="address-line1" required maxLength={200} defaultValue={typed.line1} />
        <Field label="Apartment, unit, etc. (optional)" name="line2" autoComplete="address-line2" maxLength={200} defaultValue={typed.line2} />
        <Field label="Suburb (optional)" name="suburb" autoComplete="address-level3" maxLength={100} defaultValue={typed.suburb} />
        <div className="grid grid-cols-2 gap-4">
          <Field label="City / town" name="city" autoComplete="address-level2" required maxLength={100} defaultValue={typed.city} />
          <Field label="Postcode" name="postcode" autoComplete="postal-code" required inputMode="numeric" pattern="\d{4}" maxLength={4} defaultValue={typed.postcode} />
        </div>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" name="ruralDelivery" className="h-4 w-4" defaultChecked={typed.ruralDelivery === "on"} />
          This is a rural delivery (RD) address
        </label>
      </fieldset>

      <p className="text-sm text-zinc-500">
        Shipping is worked out from your postcode and shown before you pay. Once you place the order,
        your items are held for a short time while you pay.
      </p>
      <FormError message={state.error} />
      <SubmitButton pendingText="Placing order…">Place order</SubmitButton>
    </form>
  );
}
