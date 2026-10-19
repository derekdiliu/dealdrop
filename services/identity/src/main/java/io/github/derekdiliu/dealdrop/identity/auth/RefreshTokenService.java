package io.github.derekdiliu.dealdrop.identity.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

/**
 * Refresh tokens are random strings, stored only as SHA-256 hashes. Each use swaps the token for a new one
 * in the same family (one family per sign-in). A token used a second time must have been copied, so the
 * whole family is revoked and whoever holds it has to sign in again; the exception is a repeat within a few
 * seconds, which is a browser sending several requests at once when its access token runs out.
 */
@Service
class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final RefreshTokenRepository tokens;
    private final TransactionTemplate transaction;
    private final Duration ttl;
    private final Duration reuseGrace;

    RefreshTokenService(RefreshTokenRepository tokens, TransactionTemplate transaction,
                        @Value("${dealdrop.refresh-token.ttl}") Duration ttl,
                        @Value("${dealdrop.refresh-token.reuse-grace}") Duration reuseGrace) {
        this.tokens = tokens;
        this.transaction = transaction;
        this.ttl = ttl;
        this.reuseGrace = reuseGrace;
    }

    /** The first token of a new sign-in. */
    Issued startFamily(UUID userId) {
        return transaction.execute(status -> issue(userId, UUID.randomUUID(), Instant.now()));
    }

    /**
     * Swaps a token for its successor. Empty if the token is unknown, expired or revoked, or was already
     * used, in which case its family is revoked. That revocation commits even though the request fails.
     */
    Optional<Rotated> rotate(String rawToken) {
        return transaction.execute(status -> {
            Instant now = Instant.now();
            Optional<RefreshToken> found = tokens.findByTokenHash(hash(rawToken));
            if (found.isEmpty() || found.get().getRevokedAt() != null || !found.get().getExpiresAt().isAfter(now)) {
                return Optional.empty();
            }
            RefreshToken token = found.get();
            if (token.getUsedAt() != null && token.getUsedAt().isBefore(now.minus(reuseGrace))) {
                log.warn("Refresh token used twice; revoking sign-in {} of user {}", token.getFamilyId(),
                        token.getUserId());
                tokens.revokeFamily(token.getFamilyId(), now);
                return Optional.empty();
            }
            if (token.getUsedAt() == null) {
                token.markUsed(now);
            }
            return Optional.of(new Rotated(token.getUserId(), issue(token.getUserId(), token.getFamilyId(), now)));
        });
    }

    /** Signs out the sign-in this token belongs to. Unknown tokens are ignored. */
    void revoke(String rawToken) {
        transaction.executeWithoutResult(status -> tokens.findByTokenHash(hash(rawToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now())));
    }

    /** Expired tokens can't be used or reused, so there is nothing left to check them against. */
    @Scheduled(cron = "0 0 4 * * *", zone = "Pacific/Auckland")
    void deleteExpired() {
        Integer deleted = transaction.execute(status -> tokens.deleteExpired(Instant.now()));
        log.info("Deleted {} expired refresh tokens", deleted);
    }

    private Issued issue(UUID userId, UUID familyId, Instant now) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        tokens.save(new RefreshToken(hash(rawToken), userId, familyId, now, ttl));
        return new Issued(rawToken, ttl.toSeconds());
    }

    static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    record Issued(String token, long expiresIn) {
    }

    record Rotated(UUID userId, Issued next) {
    }
}
