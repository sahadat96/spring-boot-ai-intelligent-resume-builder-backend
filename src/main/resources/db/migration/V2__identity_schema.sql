CREATE SCHEMA IF NOT EXISTS identity;

CREATE TABLE identity.users (
  id                UUID PRIMARY KEY,
  email             VARCHAR(254) NOT NULL UNIQUE CHECK (email = lower(email)),
  role              VARCHAR(16) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
  email_verified_at TIMESTAMPTZ,
  status            VARCHAR(24) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'PENDING_DELETE')),
  created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
  version           BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.credentials (
  user_id       UUID PRIMARY KEY REFERENCES identity.users (id) ON DELETE CASCADE,
  password_hash VARCHAR(100) NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.session_families (
  id               UUID PRIMARY KEY,
  user_id          UUID NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
  revoked_at       TIMESTAMPTZ,
  expires_at       TIMESTAMPTZ NOT NULL,
  device_label     VARCHAR(150),
  ip_hash          VARCHAR(64),
  user_agent_hash  VARCHAR(64),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX family_user_idx ON identity.session_families (user_id);

CREATE TABLE identity.sessions (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
  family_id  UUID NOT NULL REFERENCES identity.session_families (id) ON DELETE CASCADE,
  token_hash VARCHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  rotated_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.email_tokens (
  id          UUID PRIMARY KEY,
  user_id     UUID NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
  purpose     VARCHAR(16) NOT NULL CHECK (purpose IN ('VERIFY', 'RESET')),
  token_hash  VARCHAR(64) NOT NULL UNIQUE,
  expires_at  TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  version     BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.mfa_secrets (
  user_id      UUID PRIMARY KEY REFERENCES identity.users (id) ON DELETE CASCADE,
  secret_enc   TEXT NOT NULL,
  confirmed_at TIMESTAMPTZ,
  last_counter BIGINT NOT NULL DEFAULT -1,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE identity.oauth_accounts (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL REFERENCES identity.users (id) ON DELETE CASCADE,
  provider   VARCHAR(20) NOT NULL,
  subject    VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0,
  UNIQUE (provider, subject)
);

CREATE TABLE identity.oauth_challenges (
  token_hash  VARCHAR(64) PRIMARY KEY,
  expires_at  TIMESTAMPTZ NOT NULL,
  consumed_at TIMESTAMPTZ,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
