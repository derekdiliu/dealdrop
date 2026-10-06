package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.PaymentSucceeded;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology owned by the order service.
 *
 * <pre>
 * flash-sale ──► PLACE_REQUESTS ──► PlaceOrderListener
 *
 * outbox ──► PAYMENT_TIMEOUT_WAITING (no consumers; message expires after the payment window)
 *               │ dead-letter
 *               ▼
 *            PAYMENT_TIMEOUT_EXPIRED ──► PaymentTimeoutListener cancels the order if still unpaid
 *
 * outbox ──► EVENTS exchange (order.placed / order.rejected / order.cancelled / order.payment-rejected)
 *
 * payment.events ──payment.succeeded──► PAYMENT_SUCCEEDED ──► OrderListeners marks the order paid
 * </pre>
 *
 * Consumer queues dead-letter to a ".failed" queue once retries are exhausted, so a poison message is
 * kept for inspection rather than redelivered forever. Direct queues use the default exchange, which
 * routes to the queue named by the routing key.
 *
 * RabbitMQ rejects redeclaring a queue with different arguments, so never change an existing queue's
 * arguments (including its dead-letter target); add a new queue instead.
 */
@Configuration(proxyBeanMethods = false)
class Messaging {

    static final String DEFAULT_EXCHANGE = "";
    static final String EVENTS = "order.events";
    // Declared identically by flash-sale, which publishes to it.
    static final String PLACE_REQUESTS = "order.place-requests";
    static final String PAYMENT_TIMEOUT_WAITING = "order.payment-timeout.waiting";
    static final String PAYMENT_TIMEOUT_EXPIRED = "order.payment-timeout.expired";
    // Owned by the payment service; declared here too so the binding below can be made.
    static final String PAYMENT_EVENTS = "payment.events";
    static final String PAYMENT_SUCCEEDED = "order.payment-succeeded";
    private static final String PAYMENT_TIMEOUT_FAILED = "order.payment-timeout.failed";

    /** JSON payloads, deserialized into each listener's parameter type rather than a sender-chosen class. */
    @Bean
    MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    TopicExchange orderEvents() {
        return new TopicExchange(EVENTS);
    }

    @Bean
    Queue placeRequests() {
        return deadLetteringTo(PLACE_REQUESTS, PLACE_REQUESTS + ".failed");
    }

    @Bean
    Queue placeRequestsFailed() {
        return QueueBuilder.durable(PLACE_REQUESTS + ".failed").build();
    }

    @Bean
    Queue paymentTimeoutWaiting() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_WAITING)
                .deadLetterExchange(DEFAULT_EXCHANGE)
                .deadLetterRoutingKey(PAYMENT_TIMEOUT_EXPIRED)
                .build();
    }

    @Bean
    Queue paymentTimeoutExpired() {
        return deadLetteringTo(PAYMENT_TIMEOUT_EXPIRED, PAYMENT_TIMEOUT_FAILED);
    }

    @Bean
    Queue paymentTimeoutFailed() {
        return QueueBuilder.durable(PAYMENT_TIMEOUT_FAILED).build();
    }

    @Bean
    TopicExchange paymentEvents() {
        return new TopicExchange(PAYMENT_EVENTS);
    }

    @Bean
    Queue paymentSucceeded() {
        return deadLetteringTo(PAYMENT_SUCCEEDED, PAYMENT_SUCCEEDED + ".failed");
    }

    @Bean
    Queue paymentSucceededFailed() {
        return QueueBuilder.durable(PAYMENT_SUCCEEDED + ".failed").build();
    }

    @Bean
    Binding paymentSucceededBinding() {
        return BindingBuilder.bind(paymentSucceeded()).to(paymentEvents()).with(PaymentSucceeded.ROUTING_KEY);
    }

    private static Queue deadLetteringTo(String name, String failedQueue) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEFAULT_EXCHANGE)
                .deadLetterRoutingKey(failedQueue)
                .build();
    }
}
