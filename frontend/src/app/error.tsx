"use client";

import Link from "next/link";
import { useEffect } from "react";

import { buttonClass } from "@/components/styles";

/** Shown when a page fails to load, usually because a backend service is down. The header stays usable. */
export default function Error({ error, retry }: { error: unknown; retry: () => void }) {
  useEffect(() => {
    console.error(error);
  }, [error]);

  return (
    <main className="mx-auto w-full max-w-md flex-1 px-4 py-16 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">Something went wrong</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">
        We couldn&apos;t load this page. Please try again in a moment.
      </p>
      <div className="mt-6 space-y-4">
        <button type="button" onClick={() => retry()} className={buttonClass}>
          Try again
        </button>
        <Link href="/" className="inline-block text-sm text-zinc-500 hover:underline">
          Back to deals
        </Link>
      </div>
    </main>
  );
}
