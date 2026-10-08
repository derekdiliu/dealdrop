package io.github.derekdiliu.dealdrop.payment.messages;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Message contracts shared with the order service (as JSON; each side keeps its own copy).
 * Incoming events only declare the fields this service uses.
 */
public final class Messages {

    private Messages() {
    }

    /** Published on payment.events once Stripe confirms a payment. */
    public record PaymentSucceeded(UUID orderId, String paymentIntentId, long amountCents, String currency) {

        public static final String ROUTING_KEY = "payment.succeeded";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderCancelled(UUID orderId) {
    }

    /** The order could not accept a payment, so it must be refunded. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderPaymentRejected(UUID orderId, String paymentIntentId, String reason) {
    }
}
