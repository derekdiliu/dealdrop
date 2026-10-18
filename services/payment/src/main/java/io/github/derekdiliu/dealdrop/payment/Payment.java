package io.github.derekdiliu.dealdrop.payment;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "payment_intent_id", nullable = false)
    private String paymentIntentId;

    @Column(name = "amount_cents", nullable = false)
    private long amountCents;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Payment() {
    }

    Payment(UUID orderId, UUID userId, String paymentIntentId, long amountCents, String currency) {
        this.orderId = orderId;
        this.userId = userId;
        this.paymentIntentId = paymentIntentId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = Status.PENDING;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    void moveTo(Status status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public enum Status {
        /** Created; the customer has not paid yet (or a card was declined and they can try again). */
        PENDING,
        SUCCEEDED,
        /** The order was cancelled before the customer paid. */
        CANCELLED,
        /** Paid, but the order could not accept it, so the money was returned. */
        REFUNDED
    }
}
