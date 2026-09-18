package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.order.catalog.DealCatalog;
import io.github.derekdiliu.dealdrop.order.shipping.ShippingRates;
import io.github.derekdiliu.dealdrop.order.shipping.ShippingZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final DealCatalog catalog;
    private final Inventory inventory;
    private final OrderRepository orders;
    private final ShippingRates shippingRates;
    private final TransactionTemplate transaction;
    private final Duration paymentTimeout;

    OrderService(DealCatalog catalog, Inventory inventory, OrderRepository orders,
                 ShippingRates shippingRates, TransactionTemplate transaction,
                 @Value("${dealdrop.order.payment-timeout}") Duration paymentTimeout) {
        this.catalog = catalog;
        this.inventory = inventory;
        this.orders = orders;
        this.shippingRates = shippingRates;
        this.transaction = transaction;
        this.paymentTimeout = paymentTimeout;
    }

    /**
     * Reserves stock and the customer's allowance, records the order and schedules its payment timeout,
     * all in one transaction: either everything happens or nothing does.
     */
    Order place(UUID userId, UUID dealId, int quantity, ShippingAddress address) {
        // Read the deal before the transaction so a slow catalog never holds a database connection.
        CatalogDeal deal = catalog.find(dealId).orElseThrow(() -> ApiErrors.notFound("Deal not found."));
        switch (deal.state()) {
            case UPCOMING -> throw ApiErrors.conflict("This deal hasn't started yet.");
            case ENDED -> throw ApiErrors.conflict("This deal has ended.");
            case LIVE -> { }
        }
        if (quantity > deal.perCustomerLimit()) {
            throw limitExceeded(deal);
        }
        ShippingZone zone = ShippingZone.forPostcode(address.postcode());
        long shippingCents = shippingRates.costFor(zone, address.ruralDelivery());

        return transaction.execute(status -> {
            if (!inventory.claimPurchaseAllowance(userId, dealId, quantity, deal.perCustomerLimit())) {
                throw limitExceeded(deal);
            }
            if (!inventory.reserveStock(dealId, deal.stock(), quantity)) {
                throw ApiErrors.conflict("Sorry, there isn't enough stock left for that quantity.");
            }
            Order order = orders.save(new Order(userId, deal, quantity, address, zone, shippingCents, paymentTimeout));
            return order;
        });
    }

    List<Order> listFor(UUID userId) {
        return orders.findTop50ByUserIdOrderByCreatedAtDesc(userId);
    }

    Order getFor(UUID userId, UUID orderId) {
        return orders.findByIdAndUserId(orderId, userId).orElseThrow(() -> ApiErrors.notFound("Order not found."));
    }

    Order cancelByCustomer(UUID userId, UUID orderId) {
        getFor(userId, orderId);
        Boolean cancelled = transaction.execute(status -> inventory.cancelIfPending(orderId, CancelReason.CUSTOMER));
        if (!Boolean.TRUE.equals(cancelled)) {
            throw ApiErrors.conflict("Only orders awaiting payment can be cancelled.");
        }
        return getFor(userId, orderId);
    }

    /** Called when an order's payment window closes. Safe to call more than once. */
    void expire(UUID orderId) {
        Boolean cancelled = transaction.execute(status -> inventory.cancelIfPending(orderId, CancelReason.PAYMENT_TIMEOUT));
        if (Boolean.TRUE.equals(cancelled)) {
            log.info("Cancelled unpaid order {} after payment timeout", orderId);
        }
    }

    private static RuntimeException limitExceeded(CatalogDeal deal) {
        return ApiErrors.conflict("You can buy at most %d of this deal.".formatted(deal.perCustomerLimit()));
    }
}
