package io.github.derekdiliu.dealdrop.flashsale.messaging;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <pre>
 * flash-sale ──► order.place-requests ──► order service
 *
 * order.events ──order.placed────► flash-sale.order-placed
 *              ──order.rejected──► flash-sale.order-rejected
 *              ──order.cancelled─► flash-sale.order-cancelled
 * </pre>
 *
 * The order service owns order.place-requests and order.events; they are declared here too, with
 * identical arguments, so either service can start first without messages being dropped.
 */
@Configuration(proxyBeanMethods = false)
public class Messaging {

    static final String DEFAULT_EXCHANGE = "";
    static final String PLACE_REQUESTS = "order.place-requests";
    static final String ORDER_EVENTS = "order.events";
    public static final String ORDER_PLACED = "flash-sale.order-placed";
    public static final String ORDER_REJECTED = "flash-sale.order-rejected";
    public static final String ORDER_CANCELLED = "flash-sale.order-cancelled";
    private static final String FAILED = "flash-sale.order-events.failed";

    @Bean
    MessageConverter jsonMessageConverter() {
        JacksonJsonMessageConverter converter = new JacksonJsonMessageConverter();
        converter.setAlwaysConvertToInferredType(true);
        return converter;
    }

    @Bean
    Declarables topology() {
        TopicExchange events = new TopicExchange(ORDER_EVENTS);
        Queue placed = eventQueue(ORDER_PLACED);
        Queue rejected = eventQueue(ORDER_REJECTED);
        Queue cancelled = eventQueue(ORDER_CANCELLED);
        return new Declarables(
                events,
                QueueBuilder.durable(PLACE_REQUESTS)
                        .deadLetterExchange(DEFAULT_EXCHANGE)
                        .deadLetterRoutingKey(PLACE_REQUESTS + ".failed")
                        .build(),
                QueueBuilder.durable(PLACE_REQUESTS + ".failed").build(),
                placed, rejected, cancelled,
                QueueBuilder.durable(FAILED).build(),
                BindingBuilder.bind(placed).to(events).with("order.placed"),
                BindingBuilder.bind(rejected).to(events).with("order.rejected"),
                BindingBuilder.bind(cancelled).to(events).with("order.cancelled"));
    }

    /** Messages that still fail after retries are parked in FAILED for inspection. */
    private static Queue eventQueue(String name) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(DEFAULT_EXCHANGE)
                .deadLetterRoutingKey(FAILED)
                .build();
    }
}
