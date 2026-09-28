package io.github.derekdiliu.dealdrop.flashsale.messaging;

import io.github.derekdiliu.dealdrop.flashsale.messaging.Messages.PlaceOrder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OrderRequests {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbit;

    OrderRequests(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    /**
     * Returns once the broker has confirmed the request is stored, so an accepted purchase is never lost.
     * Throws an AmqpException if the broker cannot be reached or does not confirm in time.
     */
    public void send(PlaceOrder request) {
        rabbit.invoke(operations -> {
            operations.convertAndSend(Messaging.DEFAULT_EXCHANGE, Messaging.PLACE_REQUESTS, request);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
