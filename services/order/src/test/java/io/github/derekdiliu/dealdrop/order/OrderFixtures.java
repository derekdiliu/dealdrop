package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.UUID;

final class OrderFixtures {

    private OrderFixtures() {
    }

    /** A placement request as flash-sale would send it: $79.99 per unit, fresh order id. */
    static PlaceOrder request(UUID userId, UUID dealId, int dealStock, int limit, int quantity, String postcode,
                              boolean ruralDelivery) {
        return new PlaceOrder(UUID.randomUUID(), userId, dealId, "Merino throw", 7999, dealStock, limit, quantity,
                address(postcode, ruralDelivery));
    }

    static PlaceOrder request(UUID dealId, int dealStock, int limit, int quantity) {
        return request(UUID.randomUUID(), dealId, dealStock, limit, quantity, "1010", false);
    }

    static ShippingAddress address(String postcode, boolean ruralDelivery) {
        return new ShippingAddress("Aroha Smith", "021 123 4567", "12 Queen Street", null, "Auckland Central",
                "Auckland", postcode, ruralDelivery);
    }

    static int remainingStock(JdbcClient jdbc, UUID dealId) {
        return jdbc.sql("SELECT remaining FROM deal_inventory WHERE deal_id = :dealId")
                .param("dealId", dealId)
                .query(Integer.class)
                .single();
    }

    /** Routing keys of outbox messages about this order, oldest first. */
    static List<String> outboxRoutingKeys(JdbcClient jdbc, UUID orderId) {
        return jdbc.sql("SELECT routing_key FROM outbox_messages WHERE payload LIKE :id ORDER BY created_at")
                .param("id", "%" + orderId + "%")
                .query(String.class)
                .list();
    }
}
