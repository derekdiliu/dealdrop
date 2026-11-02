import type { Metadata } from "next";
import Link from "next/link";

import { buttonClass } from "@/components/styles";

export const metadata: Metadata = { title: "Not found" };

export default function NotFound() {
  return (
    <main className="mx-auto w-full max-w-md flex-1 px-4 py-16 text-center">
      <h1 className="text-2xl font-semibold tracking-tight">We couldn&apos;t find that page</h1>
      <p className="mt-2 text-zinc-600 dark:text-zinc-400">
        The link may be wrong, or the deal may no longer be available.
      </p>
      <Link href="/" className={`mt-6 block ${buttonClass}`}>
        See today&apos;s deals
      </Link>
    </main>
  );
}
