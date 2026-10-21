package io.github.derekdiliu.dealdrop.order.outbox;

import io.github.derekdiliu.dealdrop.order.TestJwt;
import io.github.derekdiliu.dealdrop.order.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "dealdrop.outbox.retention=P7D")
@Import({TestcontainersConfiguration.class, TestJwt.class})
class OutboxCleanupTests {

    @Autowired
    private OutboxCleanup cleanup;

    @Autowired
    private JdbcClient jdbc;

    @Test
    void deletesOnlyMessagesPublishedBeforeTheRetentionPeriod() {
        Instant now = Instant.now();
        UUID publishedLongAgo = insert(now.minus(Duration.ofDays(9)), now.minus(Duration.ofDays(8)));
        UUID publishedRecently = insert(now.minus(Duration.ofDays(9)), now.minus(Duration.ofDays(6)));
        // Never published, however old: deleting it would lose the message.
        UUID unpublished = insert(now.minus(Duration.ofDays(9)), null);

        cleanup.deletePublished();

        assertThat(exists(publishedLongAgo)).isFalse();
        assertThat(exists(publishedRecently)).isTrue();
        assertThat(exists(unpublished)).isTrue();
    }

    private UUID insert(Instant createdAt, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        // The default exchange accepts any routing key, so the relay can publish this harmlessly.
        jdbc.sql("""
                        INSERT INTO outbox_messages (id, exchange, routing_key, payload, created_at, published_at)
                        VALUES (:id, '', 'outbox-cleanup-test', '{}', :createdAt, :publishedAt)
                        """)
                .param("id", id)
                .param("createdAt", Timestamp.from(createdAt))
                .param("publishedAt", publishedAt == null ? null : Timestamp.from(publishedAt))
                .update();
        return id;
    }

    private boolean exists(UUID id) {
        return jdbc.sql("SELECT count(*) FROM outbox_messages WHERE id = :id").param("id", id)
                .query(Integer.class).single() == 1;
    }
}
