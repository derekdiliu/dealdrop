ALTER TABLE orders
    ADD COLUMN payment_intent_id VARCHAR(100),
    ADD COLUMN paid_at           TIMESTAMPTZ;
