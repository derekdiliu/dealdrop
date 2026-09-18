package io.github.derekdiliu.dealdrop.order;

import io.github.derekdiliu.dealdrop.order.catalog.CatalogDeal;
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
 * Status changes are made with conditional SQL updates in {@link Inventory}, not through this entity,
 * so that concurrent cancellations and payments cannot both succeed.
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

    @Version
    private long version;

    protected Order() {
    }

    Order(UUID userId, CatalogDeal deal, int quantity, ShippingAddress shippingAddress, ShippingZone zone,
          long shippingCents, Duration paymentTimeout) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.dealId = deal.id();
        this.dealTitle = deal.title();
        this.unitPriceCents = deal.priceCents();
        this.quantity = quantity;
        this.shippingCents = shippingCents;
        this.totalCents = deal.priceCents() * quantity + shippingCents;
        this.gstCents = Gst.includedIn(totalCents);
        this.status = OrderStatus.PENDING_PAYMENT;
        this.shippingAddress = shippingAddress;
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
}
