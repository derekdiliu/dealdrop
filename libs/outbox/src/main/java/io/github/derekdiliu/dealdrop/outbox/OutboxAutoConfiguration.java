package io.github.derekdiliu.dealdrop.outbox;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Registers the outbox in any service that depends on this module. The service owns the
 * outbox_messages table (in its own migrations), enables scheduling, and sets dealdrop.outbox.*.
 */
@AutoConfiguration
@Import({Outbox.class, OutboxRelay.class, OutboxCleanup.class})
public class OutboxAutoConfiguration {
}
