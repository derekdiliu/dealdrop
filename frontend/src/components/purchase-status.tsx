"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

type Outcome = "waiting" | "slow" | "sold-out" | "limit" | "not-found" | "signed-out";

const POLL_INTERVAL_MS = 1000;
const GIVE_UP_AFTER_MS = 30_000;

/** Follows a purchase until the order exists, then opens it. */
export function PurchaseStatus({ purchaseId }: { purchaseId: string }) {
  const router = useRouter();
  const [outcome, setOutcome] = useState<Outcome>("waiting");

  useEffect(() => {
    const startedAt = Date.now();
    let timer: ReturnType<typeof setTimeout>;
    let stopped = false;

    async function poll() {
      try {
        const response = await fetch(`/api/purchases/${purchaseId}`, { cache: "no-store" });
        if (response.status === 404) {
          return setOutcome("not-found");
        }
        if (response.status === 401) {
          return setOutcome("signed-out");
        }
        if (response.ok) {
          const purchase: { status: string; reason: string | null } = await response.json();
          if (purchase.status === "CONFIRMED") {
            return router.replace(`/orders/${purchaseId}`);
          }
          if (purchase.status === "REJECTED") {
            return setOutcome(purchase.reason === "SOLD_OUT" ? "sold-out" : "limit");
          }
        }
      } catch {
        // Network hiccup; try again.
      }
      if (Date.now() - startedAt > GIVE_UP_AFTER_MS) {
        return setOutcome("slow");
      }
      if (!stopped) {
        timer = setTimeout(poll, POLL_INTERVAL_MS);
      }
    }

    poll();
    return () => {
      stopped = true;
      clearTimeout(timer);
    };
  }, [purchaseId, router]);

  const messages: Record<Outcome, string> = {
    waiting: "Securing your order…",
    slow: "This is taking longer than usual. Your order will appear in My orders shortly.",
    "sold-out": "Sorry, the last units sold just before your order was placed. You haven't been charged.",
    limit: "You've already reached the limit for this deal.",
    "not-found": "We couldn't find that purchase.",
    "signed-out": "Your session has expired. Sign in again to see your order in My orders.",
  };

  return (
    <div className="space-y-4 text-center">
      {outcome === "waiting" && (
        <div className="mx-auto h-8 w-8 animate-spin rounded-full border-2 border-zinc-300 border-t-zinc-900 dark:border-zinc-700 dark:border-t-zinc-100" />
      )}
      <p className="text-lg">{messages[outcome]}</p>
      {outcome !== "waiting" && (
        <p className="space-x-4 text-sm">
          <Link href="/orders" className="underline">
            My orders
          </Link>
          <Link href="/" className="underline">
            Browse deals
          </Link>
        </p>
      )}
    </div>
  );
}
