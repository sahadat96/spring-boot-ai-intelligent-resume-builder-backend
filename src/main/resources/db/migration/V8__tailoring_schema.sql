CREATE SCHEMA IF NOT EXISTS tailoring;

CREATE TABLE tailoring.tailoring_sessions (
  id                  UUID PRIMARY KEY,
  user_id             UUID NOT NULL,
  source_resume_id    UUID NOT NULL,
  source_version_id   UUID NOT NULL,
  source_enc          TEXT NOT NULL,
  job_description_enc TEXT NOT NULL,
  source_url_enc      TEXT,
  candidate_enc       TEXT,
  warnings            JSONB NOT NULL DEFAULT '[]',
  result_resume_id    UUID NOT NULL,
  job_id              UUID NOT NULL,
  reservation_id      UUID NOT NULL,
  status              VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'READY', 'ACCEPTED', 'FAILED')),
  error_code          VARCHAR(64),
  score_before        INTEGER,
  score_after         INTEGER,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  version             BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX tailoring_owner_idx ON tailoring.tailoring_sessions (user_id, created_at DESC);
