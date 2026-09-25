package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.Inventory.Released;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.OrderCancelled;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.OrderPlaced;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.OrderRejected;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.PaymentTimeout;
import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.RejectReason;
import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import io.github.derekdiliu.dealdrop.order.outbox.Outbox;
import io.github.derekdiliu.dealdrop.order.shipping.ShippingRates;
import io.github.derekdiliu.dealdrop.order.shipping.ShippingZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Postgres is the source of truth for stock: flash-sale admits purchases against Redis, but an order
 * only exists once the database has also reserved its stock here.
 */
@Service
class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final Inventory inventory;
    private final OrderRepository orders;
    private final Outbox outbox;
    private final ShippingRates shippingRates;
    private final TransactionTemplate transaction;
    private final Duration paymentTimeout;

    OrderService(Inventory inventory, OrderRepository orders, Outbox outbox, ShippingRates shippingRates,
                 TransactionTemplate transaction, @Value("${dealdrop.order.payment-timeout}") Duration paymentTimeout) {
        this.inventory = inventory;
        this.orders = orders;
        this.outbox = outbox;
        this.shippingRates = shippingRates;
        this.transaction = transaction;
        this.paymentTimeout = paymentTimeout;
    }

    /**
     * Reserves stock and the customer's allowance, records the order, schedules its payment timeout and
     * announces it, all in one transaction. If stock or allowance runs out, a rejection is announced instead.
     * Safe to call again with the same request, as happens when a message is redelivered.
     */
    void place(PlaceOrder request) {
        ShippingZone zone = ShippingZone.forPostcode(request.shippingAddress().postcode());
        long shippingCents = shippingRates.costFor(zone, request.shippingAddress().ruralDelivery());
        try {
            transaction.executeWithoutResult(status -> {
                if (orders.existsById(request.orderId())) {
                    return;
                }
                if (!inventory.claimPurchaseAllowance(request.userId(), request.dealId(), request.quantity(),
                        request.perCustomerLimit())) {
                    throw new Rejected(RejectReason.LIMIT_EXCEEDED);
                }
                if (!inventory.reserveStock(request.dealId(), request.dealStock(), request.quantity())) {
                    throw new Rejected(RejectReason.SOLD_OUT);
                }
                Order order = orders.save(new Order(request, zone, shippingCents, paymentTimeout));
                outbox.enqueue(Messaging.DEFAULT_EXCHANGE, Messaging.PAYMENT_TIMEOUT_WAITING,
                        new PaymentTimeout(order.getId()), paymentTimeout);
                outbox.enqueue(Messaging.EVENTS, OrderEvents.PLACED, new OrderPlaced(order.getId()), null);
            });
        } catch (Rejected rejected) {
            log.info("Rejected order {}: {}", request.orderId(), rejected.reason);
            transaction.executeWithoutResult(status -> outbox.enqueue(Messaging.EVENTS, OrderEvents.REJECTED,
                    new OrderRejected(request.orderId(), request.userId(), request.dealId(), request.quantity(),
                            rejected.reason), null));
        }
    }

    List<Order> listFor(UUID userId) {
        return orders.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    Order getFor(UUID userId, UUID orderId) {
        return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiErrors.notFound("Order not found."));
    }

    Order cancelByCustomer(UUID userId, UUID orderId) {
        getFor(userId, orderId);
        if (cancel(orderId, CancelReason.CUSTOMER).isEmpty()) {
            throw ApiErrors.conflict("Only orders awaiting payment can be cancelled.");
        }
        return getFor(userId, orderId);
    }

    /** Called when an order's payment window closes. Safe to call more than once. */
    void expire(UUID orderId) {
        cancel(orderId, CancelReason.PAYMENT_TIMEOUT)
                .ifPresent(released -> log.info("Cancelled unpaid order {} after payment timeout", orderId));
    }

    private Optional<Released> cancel(UUID orderId, CancelReason reason) {
        return transaction.execute(status -> {
            Optional<Released> released = inventory.cancelIfPending(orderId, reason);
            released.ifPresent(r -> outbox.enqueue(Messaging.EVENTS, OrderEvents.CANCELLED,
                    new OrderCancelled(orderId, r.userId(), r.dealId(), r.quantity()), null));
            return released;
        });
    }

    /** Rolls back the placement transaction; carries why. */
    private static final class Rejected extends RuntimeException {

        private final RejectReason reason;

        Rejected(RejectReason reason) {
            super(reason.name(), null, false, false);
            this.reason = reason;
        }
    }
}
