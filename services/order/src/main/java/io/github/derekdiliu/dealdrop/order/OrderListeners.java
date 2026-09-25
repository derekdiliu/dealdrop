package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.OrderEvents.PaymentTimeout;
import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
class OrderListeners {

    private final OrderService orders;

    OrderListeners(OrderService orders) {
        this.orders = orders;
    }

    @RabbitListener(queues = Messaging.PLACE_REQUESTS)
    void onPlaceOrder(PlaceOrder request) {
        orders.place(request);
    }

    /** Paid or already-cancelled orders are left untouched. */
    @RabbitListener(queues = Messaging.PAYMENT_TIMEOUT_EXPIRED)
    void onPaymentTimeout(PaymentTimeout timeout) {
        orders.expire(timeout.orderId());
    }
}
