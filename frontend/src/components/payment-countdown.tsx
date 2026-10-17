"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

/** Time left to pay. When it runs out, reloads so the cancelled order shows. */
export function PaymentCountdown({ expiresAt }: { expiresAt: string }) {
  const router = useRouter();
  const deadline = new Date(expiresAt).getTime();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, []);

  const secondsLeft = Math.max(0, Math.ceil((deadline - now) / 1000));
  useEffect(() => {
    if (secondsLeft === 0) {
      // The server cancels the order a moment after the deadline.
      const refresh = setTimeout(() => router.refresh(), 3000);
      return () => clearTimeout(refresh);
    }
  }, [secondsLeft, router]);

  const minutes = Math.floor(secondsLeft / 60);
  const seconds = String(secondsLeft % 60).padStart(2, "0");
  return secondsLeft > 0 ? (
    <p className="text-sm">
      Your items are held for <span className="font-semibold tabular-nums">{minutes}:{seconds}</span>. Pay
      before then to keep them.
    </p>
  ) : (
    <p className="text-sm">Time&apos;s up. Your items have been released.</p>
  );
}
