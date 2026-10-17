"use client";

import { Elements, PaymentElement, useElements, useStripe } from "@stripe/react-stripe-js";
import { loadStripe } from "@stripe/stripe-js";
import { type FormEvent, useEffect, useState } from "react";

import { FormError } from "@/components/form";
import { buttonClass } from "@/components/styles";

const publishableKey = process.env.NEXT_PUBLIC_STRIPE_PUBLISHABLE_KEY;
const stripePromise = publishableKey ? loadStripe(publishableKey) : null;

/**
 * Collects the payment with Stripe's Payment Element. Card details go straight from the browser to
 * Stripe; the order becomes paid when Stripe's webhook reaches the backend.
 */
export function PaymentPanel({ orderId }: { orderId: string }) {
  const [payment, setPayment] = useState<{ clientSecret?: string; error?: string }>({});

  useEffect(() => {
    if (!stripePromise) {
      return;
    }
    fetch(`/api/orders/${orderId}/payment`, { method: "POST" })
      .then(async (response) => {
        const body = await response.json();
        setPayment(response.ok ? { clientSecret: body.clientSecret } : { error: body.detail });
      })
      .catch(() => setPayment({ error: "Payments are unavailable right now. Please try again shortly." }));
  }, [orderId]);

  if (!stripePromise) {
    return <p className="text-sm text-zinc-500">Online payment isn&apos;t set up in this environment.</p>;
  }
  if (payment.error) {
    return <FormError message={payment.error} />;
  }
  if (!payment.clientSecret) {
    return <p className="text-sm text-zinc-500">Loading payment options…</p>;
  }
  return (
    <Elements stripe={stripePromise} options={{ clientSecret: payment.clientSecret }}>
      <PayForm orderId={orderId} />
    </Elements>
  );
}

function PayForm({ orderId }: { orderId: string }) {
  const stripe = useStripe();
  const elements = useElements();
  const [error, setError] = useState<string>();
  const [paying, setPaying] = useState(false);

  async function pay(event: FormEvent) {
    event.preventDefault();
    if (!stripe || !elements) {
      return;
    }
    setPaying(true);
    // On success Stripe redirects back to the order page (after 3-D Secure if the bank asks for it).
    const result = await stripe.confirmPayment({
      elements,
      confirmParams: { return_url: `${window.location.origin}/orders/${orderId}` },
    });
    setError(result.error.message);
    setPaying(false);
  }

  return (
    <form onSubmit={pay} className="space-y-4">
      <PaymentElement />
      <FormError message={error} />
      <button type="submit" disabled={!stripe || paying} className={buttonClass}>
        {paying ? "Processing…" : "Pay now"}
      </button>
    </form>
  );
}
