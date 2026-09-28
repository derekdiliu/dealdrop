package io.github.derekdiliu.dealdrop.flashsale;

import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderCancelled;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderPlaced;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.OrderRejected;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.RejectReason;
import io.github.derekdiliu.dealdrop.flashsale.messaging.Messaging;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Keeps Redis in step with what the order service decided. Each handler is safe to run more than once.
 */
@Component
class OrderEventListener {

    private final StockGate stock;
    private final Purchases purchases;

    OrderEventListener(StockGate stock, Purchases purchases) {
        this.stock = stock;
        this.purchases = purchases;
    }

    @RabbitListener(queues = Messaging.ORDER_PLACED)
    void onPlaced(OrderPlaced event) {
        purchases.confirmed(event.orderId());
    }

    @RabbitListener(queues = Messaging.ORDER_REJECTED)
    void onRejected(OrderRejected event) {
        if (event.reason() == RejectReason.SOLD_OUT) {
            // Redis admitted a purchase the database could not fill, so Redis was over-counting stock.
            // Return only the customer's allowance and stop selling until real stock is given back.
            stock.release(event.orderId(), event.dealId(), event.userId(), event.quantity(), false);
            stock.markSoldOut(event.dealId());
        } else {
            stock.release(event.orderId(), event.dealId(), event.userId(), event.quantity(), true);
        }
        purchases.rejected(event.orderId(), event.reason().name());
    }

    @RabbitListener(queues = Messaging.ORDER_CANCELLED)
    void onCancelled(OrderCancelled event) {
        stock.release(event.orderId(), event.dealId(), event.userId(), event.quantity(), true);
    }
}
