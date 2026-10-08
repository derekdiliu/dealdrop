package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.payment.messages.Messages.PaymentSucceeded;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * <pre>
 * Stripe webhook ──► payment.events ──payment.succeeded──► order service marks the order paid
 *
 * order.events ──order.cancelled─────────► payment.order-cancelled         ──► cancel the PaymentIntent
 *              ──order.payment-rejected──► payment.order-payment-rejected  ──► refund
 * </pre>
 *
 * order.events is owned by the order service and declared here too, with identical arguments.
 */
@Configuration(proxyBeanMethods = false)
class Messaging {

    static final String PAYMENT_EVENTS = "payment.events";
    static final String ORDER_EVENTS = "order.events";
    static final String ORDER_CANCELLED = "payment.order-cancelled";
    static final String ORDER_PAYMENT_REJECTED = "payment.order-payment-rejected";
    private static final String FAILED = "payment.order-events.failed";

    @Bean
    MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    Declarables topology() {
        TopicExchange orderEvents = new TopicExchange(ORDER_EVENTS);
        Queue cancelled = eventQueue(ORDER_CANCELLED);
        Queue paymentRejected = eventQueue(ORDER_PAYMENT_REJECTED);
        return new Declarables(
                new TopicExchange(PAYMENT_EVENTS),
                orderEvents,
                cancelled,
                paymentRejected,
                QueueBuilder.durable(FAILED).build(),
                BindingBuilder.bind(cancelled).to(orderEvents).with("order.cancelled"),
                BindingBuilder.bind(paymentRejected).to(orderEvents).with("order.payment-rejected"));
    }

    /** Messages that still fail after retries are parked in FAILED for inspection. */
    private static Queue eventQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange("")
                .deadLetterRoutingKey(FAILED)
                .build();
    }

    @Component
    static class PaymentEvents {

        private static final long CONFIRM_TIMEOUT_MS = 5_000;

        private final RabbitTemplate rabbit;

        PaymentEvents(RabbitTemplate rabbit) {
            this.rabbit = rabbit;
        }

        /** Returns once the broker has stored the event; throws an AmqpException otherwise. */
        void succeeded(PaymentSucceeded event) {
            rabbit.invoke(operations -> {
                operations.convertAndSend(PAYMENT_EVENTS, PaymentSucceeded.ROUTING_KEY, event);
                operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
                return null;
            });
        }
    }
}
