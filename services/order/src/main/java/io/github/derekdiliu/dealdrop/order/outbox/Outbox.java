package io.github.derekdiliu.dealdrop.order.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Records a message in the same database transaction as the change that caused it, so the message
 * is sent if and only if the change commits. {@link OutboxRelay} publishes it afterwards.
 */
@Component
public class Outbox {

    private final JdbcClient jdbc;

    Outbox(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param expiration how long the broker keeps the message before dead-lettering it, or null to keep it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, String payload, Duration expiration) {
        jdbc.sql("""
                        INSERT INTO outbox_messages (id, exchange, routing_key, payload, expiration_ms, created_at)
                        VALUES (:id, :exchange, :routingKey, :payload, :expirationMs, :createdAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("exchange", exchange)
                .param("routingKey", routingKey)
                .param("payload", payload)
                .param("expirationMs", expiration == null ? null : expiration.toMillis())
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
