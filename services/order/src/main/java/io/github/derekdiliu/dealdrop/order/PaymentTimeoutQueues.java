package io.github.derekdiliu.dealdrop.order;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Delayed cancellation of unpaid orders, built from RabbitMQ message TTL and dead-lettering:
 *
 * <pre>
 * outbox ──► WAITING (no consumers; message expires after the payment timeout)
 *               │ dead-letter
 *               ▼
 *            EXPIRED ──► PaymentTimeoutListener cancels the order if still unpaid
 *               │ dead-letter after retries are exhausted
 *               ▼
 *            FAILED (kept for inspection)
 * </pre>
 *
 * Messages are routed through the default exchange, which delivers to the queue named by the routing key.
 */
@Configuration(proxyBeanMethods = false)
class PaymentTimeoutQueues {

    static final String WAITING = "order.payment-timeout.waiting";
    static final String EXPIRED = "order.payment-timeout.expired";
    static final String FAILED = "order.payment-timeout.failed";
    private static final String DEFAULT_EXCHANGE = "";

    @Bean
    Queue paymentTimeoutWaiting() {
        return QueueBuilder.durable(WAITING)
                .deadLetterExchange(DEFAULT_EXCHANGE)
                .deadLetterRoutingKey(EXPIRED)
                .build();
    }

    @Bean
    Queue paymentTimeoutExpired() {
        return QueueBuilder.durable(EXPIRED)
                .deadLetterExchange(DEFAULT_EXCHANGE)
                .deadLetterRoutingKey(FAILED)
                .build();
    }

    @Bean
    Queue paymentTimeoutFailed() {
        return QueueBuilder.durable(FAILED).build();
    }
}
