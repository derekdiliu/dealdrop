-- Only a SHA-256 hash of each refresh token is stored, so a copy of this table can't be used to sign in.
-- Each sign-in starts a family; using a token replaces it with a new one in the same family.
CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY,
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    family_id  UUID        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    -- Set when the token is swapped for its successor; a later use means it was copied.
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ
);

CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);
