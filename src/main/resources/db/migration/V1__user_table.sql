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
