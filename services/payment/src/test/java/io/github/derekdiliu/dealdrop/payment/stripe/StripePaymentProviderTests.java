package io.github.derekdiliu.dealdrop.payment.stripe;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Runs the real Stripe client against stripe-mock, Stripe's official API simulator, to check requests
 * are well-formed for the current API. stripe-mock is stateless, so it returns canned objects.
 */
@Testcontainers
class StripePaymentProviderTests {

    @Container
    private static final GenericContainer<?> STRIPE_MOCK =
            new GenericContainer<>("stripe/stripe-mock:v0.206.0").withExposedPorts(12111);

    private static StripePaymentProvider provider;

    @BeforeAll
    static void connect() {
        String apiBase = "http://" + STRIPE_MOCK.getHost() + ":" + STRIPE_MOCK.getMappedPort(12111);
        provider = new StripePaymentProvider(new StripeProperties("sk_test_123", "whsec_unused", apiBase));
    }

    @Test
    void createsIntentWithClientSecret() {
        PaymentProvider.Intent intent = provider.createIntent(UUID.randomUUID(), UUID.randomUUID(), 16697, "nzd");

        assertThat(intent.id()).startsWith("pi_");
        assertThat(intent.clientSecret()).isNotBlank();
        assertThat(provider.clientSecret(intent.id())).isNotBlank();
    }

    @Test
    void cancelsAndRefunds() {
        assertThat(provider.cancel("pi_123")).isTrue();
        assertThatNoException().isThrownBy(() -> provider.refund("pi_123", UUID.randomUUID()));
    }
}
