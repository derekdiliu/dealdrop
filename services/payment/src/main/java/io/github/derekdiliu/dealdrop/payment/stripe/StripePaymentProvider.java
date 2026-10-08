package io.github.derekdiliu.dealdrop.payment.stripe;

import com.stripe.StripeClient;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.net.RequestOptions;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
class StripePaymentProvider implements PaymentProvider {

    private static final int TIMEOUT_MS = 10_000;

    private final StripeClient stripe;

    StripePaymentProvider(StripeProperties properties) {
        StripeClient.StripeClientBuilder builder = StripeClient.builder()
                .setApiKey(properties.secretKey())
                .setConnectTimeout(TIMEOUT_MS)
                .setReadTimeout(TIMEOUT_MS)
                // Safe because every write below carries an idempotency key.
                .setMaxNetworkRetries(2);
        if (properties.apiBase() != null && !properties.apiBase().isBlank()) {
            builder.setApiBase(properties.apiBase());
        }
        this.stripe = builder.build();
    }

    @Override
    public Intent createIntent(UUID orderId, UUID userId, long amountCents, String currency) {
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountCents)
                .setCurrency(currency)
                // Card, Apple Pay, Google Pay and whatever else is enabled in the Stripe dashboard.
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .build())
                .putMetadata("order_id", orderId.toString())
                .putMetadata("user_id", userId.toString())
                .build();
        try {
            PaymentIntent intent = stripe.v1().paymentIntents()
                    .create(params, idempotencyKey("payment-intent-" + orderId));
            return new Intent(intent.getId(), intent.getClientSecret());
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not create payment for order " + orderId, e);
        }
    }

    @Override
    public String clientSecret(String paymentIntentId) {
        try {
            return stripe.v1().paymentIntents().retrieve(paymentIntentId).getClientSecret();
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not retrieve payment " + paymentIntentId, e);
        }
    }

    @Override
    public boolean cancel(String paymentIntentId) {
        try {
            stripe.v1().paymentIntents().cancel(paymentIntentId);
            return true;
        } catch (InvalidRequestException e) {
            if ("payment_intent_unexpected_state".equals(e.getCode())) {
                return false;
            }
            throw new PaymentProviderException("Could not cancel payment " + paymentIntentId, e);
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not cancel payment " + paymentIntentId, e);
        }
    }

    @Override
    public void refund(String paymentIntentId, UUID orderId) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .putMetadata("order_id", orderId.toString())
                .build();
        try {
            stripe.v1().refunds().create(params, idempotencyKey("refund-" + paymentIntentId));
        } catch (StripeException e) {
            throw new PaymentProviderException("Could not refund payment " + paymentIntentId, e);
        }
    }

    private static RequestOptions idempotencyKey(String key) {
        return RequestOptions.builder().setIdempotencyKey(key).build();
    }
}
