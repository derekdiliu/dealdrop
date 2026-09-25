package io.github.derekdiliu.dealdrop.order.messages;

import io.github.derekdiliu.dealdrop.order.ShippingAddress;

import java.util.UUID;

/**
 * Sent by flash-sale after it has admitted a purchase. The order id is chosen by flash-sale so the
 * customer can poll for the result straight away. Deal details are a snapshot from the catalog.
 */
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
