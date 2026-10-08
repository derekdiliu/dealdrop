-- One Stripe PaymentIntent per order; the order id is also the idempotency key sent to Stripe.
CREATE TABLE payments (
    order_id          UUID PRIMARY KEY,
    user_id           UUID         NOT NULL,
    payment_intent_id VARCHAR(100) NOT NULL UNIQUE,
    amount_cents      BIGINT       NOT NULL,
    currency          VARCHAR(3)   NOT NULL,
    status            VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);
