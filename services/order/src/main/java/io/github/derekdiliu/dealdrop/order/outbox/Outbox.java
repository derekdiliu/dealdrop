package io.github.derekdiliu.dealdrop.order.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

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
    private final JsonMapper json;

    Outbox(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    /**
     * @param payload    serialized as JSON
     * @param expiration how long the broker keeps the message before dead-lettering it, or null to keep it
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(String exchange, String routingKey, Object payload, Duration expiration) {
        jdbc.sql("""
                        INSERT INTO outbox_messages (id, exchange, routing_key, payload, expiration_ms, created_at)
                        VALUES (:id, :exchange, :routingKey, :payload, :expirationMs, :createdAt)
                        """)
                .param("id", UUID.randomUUID())
                .param("exchange", exchange)
                .param("routingKey", routingKey)
                .param("payload", json.writeValueAsString(payload))
                .param("expirationMs", expiration == null ? null : expiration.toMillis())
                .param("createdAt", Timestamp.from(Instant.now()))
                .update();
    }
}
