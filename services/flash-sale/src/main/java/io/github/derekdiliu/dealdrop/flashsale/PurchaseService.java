package io.github.derekdiliu.dealdrop.flashsale;

import io.github.derekdiliu.dealdrop.common.ApiErrors;
import io.github.derekdiliu.dealdrop.flashsale.Purchases.Purchase;
import io.github.derekdiliu.dealdrop.flashsale.StockGate.Outcome;
import io.github.derekdiliu.dealdrop.flashsale.catalog.CatalogDeal;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.PlaceOrder;
import io.github.derekdiliu.dealdrop.flashsale.messaging.OrderRequests;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * The hot path of a sale. A purchase is decided in Redis and answered immediately; the order itself is
 * created asynchronously by the order service, which the customer follows by polling the purchase.
 */
@Service
class PurchaseService {

    private static final Logger log = LoggerFactory.getLogger(PurchaseService.class);

    private final DealCache deals;
    private final StockGate stock;
    private final Purchases purchases;
    private final OrderRequests orderRequests;

    PurchaseService(DealCache deals, StockGate stock, Purchases purchases, OrderRequests orderRequests) {
        this.deals = deals;
        this.stock = stock;
        this.purchases = purchases;
        this.orderRequests = orderRequests;
    }

    /** Returns the purchase id, which becomes the order id once the order service accepts it. */
    UUID purchase(UUID userId, UUID dealId, int quantity, ShippingAddress address) {
        CatalogDeal deal = deals.get(dealId);
        Instant now = Instant.now();
        if (now.isBefore(deal.startsAt())) {
            throw ApiErrors.conflict("This deal hasn't started yet.");
        }
        if (!deal.isLiveAt(now)) {
            throw ApiErrors.conflict("This deal has ended.");
        }
        if (quantity > deal.perCustomerLimit()) {
            throw limitReached(deal);
        }

        switch (stock.reserve(deal, userId, quantity)) {
            case SOLD_OUT -> throw ApiErrors.conflict("Sorry, this deal has sold out.");
            case LIMIT_REACHED -> throw limitReached(deal);
            case TOO_MANY_ATTEMPTS -> throw ApiErrors.tooManyRequests("Too many attempts. Please wait a moment.");
            case WITHDRAWN -> throw ApiErrors.conflict("Sorry, this deal is no longer available.");
            case RESERVED -> { }
        }

        UUID purchaseId = UUID.randomUUID();
        purchases.pending(purchaseId, userId);
        try {
            orderRequests.send(new PlaceOrder(purchaseId, userId, dealId, deal.title(), deal.priceCents(),
                    deal.stock(), deal.perCustomerLimit(), quantity, address));
        } catch (AmqpException e) {
            // Nothing reached the order service, so undo the reservation rather than strand the stock.
            log.warn("Could not send order request for purchase {}: {}", purchaseId, e.getMessage());
            stock.release(purchaseId, dealId, userId, quantity, true);
            purchases.forget(purchaseId);
            throw ApiErrors.serviceUnavailable("We couldn't take your order right now. Please try again.");
        }
        return purchaseId;
    }

    Purchase find(UUID userId, UUID purchaseId) {
        return purchases.find(purchaseId)
                .filter(purchase -> purchase.userId().equals(userId))
                .orElseThrow(() -> ApiErrors.notFound("Purchase not found."));
    }

    /** Remaining stock as the sale sees it; before the first purchase, the deal's full stock. */
    int remaining(UUID dealId) {
        return stock.remaining(dealId).orElseGet(() -> deals.get(dealId).stock());
    }

    private static RuntimeException limitReached(CatalogDeal deal) {
        return ApiErrors.conflict("You can buy at most %d of this deal.".formatted(deal.perCustomerLimit()));
    }
}
