package io.github.derekdiliu.dealdrop.flashsale;

import io.github.derekdiliu.dealdrop.flashsale.catalog.CatalogDeal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Admits purchases against stock held in Redis, so a sale's burst of requests is decided in memory and
 * only admitted purchases go on to the database. Postgres (in the order service) still has the final say:
 * if Redis and the database ever disagree, the database rejects and Redis is corrected.
 *
 * Keys share a {dealId} hash tag so each script's keys live on the same node in Redis Cluster.
 */
@Component
class StockGate {

    private static final RedisScript<Long> RESERVE =
            RedisScript.of(new ClassPathResource("scripts/reserve.lua"), Long.class);
    private static final RedisScript<Long> RELEASE =
            RedisScript.of(new ClassPathResource("scripts/release.lua"), Long.class);
    private static final Duration KEY_LIFETIME_AFTER_SALE = Duration.ofDays(1);
    // Must outlive any redelivery of the same order event.
    private static final Duration RELEASE_MARKER_LIFETIME = Duration.ofDays(7);
    // Only needs to outlive cached deal details; after that the catalog itself reports the deal as gone.
    private static final Duration WITHDRAWN_MARKER_LIFETIME = Duration.ofDays(1);

    private final StringRedisTemplate redis;
    private final int attemptsPerWindow;
    private final Duration attemptWindow;

    StockGate(StringRedisTemplate redis,
              @Value("${dealdrop.flash-sale.rate-limit}") int attemptsPerWindow,
              @Value("${dealdrop.flash-sale.rate-window}") Duration attemptWindow) {
        this.redis = redis;
        this.attemptsPerWindow = attemptsPerWindow;
        this.attemptWindow = attemptWindow;
    }

    Outcome reserve(CatalogDeal deal, UUID userId, int quantity) {
        Long result = redis.execute(RESERVE,
                List.of(stockKey(deal.id()), heldKey(deal.id()), "fs:{" + deal.id() + "}:attempts:" + userId,
                        withdrawnKey(deal.id())),
                String.valueOf(deal.stock()), userId.toString(), String.valueOf(quantity),
                String.valueOf(deal.perCustomerLimit()), String.valueOf(attemptsPerWindow),
                String.valueOf(attemptWindow.toSeconds()), String.valueOf(keyLifetime(deal).toSeconds()));
        return switch (result.intValue()) {
            case -1 -> Outcome.SOLD_OUT;
            case -2 -> Outcome.LIMIT_REACHED;
            case -3 -> Outcome.TOO_MANY_ATTEMPTS;
            case -4 -> Outcome.WITHDRAWN;
            default -> Outcome.RESERVED;
        };
    }

    /**
     * Returns the customer's allowance and, if {@code returnStock}, the units. Idempotent per order, so a
     * redelivered event never gives stock back twice.
     */
    void release(UUID orderId, UUID dealId, UUID userId, int quantity, boolean returnStock) {
        redis.execute(RELEASE,
                List.of(stockKey(dealId), heldKey(dealId), "fs:{" + dealId + "}:released:" + orderId),
                userId.toString(), String.valueOf(quantity), returnStock ? "1" : "0",
                String.valueOf(RELEASE_MARKER_LIFETIME.toSeconds()));
    }

    /** The database says there is nothing left; stop admitting purchases until stock is given back. */
    void markSoldOut(UUID dealId) {
        redis.opsForValue().setIfPresent(stockKey(dealId), "0");
    }

    /**
     * The deal is off sale for good. A marker rather than zero stock, because cancelling its orders gives
     * stock back. Shared by every instance, so none keeps selling from deal details it has cached.
     */
    void markWithdrawn(UUID dealId) {
        redis.opsForValue().set(withdrawnKey(dealId), "1", WITHDRAWN_MARKER_LIFETIME);
    }

    /** Empty until the first purchase attempt seeds the deal's stock. */
    Optional<Integer> remaining(UUID dealId) {
        return Optional.ofNullable(redis.opsForValue().get(stockKey(dealId))).map(Integer::valueOf);
    }

    private static Duration keyLifetime(CatalogDeal deal) {
        Duration untilEnd = Duration.between(Instant.now(), deal.endsAt());
        return (untilEnd.isNegative() ? Duration.ZERO : untilEnd).plus(KEY_LIFETIME_AFTER_SALE);
    }

    private static String stockKey(UUID dealId) {
        return "fs:{" + dealId + "}:stock";
    }

    private static String heldKey(UUID dealId) {
        return "fs:{" + dealId + "}:held";
    }

    private static String withdrawnKey(UUID dealId) {
        return "fs:{" + dealId + "}:withdrawn";
    }

    enum Outcome {
        RESERVED,
        SOLD_OUT,
        LIMIT_REACHED,
        TOO_MANY_ATTEMPTS,
        WITHDRAWN
    }
}
