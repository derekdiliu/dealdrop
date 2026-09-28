package io.github.derekdiliu.dealdrop.flashsale.messaging;

import io.github.derekdiliu.dealdrop.flashsale.ShippingAddress;

import java.util.UUID;

/**
 * Message contracts shared with the order service (as JSON; each side keeps its own copy).
 */
public final class Messages {

    private Messages() {
    }

    /** Asks the order service to create an order this service has admitted. */
    public record PlaceOrder(
            UUID orderId,
            UUID userId,
            UUID dealId,
            String dealTitle,
            long unitPriceCents,
            int dealStock,
            int perCustomerLimit,
            int quantity,
            ShippingAddress shippingAddress) {
    }

    public record OrderPlaced(UUID orderId) {
    }

    public record OrderRejected(UUID orderId, UUID userId, UUID dealId, int quantity, RejectReason reason) {
    }

    public record OrderCancelled(UUID orderId, UUID userId, UUID dealId, int quantity) {
    }

    public enum RejectReason {
        SOLD_OUT,
        LIMIT_EXCEEDED
    }
}
