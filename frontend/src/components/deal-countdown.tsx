"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";

import { formatDuration } from "@/lib/format";

const REFRESH_EVERY_MS = 3000;

/**
 * Time until a deal starts or ends. At zero it re-renders the page, and keeps doing so until the server
 * agrees the deal has moved on (the server's clock may be a moment behind the browser's).
 */
export function DealCountdown({ until, label }: { until: string; label: string }) {
  const router = useRouter();
  const deadline = new Date(until).getTime();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const tick = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(tick);
  }, []);

  const secondsLeft = Math.max(0, Math.ceil((deadline - now) / 1000));
  useEffect(() => {
    if (secondsLeft === 0) {
      router.refresh();
      const refresh = setInterval(() => router.refresh(), REFRESH_EVERY_MS);
      return () => clearInterval(refresh);
    }
  }, [secondsLeft, router]);

  return (
    <p className="text-sm font-medium">
      {label}{" "}
      {/* The server renders a second or so before the browser hydrates. */}
      <span className="tabular-nums" suppressHydrationWarning>
        {secondsLeft > 0 ? formatDuration(secondsLeft) : "a moment"}
      </span>
    </p>
  );
}
