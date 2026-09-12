CREATE TABLE orders (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL,
    deal_id          UUID         NOT NULL,
    deal_title       VARCHAR(120) NOT NULL,
    -- Money is NZD cents, GST-inclusive; prices are snapshotted at order time.
    unit_price_cents BIGINT       NOT NULL,
    quantity         INTEGER      NOT NULL CHECK (quantity > 0),
    shipping_cents   BIGINT       NOT NULL,
    total_cents      BIGINT       NOT NULL,
    gst_cents        BIGINT       NOT NULL,
    status           VARCHAR(20)  NOT NULL,
    cancel_reason    VARCHAR(20),
    recipient_name   VARCHAR(100) NOT NULL,
    phone            VARCHAR(20)  NOT NULL,
    address_line1    VARCHAR(200) NOT NULL,
    address_line2    VARCHAR(200),
    suburb           VARCHAR(100),
    city             VARCHAR(100) NOT NULL,
    postcode         VARCHAR(4)   NOT NULL,
    rural_delivery   BOOLEAN      NOT NULL,
    shipping_zone    VARCHAR(20)  NOT NULL,
    created_at       TIMESTAMPTZ  NOT NULL,
    expires_at       TIMESTAMPTZ  NOT NULL,
    updated_at       TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL
);

CREATE INDEX orders_user_created_idx ON orders (user_id, created_at DESC);

-- Remaining stock per deal, seeded from the catalog on first order and decremented atomically.
CREATE TABLE deal_inventory (
    deal_id   UUID PRIMARY KEY,
    remaining INTEGER NOT NULL CHECK (remaining >= 0)
);

-- Units each customer holds per deal (pending or paid), enforcing the per-customer limit.
CREATE TABLE purchase_counts (
    user_id  UUID    NOT NULL,
    deal_id  UUID    NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    PRIMARY KEY (user_id, deal_id)
);

-- Transactional outbox: messages are written with the business change and published afterwards.
CREATE TABLE outbox_messages (
    id            UUID PRIMARY KEY,
    exchange      VARCHAR(100) NOT NULL,
    routing_key   VARCHAR(200) NOT NULL,
    payload       TEXT         NOT NULL,
    expiration_ms BIGINT,
    created_at    TIMESTAMPTZ  NOT NULL,
    published_at  TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox_messages (created_at) WHERE published_at IS NULL;
