CREATE SCHEMA IF NOT EXISTS ai;

CREATE TABLE ai.ai_requests (
  id              UUID PRIMARY KEY,
  owner_id        UUID NOT NULL,
  reference_id    UUID NOT NULL,
  capability      VARCHAR(50) NOT NULL,
  request_hash    VARCHAR(64) NOT NULL,
  prompt_version  VARCHAR(16) NOT NULL,
  model           VARCHAR(100) NOT NULL,
  status          VARCHAR(16) NOT NULL CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED')),
  claim_token     UUID,
  requested_day   DATE NOT NULL,
  reserved_tokens BIGINT NOT NULL,
  input_tokens    BIGINT NOT NULL DEFAULT 0,
  output_tokens   BIGINT NOT NULL DEFAULT 0,
  latency_ms      BIGINT NOT NULL DEFAULT 0,
  output_enc      TEXT,
  error_code      VARCHAR(64),
  locked_until    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  version         BIGINT NOT NULL DEFAULT 0,
  UNIQUE (owner_id, reference_id, capability)
);

CREATE INDEX ai_budget_idx ON ai.ai_requests (requested_day, status, owner_id);

CREATE TABLE ai.ai_usage_attempts (
  id            UUID PRIMARY KEY,
  request_id    UUID NOT NULL REFERENCES ai.ai_requests (id) ON DELETE CASCADE,
  owner_id      UUID NOT NULL,
  requested_day DATE NOT NULL,
  input_tokens  BIGINT NOT NULL DEFAULT 0,
  output_tokens BIGINT NOT NULL DEFAULT 0,
  estimated_usd NUMERIC(18, 8),
  completed_at  TIMESTAMPTZ,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX ai_attempt_budget_idx ON ai.ai_usage_attempts (requested_day, owner_id);