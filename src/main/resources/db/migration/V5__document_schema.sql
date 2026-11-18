CREATE SCHEMA IF NOT EXISTS document;

CREATE TABLE document.stored_files (
  id               UUID PRIMARY KEY,
  owner_id         UUID NOT NULL,
  storage_key      VARCHAR(300) UNIQUE NOT NULL,
  original_name_enc TEXT NOT NULL,
  mime             VARCHAR(100) NOT NULL,
  size             BIGINT NOT NULL CHECK (size BETWEEN 1 AND 10485760),
  sha256           VARCHAR(64) NOT NULL,
  scan_status      VARCHAR(16) NOT NULL CHECK (scan_status IN ('CLEAN', 'SKIPPED')),
  parse_status     VARCHAR(24) NOT NULL DEFAULT 'QUEUED',
  parse_job_id     UUID,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  version          BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX files_owner_idx ON document.stored_files (owner_id, created_at DESC);

CREATE TABLE document.parse_results (
  file_id                UUID PRIMARY KEY REFERENCES document.stored_files (id) ON DELETE CASCADE,
  text_enc               TEXT NOT NULL,
  parser_version         VARCHAR(30) NOT NULL,
  ocr_used               BOOLEAN NOT NULL DEFAULT false,
  reviewed_at            TIMESTAMPTZ,
  reviewed_structure_enc TEXT,
  created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
  version                BIGINT NOT NULL DEFAULT 0
);