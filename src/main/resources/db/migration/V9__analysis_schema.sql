CREATE SCHEMA IF NOT EXISTS analysis;

CREATE TABLE analysis.resume_uploads (
  id            UUID PRIMARY KEY,
  owner_id      UUID NOT NULL,
  file_name     VARCHAR(255) NOT NULL,
  mime_type     VARCHAR(100) NOT NULL,
  file_size     BIGINT NOT NULL CHECK (file_size BETWEEN 1 AND 10485760),
  sha256        VARCHAR(64) NOT NULL,
  content_bytes BYTEA NOT NULL,
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  version       BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX resume_uploads_owner_idx ON analysis.resume_uploads (owner_id, created_at DESC);

CREATE TABLE analysis.resume_analyses (
  id               UUID PRIMARY KEY,
  owner_id         UUID NOT NULL,
  file_id          UUID REFERENCES analysis.resume_uploads (id) ON DELETE SET NULL,
  file_name        VARCHAR(255) NOT NULL,
  file_size        BIGINT NOT NULL,
  mime_type        VARCHAR(100) NOT NULL,
  target_role      VARCHAR(150),
  job_description  TEXT,
  status           VARCHAR(20) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED', 'PROCESSING', 'COMPLETED', 'FAILED')),
  error_message    VARCHAR(500),
  overall_score    INTEGER CHECK (overall_score BETWEEN 0 AND 100),
  ats_score        INTEGER CHECK (ats_score BETWEEN 0 AND 100),
  content_score    INTEGER CHECK (content_score BETWEEN 0 AND 100),
  formatting_score INTEGER CHECK (formatting_score BETWEEN 0 AND 100),
  jd_match_score   INTEGER CHECK (jd_match_score BETWEEN 0 AND 100),
  strengths        JSONB NOT NULL DEFAULT '[]',
  areas_to_improve JSONB NOT NULL DEFAULT '[]',
  keywords         JSONB NOT NULL DEFAULT '[]',
  missing_keywords JSONB NOT NULL DEFAULT '[]',
  score_details    JSONB NOT NULL DEFAULT '{}',
  extracted_text   TEXT,
  sections         JSONB NOT NULL DEFAULT '{}',
  ocr_used         BOOLEAN NOT NULL DEFAULT false,
  parser_version   VARCHAR(30) NOT NULL DEFAULT '2.0.0',
  ai_enhanced      BOOLEAN NOT NULL DEFAULT false,
  ai_provider      VARCHAR(30),
  ai_model         VARCHAR(100),
  ai_suggestions   JSONB,
  processing_ms    BIGINT NOT NULL DEFAULT 0,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX analysis_owner_idx ON analysis.resume_analyses (owner_id, created_at DESC);
CREATE INDEX analysis_file_idx ON analysis.resume_analyses (file_id);
CREATE INDEX analysis_status_idx ON analysis.resume_analyses (status, created_at);
CREATE INDEX analysis_scores_idx ON analysis.resume_analyses (overall_score, ats_score);
