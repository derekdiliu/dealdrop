package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.messages.PlaceOrder;
import io.github.derekdiliu.dealdrop.order.shipping.ShippingZone;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Status changes are made with conditional updates ({@link Inventory#cancelIfPending},
 * {@link OrderRepository#markPaidIfPending}), not through this entity, so that a concurrent cancellation
 * and payment cannot both succeed.
 */
@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "deal_id", nullable = false)
    private UUID dealId;

    @Column(name = "deal_title", nullable = false)
    private String dealTitle;

    @Column(name = "unit_price_cents", nullable = false)
    private long unitPriceCents;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "shipping_cents", nullable = false)
    private long shippingCents;

    @Column(name = "total_cents", nullable = false)
    private long totalCents;

    @Column(name = "gst_cents", nullable = false)
    private long gstCents;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason")
    private CancelReason cancelReason;

    @Embedded
    private ShippingAddress shippingAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "shipping_zone", nullable = false)
    private ShippingZone shippingZone;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "payment_intent_id")
    private String paymentIntentId;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Version
    private long version;

    protected Order() {
    }

    Order(PlaceOrder request, ShippingZone zone, long shippingCents, Duration paymentTimeout) {
        this.id = request.orderId();
        this.userId = request.userId();
        this.dealId = request.dealId();
        this.dealTitle = request.dealTitle();
        this.unitPriceCents = request.unitPriceCents();
        this.quantity = request.quantity();
        this.shippingCents = shippingCents;
        this.totalCents = request.unitPriceCents() * request.quantity() + shippingCents;
        this.gstCents = Gst.includedIn(totalCents);
        this.status = OrderStatus.PENDING_PAYMENT;
        this.shippingAddress = request.shippingAddress();
        this.shippingZone = zone;
        this.createdAt = Instant.now();
        this.expiresAt = createdAt.plus(paymentTimeout);
        this.updatedAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getDealId() {
        return dealId;
    }

    public String getDealTitle() {
        return dealTitle;
    }

    public long getUnitPriceCents() {
        return unitPriceCents;
    }

    public int getQuantity() {
        return quantity;
    }

    public long getShippingCents() {
        return shippingCents;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public long getGstCents() {
        return gstCents;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public CancelReason getCancelReason() {
        return cancelReason;
    }

    public ShippingAddress getShippingAddress() {
        return shippingAddress;
    }

    public ShippingZone getShippingZone() {
        return shippingZone;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public String getPaymentIntentId() {
        return paymentIntentId;
    }

    public Instant getPaidAt() {
        return paidAt;
    }
}
