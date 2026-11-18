CREATE SCHEMA IF NOT EXISTS billing;

CREATE TABLE billing.plans (
  code             VARCHAR(16) PRIMARY KEY,
  name             VARCHAR(100) NOT NULL,
  currency         VARCHAR(3) NOT NULL,
  price_monthly    INTEGER NOT NULL,
  price_six_month  INTEGER NOT NULL,
  limits           JSONB NOT NULL,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  version          BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.customers (
  user_id            UUID PRIMARY KEY,
  stripe_customer_id VARCHAR(100) UNIQUE NOT NULL,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  version            BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.subscriptions (
  user_id              UUID PRIMARY KEY,
  plan_code            VARCHAR(16) NOT NULL REFERENCES billing.plans (code),
  stripe_sub_id        VARCHAR(100) UNIQUE NOT NULL,
  status               VARCHAR(30) NOT NULL,
  current_period_end   TIMESTAMPTZ NOT NULL,
  cancel_at_period_end BOOLEAN NOT NULL DEFAULT false,
  created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
  version              BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.usage_counters (
  user_id    UUID NOT NULL,
  feature    VARCHAR(40) NOT NULL,
  period     VARCHAR(10) NOT NULL,
  used       INTEGER NOT NULL DEFAULT 0 CHECK (used >= 0),
  reserved   INTEGER NOT NULL DEFAULT 0 CHECK (reserved >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, feature, period)
);

CREATE TABLE billing.usage_reservations (
  id         UUID PRIMARY KEY,
  user_id    UUID NOT NULL,
  feature    VARCHAR(40) NOT NULL,
  period     VARCHAR(10) NOT NULL,
  status     VARCHAR(16) NOT NULL CHECK (status IN ('RESERVED', 'CONSUMED', 'RELEASED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  version    BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.stripe_events (
  id           VARCHAR(100) PRIMARY KEY,
  type         VARCHAR(100) NOT NULL,
  processed_at TIMESTAMPTZ,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE billing.checkout_leases (
  owner_id   UUID PRIMARY KEY,
  job_id     UUID NOT NULL,
  price      VARCHAR(100) NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE billing.subscription_sync (
  stripe_sub_id VARCHAR(100) PRIMARY KEY,
  requested     BIGINT NOT NULL DEFAULT 0,
  applied       BIGINT NOT NULL DEFAULT 0
);