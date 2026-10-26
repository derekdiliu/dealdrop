package io.github.derekdiliu.dealdrop.catalog;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * <pre>
 * outbox ──► catalog.events (deal.withdrawn) ──► order, flash-sale
 * </pre>
 *
 * Consumers declare the exchange too, with identical arguments, so either side can start first.
 */
@Configuration(proxyBeanMethods = false)
public class Messaging {

    public static final String EVENTS = "catalog.events";

    @Bean
    TopicExchange catalogEvents() {
        return new TopicExchange(EVENTS);
    }
}
