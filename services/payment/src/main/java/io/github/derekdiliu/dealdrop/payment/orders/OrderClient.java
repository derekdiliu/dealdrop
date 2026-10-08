package io.github.derekdiliu.dealdrop.payment.orders;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderClient {

    /**
     * Looks the order up as the customer, by forwarding their token: the order service only returns
     * the customer's own orders, so this also proves the order is theirs. Empty if not found or not theirs.
     */
    Optional<OrderSummary> find(UUID orderId, String customerToken);

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderSummary(UUID id, String status, long totalCents, String currency, Instant expiresAt) {

        public boolean isAwaitingPayment() {
            return "PENDING_PAYMENT".equals(status);
        }
    }
}
