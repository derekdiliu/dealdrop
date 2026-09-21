package io.github.derekdiliu.dealdrop.order;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class PaymentTimeoutListener {

    private final OrderService orders;

    PaymentTimeoutListener(OrderService orders) {
        this.orders = orders;
    }

    /** The payload is the order id. Paid or already-cancelled orders are left untouched. */
    @RabbitListener(queues = PaymentTimeoutQueues.EXPIRED)
    void onPaymentTimeout(String orderId) {
        orders.expire(UUID.fromString(orderId));
    }
}
