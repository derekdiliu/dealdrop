package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.payment.Payment.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/**
 * Back-office view of an order's payment; admin role required (see SecurityConfig).
 */
@RestController
class AdminPaymentController {

    private final PaymentRepository payments;

    AdminPaymentController(PaymentRepository payments) {
        this.payments = payments;
    }

    /** 404 until the customer opens the payment page, which is when the PaymentIntent is created. */
    @GetMapping("/api/payment/admin/payments/{orderId}")
    AdminPaymentResponse get(@PathVariable UUID orderId) {
        return payments.findById(orderId)
                .map(AdminPaymentResponse::from)
                .orElseThrow(() -> ApiErrors.notFound("No payment has been started for this order."));
    }

    record AdminPaymentResponse(
            UUID orderId,
            String paymentIntentId,
            long amountCents,
            String currency,
            Status status,
            Instant createdAt,
            Instant updatedAt) {

        static AdminPaymentResponse from(Payment payment) {
            return new AdminPaymentResponse(payment.getOrderId(), payment.getPaymentIntentId(),
                    payment.getAmountCents(), payment.getCurrency(), payment.getStatus(), payment.getCreatedAt(),
                    payment.getUpdatedAt());
        }
    }
}
