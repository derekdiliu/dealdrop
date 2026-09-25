package io.github.derekdiliu.dealdrop.order.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Publishes outbox messages to RabbitMQ. Delivery is at-least-once: a crash between the broker's
 * confirm and marking the row means the message is sent again, so consumers must be idempotent.
 */
@Component
class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int BATCH_SIZE = 100;
    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final JdbcClient jdbc;
    private final RabbitTemplate rabbit;
    private final TransactionTemplate transaction;

    OutboxRelay(JdbcClient jdbc, RabbitTemplate rabbit, TransactionTemplate transaction) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
        this.transaction = transaction;
    }

    @Scheduled(fixedDelayString = "${dealdrop.outbox.poll-interval}")
    void publishPending() {
        transaction.executeWithoutResult(status -> {
            // SKIP LOCKED lets several service instances relay in parallel without sending a message twice.
            List<PendingMessage> batch = jdbc.sql("""
                            SELECT id, exchange, routing_key, payload, expiration_ms
                            FROM outbox_messages
                            WHERE published_at IS NULL
                            ORDER BY created_at
                            LIMIT :limit
                            FOR UPDATE SKIP LOCKED
                            """)
                    .param("limit", BATCH_SIZE)
                    .query(PendingMessage.class)
                    .list();

            for (PendingMessage pending : batch) {
                try {
                    publish(pending);
                } catch (AmqpException e) {
                    // Keep what was confirmed so far; the rest is retried on the next poll.
                    log.warn("Outbox publish failed, will retry: {}", e.getMessage());
                    return;
                }
                jdbc.sql("UPDATE outbox_messages SET published_at = :now WHERE id = :id")
                        .param("now", Timestamp.from(Instant.now()))
                        .param("id", pending.id())
                        .update();
            }
        });
    }

    private void publish(PendingMessage pending) {
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setMessageId(pending.id().toString());
        if (pending.expirationMs() != null) {
            properties.setExpiration(pending.expirationMs().toString());
        }
        Message message = new Message(pending.payload().getBytes(StandardCharsets.UTF_8), properties);
        rabbit.invoke(operations -> {
            operations.send(pending.exchange(), pending.routingKey(), message);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }

    record PendingMessage(UUID id, String exchange, String routingKey, String payload, Long expirationMs) {
    }
}
