package io.github.derekdiliu.dealdrop.payment.stripe;

import java.util.UUID;

/**
 * The card payment provider (Stripe). Kept behind an interface so payment logic can be tested without it.
 * Implementations throw {@link PaymentProviderException} when the provider cannot be reached or refuses.
 */
public interface PaymentProvider {

    /** Idempotent per order: asking again for the same order returns the same intent. */
    Intent createIntent(UUID orderId, UUID userId, long amountCents, String currency);

    /** The secret the browser needs to confirm the payment; it is never stored. */
    String clientSecret(String paymentIntentId);

    /** Returns false if the intent can no longer be cancelled, for example because it already succeeded. */
    boolean cancel(String paymentIntentId);

    /** Refunds the intent in full. Idempotent per intent. */
    void refund(String paymentIntentId, UUID orderId);

    record Intent(String id, String clientSecret) {
    }
}
