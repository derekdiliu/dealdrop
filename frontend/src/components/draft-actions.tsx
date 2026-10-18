"use client";

import { useActionState, useState } from "react";
import { useFormStatus } from "react-dom";

import { deleteDeal, publishDeal } from "@/app/actions/admin";
import { FormError, SubmitButton } from "@/components/form";
import { dangerButtonClass, secondaryButtonClass } from "@/components/styles";

export function DraftActions({ dealId }: { dealId: string }) {
  const [published, publish] = useActionState(publishDeal.bind(null, dealId), {});
  const [deleted, remove] = useActionState(deleteDeal.bind(null, dealId), {});
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  return (
    <div className="space-y-3">
      <form action={publish}>
        <SubmitButton pendingText="Publishing…">Publish</SubmitButton>
      </form>
      <FormError message={published.error} />
      {confirmingDelete ? (
        <form action={remove} className="flex flex-wrap items-center gap-2">
          <span>Delete this draft?</span>
          <DeleteButton />
          <button type="button" onClick={() => setConfirmingDelete(false)} className={secondaryButtonClass}>
            Keep
          </button>
        </form>
      ) : (
        <button type="button" onClick={() => setConfirmingDelete(true)} className="text-red-700 hover:underline dark:text-red-400">
          Delete draft
        </button>
      )}
      <FormError message={deleted.error} />
    </div>
  );
}

function DeleteButton() {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} className={dangerButtonClass}>
      {pending ? "Deleting…" : "Delete"}
    </button>
  );
}
