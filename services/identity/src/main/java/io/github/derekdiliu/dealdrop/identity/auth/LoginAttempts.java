package io.github.derekdiliu.dealdrop.identity.auth;

import io.github.derekdiliu.dealdrop.identity.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Limits sign-in attempts per email, so a password can't be found by trying many. Attempts are counted
 * before the password is checked, so sending many at once gets no more through; signing in resets the
 * count. Unknown emails are counted the same way, so the limit doesn't reveal which accounts exist.
 *
 * There is no per-IP limit here: every sign-in arrives through the frontend's server, so all customers
 * would share one address. That belongs in front of the frontend.
 */
@Component
class LoginAttempts {

    private static final Logger log = LoggerFactory.getLogger(LoginAttempts.class);
    private static final RedisScript<Long> ATTEMPT =
            RedisScript.of(new ClassPathResource("scripts/login-attempt.lua"), Long.class);

    private final StringRedisTemplate redis;
    private final int maxAttempts;
    private final Duration window;

    LoginAttempts(StringRedisTemplate redis,
                  @Value("${dealdrop.login.max-attempts}") int maxAttempts,
                  @Value("${dealdrop.login.window}") Duration window) {
        this.redis = redis;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    /** Counts an attempt, or throws if this email has none left in the current window. */
    void record(String email) {
        Long retryAfterSeconds;
        try {
            retryAfterSeconds = redis.execute(ATTEMPT, List.of(key(email)), String.valueOf(maxAttempts),
                    String.valueOf(window.toSeconds()));
        } catch (DataAccessException e) {
            // Better to sign people in without the limit than to sign nobody in while Redis is down.
            log.warn("Couldn't count a sign-in attempt; allowing it", e);
            return;
        }
        if (retryAfterSeconds > 0) {
            throw new TooManyAttempts(Duration.ofSeconds(retryAfterSeconds));
        }
    }

    void reset(String email) {
        try {
            redis.delete(key(email));
        } catch (DataAccessException e) {
            log.warn("Couldn't reset sign-in attempts", e);
        }
    }

    private static String key(String email) {
        return "login:attempts:" + UserService.normalizeEmail(email);
    }

    static final class TooManyAttempts extends RuntimeException {

        private final Duration retryAfter;

        TooManyAttempts(Duration retryAfter) {
            super(null, null, false, false);
            this.retryAfter = retryAfter;
        }

        Duration retryAfter() {
            return retryAfter;
        }
    }
}
