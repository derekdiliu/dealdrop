package io.github.derekdiliu.dealdrop.payment;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.net.Webhook;
import io.github.derekdiliu.dealdrop.payment.stripe.StripeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Receives Stripe's notifications. Public, so every request's signature is checked against the endpoint
 * secret before it is trusted. Any failure after that returns an error, and Stripe retries the delivery.
 */
@RestController
class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    private final PaymentService payments;
    private final String webhookSecret;
    private final JsonMapper json;

    StripeWebhookController(PaymentService payments, StripeProperties properties, JsonMapper json) {
        this.payments = payments;
        this.webhookSecret = properties.webhookSecret();
        this.json = json;
    }

    @PostMapping("/api/payment/webhooks/stripe")
    ResponseEntity<Void> receive(@RequestBody String payload,
                                 @RequestHeader(name = "Stripe-Signature", required = false) String signature) {
        if (signature == null) {
            return ResponseEntity.badRequest().build();
        }
        try {
            Webhook.constructEvent(payload, signature, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Rejected webhook with an invalid signature");
            return ResponseEntity.badRequest().build();
        }

        // Read fields from the raw payload rather than the SDK's typed objects, so a difference between the
        // account's API version and the SDK's cannot make an event unreadable.
        JsonNode event = json.readTree(payload);
        JsonNode intent = event.path("data").path("object");
        String type = event.path("type").asString();
        if ("payment_intent.succeeded".equals(type)) {
            String orderId = intent.path("metadata").path("order_id").asString(null);
            if (orderId == null) {
                log.warn("Ignoring payment {} with no order id", intent.path("id").asString());
            } else {
                payments.succeeded(UUID.fromString(orderId), intent.path("id").asString(),
                        intent.path("amount_received").asLong(), intent.path("currency").asString());
            }
        }
        return ResponseEntity.ok().build();
    }
}
