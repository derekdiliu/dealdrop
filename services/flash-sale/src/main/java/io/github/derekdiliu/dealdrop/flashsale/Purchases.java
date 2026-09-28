package io.github.derekdiliu.dealdrop.flashsale;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Status of each purchase while the order service works on it, kept in Redis for clients to poll.
 */
@Component
class Purchases {

    private static final Duration LIFETIME = Duration.ofDays(1);

    private final StringRedisTemplate redis;

    Purchases(StringRedisTemplate redis) {
        this.redis = redis;
    }

    void pending(UUID purchaseId, UUID userId) {
        String key = key(purchaseId);
        redis.opsForHash().putAll(key, Map.of("userId", userId.toString(), "status", Status.PENDING.name()));
        redis.expire(key, LIFETIME);
    }

    void confirmed(UUID purchaseId) {
        update(purchaseId, Map.of("status", Status.CONFIRMED.name()));
    }

    void rejected(UUID purchaseId, String reason) {
        update(purchaseId, Map.of("status", Status.REJECTED.name(), "reason", reason));
    }

    void forget(UUID purchaseId) {
        redis.delete(key(purchaseId));
    }

    Optional<Purchase> find(UUID purchaseId) {
        Map<Object, Object> fields = redis.opsForHash().entries(key(purchaseId));
        if (fields.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Purchase(purchaseId, UUID.fromString((String) fields.get("userId")),
                Status.valueOf((String) fields.get("status")), (String) fields.get("reason")));
    }

    /** Only touches purchases that still exist, so a late event cannot recreate an expired one. */
    private void update(UUID purchaseId, Map<String, String> fields) {
        String key = key(purchaseId);
        if (Boolean.TRUE.equals(redis.hasKey(key))) {
            redis.opsForHash().putAll(key, fields);
        }
    }

    private static String key(UUID purchaseId) {
        return "fs:purchase:" + purchaseId;
    }

    record Purchase(UUID id, UUID userId, Status status, String reason) {
    }

    enum Status {
        /** Admitted; waiting for the order service. */
        PENDING,
        /** The order exists; its id is the purchase id. */
        CONFIRMED,
        REJECTED
    }
}
