package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.payment.messages.Messages.OrderCancelled;
import io.github.derekdiliu.dealdrop.payment.messages.Messages.OrderPaymentRejected;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Both handlers are safe to repeat: cancelling and refunding are idempotent at Stripe.
 */
@Component
class OrderEventListener {

    private final PaymentService payments;

    OrderEventListener(PaymentService payments) {
        this.payments = payments;
    }

    @RabbitListener(queues = Messaging.ORDER_CANCELLED)
    void onOrderCancelled(OrderCancelled event) {
        payments.orderCancelled(event.orderId());
    }

    @RabbitListener(queues = Messaging.ORDER_PAYMENT_REJECTED)
    void onPaymentRejected(OrderPaymentRejected event) {
        payments.refund(event.orderId(), event.paymentIntentId(), event.reason());
    }
}
