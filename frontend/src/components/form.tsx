"use client";

import type { InputHTMLAttributes } from "react";
import { useFormStatus } from "react-dom";

import { buttonClass } from "@/components/styles";

export function Field({ label, ...input }: { label: string } & InputHTMLAttributes<HTMLInputElement>) {
  return (
    <label className="block text-sm">
      <span className="font-medium">{label}</span>
      <input
        {...input}
        className="mt-1 w-full rounded-md border border-zinc-300 bg-transparent px-3 py-2 focus:border-zinc-900 focus:outline-none dark:border-zinc-700 dark:focus:border-zinc-100"
      />
    </label>
  );
}

export function SubmitButton({ children, pendingText }: { children: React.ReactNode; pendingText: string }) {
  const { pending } = useFormStatus();
  return (
    <button type="submit" disabled={pending} className={buttonClass}>
      {pending ? pendingText : children}
    </button>
  );
}

export function FormError({ message }: { message?: string }) {
  return message ? (
    <p role="alert" className="rounded-md bg-red-50 px-3 py-2 text-sm text-red-700 dark:bg-red-950 dark:text-red-300">
      {message}
    </p>
  ) : null;
}

