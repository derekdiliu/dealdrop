package io.github.derekdiliu.dealdrop.order;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Stock and per-customer limits, enforced with single conditional SQL statements so concurrent
 * orders can never oversell or exceed a limit. All methods run inside the caller's transaction.
 */
@Component
@Transactional(propagation = Propagation.MANDATORY)
class Inventory {

    private final JdbcClient jdbc;

    Inventory(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Adds quantity to what the customer holds of this deal, unless that would exceed the limit. */
    boolean claimPurchaseAllowance(UUID userId, UUID dealId, int quantity, int limit) {
        return jdbc.sql("""
                        INSERT INTO purchase_counts (user_id, deal_id, quantity)
                        VALUES (:userId, :dealId, :quantity)
                        ON CONFLICT (user_id, deal_id) DO UPDATE
                            SET quantity = purchase_counts.quantity + EXCLUDED.quantity
                            WHERE purchase_counts.quantity + EXCLUDED.quantity <= :limit
                        """)
                .param("userId", userId)
                .param("dealId", dealId)
                .param("quantity", quantity)
                .param("limit", limit)
                .update() == 1;
    }

    /** Takes quantity from the deal's remaining stock, seeding it from the catalog on first use. */
    boolean reserveStock(UUID dealId, int initialStock, int quantity) {
        jdbc.sql("INSERT INTO deal_inventory (deal_id, remaining) VALUES (:dealId, :stock) ON CONFLICT DO NOTHING")
                .param("dealId", dealId)
                .param("stock", initialStock)
                .update();
        return jdbc.sql("""
                        UPDATE deal_inventory SET remaining = remaining - :quantity
                        WHERE deal_id = :dealId AND remaining >= :quantity
                        """)
                .param("dealId", dealId)
                .param("quantity", quantity)
                .update() == 1;
    }

    /**
     * Cancels the order if it is still awaiting payment and returns its stock and allowance.
     * Returns empty if the order was already paid or cancelled, which makes repeated calls harmless.
     */
    Optional<Released> cancelIfPending(UUID orderId, CancelReason reason) {
        return jdbc.sql("""
                        UPDATE orders
                        SET status = 'CANCELLED', cancel_reason = :reason, updated_at = :now, version = version + 1
                        WHERE id = :orderId AND status = 'PENDING_PAYMENT'
                        RETURNING user_id, deal_id, quantity
                        """)
                .param("reason", reason.name())
                .param("now", Timestamp.from(Instant.now()))
                .param("orderId", orderId)
                .query(Released.class)
                .optional()
                .map(released -> {
                    release(released);
                    return released;
                });
    }

    private void release(Released released) {
        jdbc.sql("UPDATE deal_inventory SET remaining = remaining + :quantity WHERE deal_id = :dealId")
                .param("quantity", released.quantity())
                .param("dealId", released.dealId())
                .update();
        jdbc.sql("""
                        UPDATE purchase_counts SET quantity = quantity - :quantity
                        WHERE user_id = :userId AND deal_id = :dealId
                        """)
                .param("quantity", released.quantity())
                .param("userId", released.userId())
                .param("dealId", released.dealId())
                .update();
    }

    record Released(UUID userId, UUID dealId, int quantity) {
    }
}
