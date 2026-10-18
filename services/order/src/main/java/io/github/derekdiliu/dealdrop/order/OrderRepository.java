package io.github.derekdiliu.dealdrop.order;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    List<Order> findTop50ByUserIdOrderByCreatedAtDesc(UUID userId);

    Optional<Order> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Marks the order paid only if it is still awaiting payment for exactly this amount; returns rows changed.
     * Conditional, so it cannot race with a timeout or customer cancellation.
     */
    @Modifying(clearAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.status = io.github.derekdiliu.dealdrop.order.OrderStatus.PAID,
                o.paymentIntentId = :paymentIntentId, o.paidAt = :now, o.updatedAt = :now, o.version = o.version + 1
            WHERE o.id = :orderId
              AND o.status = io.github.derekdiliu.dealdrop.order.OrderStatus.PENDING_PAYMENT
              AND o.totalCents = :amountCents
            """)
    int markPaidIfPending(@Param("orderId") UUID orderId, @Param("paymentIntentId") String paymentIntentId,
                          @Param("amountCents") long amountCents, @Param("now") Instant now);
}
