package io.github.derekdiliu.dealdrop.order.messages;

import java.util.UUID;

/**
 * Published by the payment service on the payment.events exchange once Stripe confirms a payment.
 */
public record PaymentSucceeded(UUID orderId, String paymentIntentId, long amountCents, String currency) {

    public static final String ROUTING_KEY = "payment.succeeded";
}
