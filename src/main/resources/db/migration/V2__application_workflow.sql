CREATE TABLE IF NOT EXISTS career_application_workflows (
    application_id          varchar(64) PRIMARY KEY,
    ats                     varchar(64) NOT NULL,
    origin                  varchar(512) NOT NULL,
    page_url                varchar(2000) NOT NULL,
    job_title               varchar(500),
    state                   varchar(40) NOT NULL,
    detected_fields         integer NOT NULL DEFAULT 0,
    resolved_fields         integer NOT NULL DEFAULT 0,
    review_fields           integer NOT NULL DEFAULT 0,
    approved_fields         integer NOT NULL DEFAULT 0,
    applied_fields          integer NOT NULL DEFAULT 0,
    resume_attached         boolean NOT NULL DEFAULT false,
    detected_action         varchar(80),
    success_url             varchar(2000),
    external_application_id varchar(255),
    submitted_at            timestamp with time zone,
    confirmed_at            timestamp with time zone,
    record_version          bigint NOT NULL DEFAULT 0,
    created_at              timestamp with time zone NOT NULL,
    updated_at              timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_career_application_state_updated
    ON career_application_workflows (state, updated_at DESC);
CREATE INDEX IF NOT EXISTS idx_career_application_origin_updated
    ON career_application_workflows (origin, updated_at DESC);

CREATE TABLE IF NOT EXISTS career_application_workflow_events (
    id              varchar(64) PRIMARY KEY,
    application_id  varchar(64) NOT NULL REFERENCES career_application_workflows(application_id),
    from_state      varchar(40),
    to_state        varchar(40) NOT NULL,
    event_type      varchar(80) NOT NULL,
    metadata_json   text NOT NULL,
    occurred_at     timestamp with time zone NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_career_application_event_timeline
    ON career_application_workflow_events (application_id, occurred_at ASC);
