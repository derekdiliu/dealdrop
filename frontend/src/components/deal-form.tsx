"use client";

import Image from "next/image";
import { useActionState, useState } from "react";

import { requestImageUpload, saveDeal } from "@/app/actions/admin";
import { Field, FormError, SubmitButton, TextArea } from "@/components/form";
import { secondaryButtonClass } from "@/components/styles";
import type { AdminDeal } from "@/lib/admin-deals";
import { toNzInputValue } from "@/lib/format";

/** Creates a draft, or edits the given one. */
export function DealForm({ deal }: { deal?: AdminDeal }) {
  const [state, action] = useActionState(saveDeal.bind(null, deal?.id ?? null), {});
  const typed = state.values ?? {};
  const dollars = (cents: number | undefined) => (cents === undefined ? undefined : (cents / 100).toFixed(2));
  return (
    <form action={action} className="space-y-4">
      <Field label="Title" name="title" required maxLength={120} defaultValue={typed.title ?? deal?.title} />
      <TextArea label="Description" name="description" required maxLength={4000} rows={6} defaultValue={typed.description ?? deal?.description} />
      <ImageField savedKey={deal?.imageKey ?? null} savedUrl={deal?.imageUrl ?? null} />
      <div className="grid gap-4 sm:grid-cols-2">
        <Field label="RRP, NZD incl. GST" name="rrp" type="number" min="0.01" step="0.01" inputMode="decimal" required defaultValue={typed.rrp ?? dollars(deal?.rrpCents)} />
        <Field label="Deal price, NZD incl. GST" name="price" type="number" min="0.01" step="0.01" inputMode="decimal" required defaultValue={typed.price ?? dollars(deal?.priceCents)} />
        <Field label="Stock" name="stock" type="number" min={1} max={100000} step={1} required defaultValue={typed.stock ?? deal?.stock} />
        <Field label="Limit per customer" name="perCustomerLimit" type="number" min={1} step={1} required defaultValue={typed.perCustomerLimit ?? deal?.perCustomerLimit} />
        <Field label="Starts (NZ time)" name="startsAt" type="datetime-local" required defaultValue={typed.startsAt ?? (deal && toNzInputValue(deal.startsAt))} />
        <Field label="Ends (NZ time)" name="endsAt" type="datetime-local" required defaultValue={typed.endsAt ?? (deal && toNzInputValue(deal.endsAt))} />
      </div>
      <FormError message={state.error} />
      <SubmitButton pendingText="Saving…">{deal ? "Save changes" : "Create draft"}</SubmitButton>
    </form>
  );
}

const IMAGE_TYPES = ["image/jpeg", "image/png", "image/webp"];
const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

/**
 * Uploads straight from the browser to storage with a presigned URL, then submits only the key.
 * The bucket must allow PUT from this site's origin (CORS).
 */
function ImageField({ savedKey, savedUrl }: { savedKey: string | null; savedUrl: string | null }) {
  const [image, setImage] = useState({ key: savedKey, preview: savedUrl });
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string>();

  async function upload(file: File) {
    if (!IMAGE_TYPES.includes(file.type)) {
      setError("Choose a JPEG, PNG or WebP image.");
      return;
    }
    if (file.size > MAX_IMAGE_BYTES) {
      setError("Images must be at most 5 MB.");
      return;
    }
    setError(undefined);
    setUploading(true);
    try {
      const target = await requestImageUpload(file.type, file.size);
      if ("error" in target) {
        setError(target.error);
        return;
      }
      const response = await fetch(target.uploadUrl, { method: "PUT", headers: target.headers, body: file });
      if (!response.ok) {
        setError("The upload failed. Please try again.");
        return;
      }
      setImage({ key: target.key, preview: URL.createObjectURL(file) });
    } catch {
      setError("The upload failed. Check your connection and try again.");
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="space-y-2 text-sm">
      <span className="font-medium">Image</span>
      <input type="hidden" name="imageKey" value={image.key ?? ""} />
      <div className="flex items-center gap-4">
        <div className="relative aspect-[4/3] w-40 overflow-hidden rounded-md bg-zinc-100 dark:bg-zinc-900">
          {image.preview ? (
            <Image src={image.preview} alt="Deal image" fill unoptimized className="object-cover" />
          ) : (
            <span className="flex h-full items-center justify-center text-zinc-500">No image</span>
          )}
        </div>
        <label className={`cursor-pointer ${secondaryButtonClass}`}>
          {uploading ? "Uploading…" : image.key ? "Replace image" : "Upload image"}
          <input
            type="file"
            accept={IMAGE_TYPES.join(",")}
            className="sr-only"
            disabled={uploading}
            onChange={(event) => {
              const file = event.target.files?.[0];
              // Cleared so choosing the same file again still triggers an upload.
              event.target.value = "";
              if (file) {
                upload(file);
              }
            }}
          />
        </label>
      </div>
      <p className="text-zinc-500">JPEG, PNG or WebP, up to 5 MB. Required before publishing.</p>
      <FormError message={error} />
    </div>
  );
}
