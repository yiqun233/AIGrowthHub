ALTER TABLE app_setting ADD COLUMN timezone varchar(100) NOT NULL DEFAULT '';
ALTER TABLE study_log ADD COLUMN activity_type varchar(20) NOT NULL DEFAULT 'LEARNING' CHECK(activity_type IN ('LEARNING','DEVELOPMENT','REST'));
ALTER TABLE study_log ADD CONSTRAINT rest_zero_minutes CHECK(activity_type<>'REST' OR duration_minutes=0);
