package io.github.derekdiliu.dealdrop.catalog.deal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deals")
public class Deal {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String description;

    @Column(name = "image_key")
    private String imageKey;

    @Column(name = "rrp_cents", nullable = false)
    private long rrpCents;

    @Column(name = "price_cents", nullable = false)
    private long priceCents;

    @Column(nullable = false)
    private int stock;

    @Column(name = "per_customer_limit", nullable = false)
    private int perCustomerLimit;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DealStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Deal() {
    }

    Deal(DealDetails details) {
        this.id = UUID.randomUUID();
        this.status = DealStatus.DRAFT;
        this.createdAt = Instant.now();
        apply(details);
    }

    void update(DealDetails details) {
        apply(details);
    }

    void publish() {
        this.status = DealStatus.PUBLISHED;
        this.updatedAt = Instant.now();
    }

    DealState stateAt(Instant now) {
        return DealState.of(startsAt, endsAt, now);
    }

    private void apply(DealDetails details) {
        this.title = details.title();
        this.description = details.description();
        this.imageKey = details.imageKey();
        this.rrpCents = details.rrpCents();
        this.priceCents = details.priceCents();
        this.stock = details.stock();
        this.perCustomerLimit = details.perCustomerLimit();
        this.startsAt = details.startsAt();
        this.endsAt = details.endsAt();
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getImageKey() {
        return imageKey;
    }

    public long getRrpCents() {
        return rrpCents;
    }

    public long getPriceCents() {
        return priceCents;
    }

    public int getStock() {
        return stock;
    }

    public int getPerCustomerLimit() {
        return perCustomerLimit;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public DealStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
