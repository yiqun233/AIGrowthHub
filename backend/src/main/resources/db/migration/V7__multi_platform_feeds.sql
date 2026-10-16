ALTER TABLE feed_source
    ADD COLUMN platform_id varchar(30) NOT NULL DEFAULT 'release',
    ADD COLUMN adapter_type varchar(20) NOT NULL DEFAULT 'rsshub',
    ADD COLUMN profile_url varchar(2000),
    ADD COLUMN enabled boolean NOT NULL DEFAULT true,
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now();

UPDATE feed_source SET enabled = false WHERE id IN ('spring-boot', 'pgvector');

CREATE INDEX idx_feed_source_enabled ON feed_source(enabled, platform_id);
CREATE UNIQUE INDEX uq_feed_subscription_profile ON feed_source(platform_id, lower(profile_url)) WHERE profile_url IS NOT NULL AND enabled=true;

CREATE TABLE feed_source_secret (
    source_id varchar(30) PRIMARY KEY REFERENCES feed_source(id),
    encrypted_cookie text NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
