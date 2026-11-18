CREATE SCHEMA IF NOT EXISTS account;

CREATE TABLE account.user_profiles (
  user_id         UUID PRIMARY KEY,
  first_name      VARCHAR(100) NOT NULL DEFAULT '',
  last_name       VARCHAR(100) NOT NULL DEFAULT '',
  headline        VARCHAR(200) NOT NULL DEFAULT '',
  about           VARCHAR(4000) NOT NULL DEFAULT '',
  country         VARCHAR(100) NOT NULL DEFAULT '',
  visa_status_enc TEXT,
  ai_consent_at   TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE account.notification_preferences (
  user_id         UUID PRIMARY KEY,
  weekly_tips     BOOLEAN NOT NULL DEFAULT true,
  job_alerts      BOOLEAN NOT NULL DEFAULT true,
  product_updates BOOLEAN NOT NULL DEFAULT false,
  account_billing BOOLEAN NOT NULL DEFAULT true,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE account.deletion_requests (
  id            UUID PRIMARY KEY,
  user_id       UUID NOT NULL UNIQUE,
  audit_id      UUID NOT NULL,
  execute_after TIMESTAMPTZ NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);