package io.github.derekdiliu.dealdrop.payment.stripe;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param apiBase only set in tests, to point the client at stripe-mock
 */
@Validated
@ConfigurationProperties("dealdrop.stripe")
public record StripeProperties(@NotBlank String secretKey, @NotBlank String webhookSecret, String apiBase) {
}
