CREATE TABLE deals (
    id                 UUID PRIMARY KEY,
    title              VARCHAR(120)  NOT NULL,
    description        VARCHAR(4000) NOT NULL,
    image_key          VARCHAR(200),
    -- Prices are NZD cents, GST-inclusive.
    rrp_cents          BIGINT        NOT NULL CHECK (rrp_cents > 0),
    price_cents        BIGINT        NOT NULL CHECK (price_cents > 0 AND price_cents <= rrp_cents),
    stock              INTEGER       NOT NULL CHECK (stock > 0),
    per_customer_limit INTEGER       NOT NULL CHECK (per_customer_limit > 0 AND per_customer_limit <= stock),
    starts_at          TIMESTAMPTZ   NOT NULL,
    ends_at            TIMESTAMPTZ   NOT NULL CHECK (ends_at > starts_at),
    status             VARCHAR(20)   NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL,
    version            BIGINT        NOT NULL
);

-- Storefront listing: published deals that have not ended, in start order.
CREATE INDEX deals_published_ends_at_idx ON deals (ends_at, starts_at) WHERE status = 'PUBLISHED';
