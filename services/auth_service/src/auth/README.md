CREATE TABLE auth_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    username VARCHAR(64) UNIQUE NOT NULL,

    password_hash TEXT NOT NULL,

    account_id BIGINT NOT NULL
        REFERENCES accounts(id),

    roles TEXT[] NOT NULL
        DEFAULT ARRAY['CUSTOMER'],

    created_at TIMESTAMPTZ NOT NULL
        DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE refresh_tokens (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL
        REFERENCES auth_users(id)
        ON DELETE CASCADE,

    token_hash TEXT NOT NULL UNIQUE,

    expires_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    consumed_at TIMESTAMPTZ,

    revoked_at TIMESTAMPTZ
);