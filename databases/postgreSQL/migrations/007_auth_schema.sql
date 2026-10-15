-- 007: auth schema (logins and refresh tokens), owned by the auth service.
-- account_id is the trade.accounts id the login belongs to; it is carried in the tokens the auth
-- service issues and is deliberately not a foreign key, so the two schemas stay independent.

BEGIN;

CREATE SCHEMA IF NOT EXISTS auth;

SET LOCAL search_path TO auth, public;

CREATE TABLE IF NOT EXISTS auth_users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(64) UNIQUE NOT NULL,
    password_hash   TEXT NOT NULL,
    account_id      BIGINT NOT NULL,
    roles           TEXT[] NOT NULL DEFAULT ARRAY['CUSTOMER'],
    created_at      TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users (username);
CREATE INDEX IF NOT EXISTS idx_auth_users_account_id ON auth_users (account_id);

CREATE TABLE IF NOT EXISTS refresh_tokens (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       UUID NOT NULL REFERENCES auth_users(id) ON DELETE CASCADE,
    token_hash    TEXT NOT NULL UNIQUE,
    expires_at    TIMESTAMPTZ NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    consumed_at   TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id ON refresh_tokens (user_id);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at ON refresh_tokens (expires_at);

COMMIT;
