package io.github.derekdiliu.dealdrop.order.messages;

import java.util.UUID;

/**
 * Events published on the order.events exchange. Routing keys are the constants below.
 */
public final class OrderEvents {

    public static final String PLACED = "order.placed";
    public static final String REJECTED = "order.rejected";
    public static final String CANCELLED = "order.cancelled";
    public static final String PAYMENT_REJECTED = "order.payment-rejected";

    private OrderEvents() {
    }

    public record OrderPlaced(UUID orderId) {
    }

    /** The order could not be created; no stock is held for it. */
    public record OrderRejected(UUID orderId, UUID userId, UUID dealId, int quantity, RejectReason reason) {
    }

    /** A placed order was cancelled and its stock returned. */
    public record OrderCancelled(UUID orderId, UUID userId, UUID dealId, int quantity) {
    }

    /** A payment arrived that the order cannot accept; the payment service refunds it. */
    public record OrderPaymentRejected(UUID orderId, String paymentIntentId, PaymentRejectReason reason) {
    }

    public enum PaymentRejectReason {
        /** Most often the payment window closed just before the customer paid. */
        ORDER_NOT_AWAITING_PAYMENT,
        ORDER_NOT_FOUND,
        AMOUNT_MISMATCH
    }

    public enum RejectReason {
        SOLD_OUT,
        LIMIT_EXCEEDED
    }

    /** Self-addressed: delivered after the payment window to cancel the order if still unpaid. */
    public record PaymentTimeout(UUID orderId) {
    }
}
