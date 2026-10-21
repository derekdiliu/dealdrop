package io.github.derekdiliu.dealdrop.payment;

import io.github.derekdiliu.dealdrop.common.ApiErrors;
import io.github.derekdiliu.dealdrop.payment.Messaging.PaymentEvents;
import io.github.derekdiliu.dealdrop.payment.Payment.Status;
import io.github.derekdiliu.dealdrop.payment.messages.Messages.PaymentSucceeded;
import io.github.derekdiliu.dealdrop.payment.orders.OrderClient;
import io.github.derekdiliu.dealdrop.payment.orders.OrderClient.OrderSummary;
import io.github.derekdiliu.dealdrop.payment.stripe.PaymentProvider;
import io.github.derekdiliu.dealdrop.payment.stripe.PaymentProvider.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Stripe calls are made outside database transactions and are idempotent, so retrying any step after a
 * failure is safe.
 */
@Service
class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    // Don't start a payment the order may time out in the middle of.
    private static final Duration MIN_TIME_TO_PAY = Duration.ofMinutes(1);

    private final OrderClient orders;
    private final PaymentProvider provider;
    private final PaymentRepository payments;
    private final PaymentEvents events;

    PaymentService(OrderClient orders, PaymentProvider provider, PaymentRepository payments, PaymentEvents events) {
        this.orders = orders;
        this.provider = provider;
        this.payments = payments;
        this.events = events;
    }

    /** Returns what the browser needs to collect the payment, creating the PaymentIntent on first use. */
    StartedPayment start(UUID userId, String customerToken, UUID orderId) {
        OrderSummary order = orders.find(orderId, customerToken)
                .orElseThrow(() -> ApiErrors.notFound("Order not found."));
        if (!order.isAwaitingPayment()) {
            throw ApiErrors.conflict("This order isn't awaiting payment.");
        }
        if (order.expiresAt().isBefore(Instant.now().plus(MIN_TIME_TO_PAY))) {
            throw ApiErrors.conflict("This order is about to expire and can no longer be paid.");
        }

        Optional<Payment> existing = payments.findById(orderId);
        if (existing.isPresent()) {
            return resume(existing.get());
        }
        String currency = order.currency().toLowerCase(Locale.ROOT);
        Intent intent = provider.createIntent(orderId, userId, order.totalCents(), currency);
        try {
            payments.saveAndFlush(new Payment(orderId, userId, intent.id(), order.totalCents(), currency));
        } catch (DataIntegrityViolationException concurrentRequest) {
            // Another request for the same order got there first; Stripe returned it the same intent.
        }
        return new StartedPayment(orderId, intent.id(), intent.clientSecret(), order.totalCents(), currency);
    }

    /**
     * Stripe says the payment went through. The event is passed on before anything is recorded here, so if
     * this fails the webhook fails, Stripe retries, and nothing is lost; the order service ignores repeats.
     */
    void succeeded(UUID orderId, String paymentIntentId, long amountCents, String currency) {
        events.succeeded(new PaymentSucceeded(orderId, paymentIntentId, amountCents, currency));
        payments.findByPaymentIntentId(paymentIntentId)
                .filter(payment -> payment.getStatus() == Status.PENDING)
                .ifPresent(payment -> {
                    payment.moveTo(Status.SUCCEEDED);
                    payments.save(payment);
                });
    }

    /** Stops the customer paying for an order that no longer exists. */
    void orderCancelled(UUID orderId) {
        payments.findById(orderId)
                .filter(payment -> payment.getStatus() == Status.PENDING)
                .ifPresent(payment -> {
                    if (provider.cancel(payment.getPaymentIntentId())) {
                        payment.moveTo(Status.CANCELLED);
                        payments.save(payment);
                    }
                    // Otherwise the customer paid at the last moment; that payment will be rejected and refunded.
                });
    }

    void refund(UUID orderId, String paymentIntentId, String reason) {
        provider.refund(paymentIntentId, orderId);
        log.warn("Refunded payment {} for order {}: {}", paymentIntentId, orderId, reason);
        payments.findByPaymentIntentId(paymentIntentId).ifPresent(payment -> {
            payment.moveTo(Status.REFUNDED);
            payments.save(payment);
        });
    }

    private StartedPayment resume(Payment payment) {
        if (payment.getStatus() != Status.PENDING) {
            throw ApiErrors.conflict("This order has already been paid.");
        }
        return new StartedPayment(payment.getOrderId(), payment.getPaymentIntentId(),
                provider.clientSecret(payment.getPaymentIntentId()), payment.getAmountCents(), payment.getCurrency());
    }

    record StartedPayment(UUID orderId, String paymentIntentId, String clientSecret, long amountCents,
                          String currency) {
    }
}
