CREATE TABLE IF NOT EXISTS career_resume_assets (
    id                  varchar(64) PRIMARY KEY,
    owner_id            varchar(64) NOT NULL,
    display_name        varchar(255) NOT NULL,
    storage_bucket      varchar(120) NOT NULL,
    storage_object_key  text NOT NULL,
    mime_type           varchar(120) NOT NULL,
    size_bytes          bigint,
    sha256              varchar(64),
    status              varchar(32) NOT NULL,
    active              boolean NOT NULL DEFAULT false,
    record_version      bigint NOT NULL DEFAULT 0,
    created_at          timestamp with time zone NOT NULL,
    updated_at          timestamp with time zone NOT NULL,
    activated_at        timestamp with time zone,
    deleted_at          timestamp with time zone,
    CONSTRAINT ck_career_resume_asset_status CHECK
        (status IN ('UPLOADING', 'READY', 'ACTIVE', 'ARCHIVED', 'REJECTED')),
    CONSTRAINT ck_career_resume_asset_size CHECK (size_bytes IS NULL OR size_bytes > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_career_resume_asset_object
    ON career_resume_assets (storage_bucket, storage_object_key);
CREATE UNIQUE INDEX IF NOT EXISTS uq_career_resume_asset_active
    ON career_resume_assets (owner_id) WHERE active = true AND deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_career_resume_asset_owner_updated
    ON career_resume_assets (owner_id, deleted_at, updated_at DESC);
