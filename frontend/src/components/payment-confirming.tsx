"use client";

import { useRouter } from "next/navigation";
import { useEffect } from "react";

const REFRESH_EVERY_MS = 2000;

/**
 * Shown after Stripe sends the customer back but before its webhook has marked the order paid.
 * Re-renders the page until the status changes.
 */
export function PaymentConfirming() {
  const router = useRouter();
  useEffect(() => {
    const refresh = setInterval(() => router.refresh(), REFRESH_EVERY_MS);
    return () => clearInterval(refresh);
  }, [router]);
  return <p className="text-sm">Payment received. Confirming your order…</p>;
}
