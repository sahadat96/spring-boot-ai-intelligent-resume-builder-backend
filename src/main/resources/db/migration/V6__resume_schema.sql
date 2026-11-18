CREATE SCHEMA IF NOT EXISTS resume;

CREATE TABLE resume.resume_templates (
  code         VARCHAR(50) PRIMARY KEY,
  name         VARCHAR(100) NOT NULL,
  badge        VARCHAR(100),
  supports_ats BOOLEAN NOT NULL DEFAULT true,
  active       BOOLEAN NOT NULL DEFAULT true,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
  version      BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE resume.resumes (
  id                 UUID PRIMARY KEY,
  user_id            UUID NOT NULL,
  title              VARCHAR(150) NOT NULL,
  type               VARCHAR(16) NOT NULL CHECK (type IN ('SCRATCH', 'UPLOADED', 'TAILORED')),
  status             VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  template_code      VARCHAR(50) NOT NULL REFERENCES resume.resume_templates (code),
  current_version_id UUID NOT NULL,
  current_content    JSONB NOT NULL,
  schema_version     INTEGER NOT NULL DEFAULT 1,
  deleted_at         TIMESTAMPTZ,
  created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
  version            BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX resumes_owner_idx ON resume.resumes (user_id, updated_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX resumes_skills_idx ON resume.resumes USING gin ((current_content -> 'skills'));
CREATE INDEX resume_trash_purge_idx ON resume.resumes (deleted_at) WHERE deleted_at IS NOT NULL;

CREATE TABLE resume.resume_versions (
  id             UUID PRIMARY KEY,
  resume_id      UUID NOT NULL REFERENCES resume.resumes (id) ON DELETE CASCADE,
  number         INTEGER NOT NULL,
  content        JSONB NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1,
  reason         VARCHAR(20) NOT NULL,
  created_by     UUID NOT NULL,
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (resume_id, number)
);

CREATE OR REPLACE FUNCTION resume.reject_version_update()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
  RAISE EXCEPTION 'Resume versions are immutable';
END;
$$;

CREATE TRIGGER immutable_resume_version
BEFORE UPDATE ON resume.resume_versions
FOR EACH ROW
EXECUTE FUNCTION resume.reject_version_update();

CREATE TABLE resume.resume_exports (
  id             UUID PRIMARY KEY,
  owner_id       UUID NOT NULL,
  resume_id      UUID NOT NULL REFERENCES resume.resumes (id) ON DELETE CASCADE,
  version_id     UUID NOT NULL REFERENCES resume.resume_versions (id),
  template_code  VARCHAR(50) NOT NULL,
  format         VARCHAR(8) NOT NULL,
  file_id        UUID NOT NULL,
  job_id         UUID NOT NULL,
  reservation_id UUID NOT NULL,
  status         VARCHAR(16) NOT NULL DEFAULT 'QUEUED',
  error_code     VARCHAR(64),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
  version        BIGINT NOT NULL DEFAULT 0,
  UNIQUE (version_id, template_code, format)
);
