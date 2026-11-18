CREATE SCHEMA IF NOT EXISTS platform;

CREATE TABLE platform.async_jobs (
  id           UUID PRIMARY KEY,
  owner_id     UUID NOT NULL,
  type         VARCHAR(64) NOT NULL,
  payload_enc  TEXT NOT NULL,
  status       VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED')),
  priority     INTEGER NOT NULL DEFAULT 0 CHECK (priority BETWEEN 0 AND 10),
  attempts     INTEGER NOT NULL DEFAULT 0,
  run_after    TIMESTAMPTZ NOT NULL DEFAULT now(),
  locked_by    UUID,
  locked_until TIMESTAMPTZ,
  result_enc   TEXT,
  last_error   VARCHAR(64),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX async_jobs_claim_idx ON platform.async_jobs (run_after) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX async_jobs_priority_idx ON platform.async_jobs (priority DESC, run_after) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX async_jobs_owner_idx ON platform.async_jobs (owner_id, created_at DESC);

CREATE TABLE platform.outbox_events (
  id           UUID PRIMARY KEY,
  owner_id     UUID NOT NULL,
  type         VARCHAR(80) NOT NULL,
  payload_enc  TEXT NOT NULL,
  published_at TIMESTAMPTZ,
  attempts     INTEGER NOT NULL DEFAULT 0,
  run_after    TIMESTAMPTZ NOT NULL DEFAULT now(),
  locked_by    UUID,
  locked_until TIMESTAMPTZ,
  dead_at      TIMESTAMPTZ,
  last_error   VARCHAR(64),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX outbox_pending_idx ON platform.outbox_events (run_after) WHERE published_at IS NULL AND dead_at IS NULL;

CREATE TABLE platform.event_receipts (
  event_id   UUID NOT NULL,
  consumer   VARCHAR(100) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, consumer)
);

CREATE TABLE platform.idempotency_keys (
  owner_id     UUID NOT NULL,
  scope        VARCHAR(120) NOT NULL,
  key          VARCHAR(100) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  response_enc TEXT NOT NULL,
  expires_at   TIMESTAMPTZ NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (owner_id, scope, key)
);

CREATE TABLE platform.audit_events (
  id         UUID PRIMARY KEY,
  owner_id   UUID,
  type       VARCHAR(80) NOT NULL,
  trace_id   VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  details    JSONB NOT NULL DEFAULT '{}'
);

CREATE TABLE platform.deletion_audit (
  id           UUID PRIMARY KEY,
  requested_at TIMESTAMPTZ NOT NULL,
  completed_at TIMESTAMPTZ,
  status       VARCHAR(16) NOT NULL,
  error_code   VARCHAR(64),
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE platform.blocked_owners (
  owner_id   UUID PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE platform.erasure_tombstones (
  owner_hash   VARCHAR(64) PRIMARY KEY,
  audit_id     UUID NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
