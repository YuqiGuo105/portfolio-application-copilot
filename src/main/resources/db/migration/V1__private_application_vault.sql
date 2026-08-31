CREATE TABLE IF NOT EXISTS career_private_resumes (
    id                  varchar(64) PRIMARY KEY,
    label               varchar(160) NOT NULL,
    content_ciphertext  text NOT NULL,
    file_name           varchar(255),
    mime_type           varchar(120),
    source_url          text,
    active              boolean NOT NULL DEFAULT false,
    record_version      bigint NOT NULL DEFAULT 0,
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    deleted_at          timestamp with time zone
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_career_private_resume_active
    ON career_private_resumes (active) WHERE active = true AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_career_private_resume_updated
    ON career_private_resumes (deleted_at, updated_at DESC);

CREATE TABLE IF NOT EXISTS career_private_profile (
    owner_id            varchar(64) PRIMARY KEY,
    answers_ciphertext  text NOT NULL,
    record_version      bigint NOT NULL DEFAULT 0,
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    deleted_at          timestamp with time zone
);

CREATE TABLE IF NOT EXISTS career_vault_audit_log (
    id                  varchar(64) PRIMARY KEY,
    actor               varchar(255) NOT NULL,
    action              varchar(80) NOT NULL,
    resource_type       varchar(80) NOT NULL,
    resource_id         varchar(160) NOT NULL,
    metadata_json       text NOT NULL,
    occurred_at         timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_career_vault_audit_resource
    ON career_vault_audit_log (resource_type, resource_id, occurred_at DESC);
