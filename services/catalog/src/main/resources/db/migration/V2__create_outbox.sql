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
