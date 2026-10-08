package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.payment.PaymentService.StartedPayment;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /**
     * Starts (or resumes) paying for an order. The client secret is passed to Stripe.js in the browser,
     * which collects the card details; they never reach this service.
     */
    @PostMapping("/api/payment/orders/{orderId}/payment-intent")
    StartedPayment start(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID orderId) {
        return payments.start(UUID.fromString(jwt.getSubject()), jwt.getTokenValue(), orderId);
    }
}
