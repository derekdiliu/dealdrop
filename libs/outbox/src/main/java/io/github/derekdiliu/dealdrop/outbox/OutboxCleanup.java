package io.github.derekdiliu.dealdrop.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/**
 * Deletes messages once they have been published for longer than the retention period, so the outbox
 * does not grow forever. They are kept that long to show what was sent when investigating an order.
 * Unpublished messages are never deleted.
 */
@Component
public class OutboxCleanup {

    private static final Logger log = LoggerFactory.getLogger(OutboxCleanup.class);

    private final JdbcClient jdbc;
    private final Duration retention;

    OutboxCleanup(JdbcClient jdbc, @Value("${dealdrop.outbox.retention}") Duration retention) {
        this.jdbc = jdbc;
        this.retention = retention;
    }

    @Scheduled(fixedDelayString = "${dealdrop.outbox.cleanup-interval}")
    public void deletePublished() {
        int deleted = jdbc.sql("DELETE FROM outbox_messages WHERE published_at < :cutoff")
                .param("cutoff", Timestamp.from(Instant.now().minus(retention)))
                .update();
        if (deleted > 0) {
            log.info("Deleted {} outbox messages published more than {} ago", deleted, retention);
        }
    }
}
