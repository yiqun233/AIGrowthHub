CREATE TABLE learning_stage (
 id uuid PRIMARY KEY,
 title varchar(200) NOT NULL,
 desired_outcome text NOT NULL,
 acceptance_criteria text NOT NULL,
 start_date date NOT NULL,
 target_date date,
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','PAUSED','CLOSED')),
 outcome varchar(20) CHECK(outcome IN ('ACHIEVED','PARTIAL','RESCOPED','ABANDONED')),
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 CHECK ((status='CLOSED') = (outcome IS NOT NULL))
);
CREATE INDEX idx_learning_stage_status ON learning_stage(status, start_date DESC);
CREATE UNIQUE INDEX idx_learning_stage_single_active ON learning_stage(status) WHERE status='ACTIVE';

CREATE TABLE learning_stage_review (
 id uuid PRIMARY KEY,
 stage_id uuid NOT NULL UNIQUE REFERENCES learning_stage(id),
 source_snapshot jsonb NOT NULL,
 source_hash varchar(64) NOT NULL,
 ai_draft jsonb,
 final_content text NOT NULL DEFAULT '',
 status varchar(20) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED')),
 confirmed_at timestamptz,
 version bigint NOT NULL DEFAULT 0,
 created_at timestamptz NOT NULL,
 updated_at timestamptz NOT NULL,
 CHECK ((status='CONFIRMED') = (confirmed_at IS NOT NULL))
);

ALTER TABLE learning_task ADD COLUMN learning_stage_id uuid REFERENCES learning_stage(id);
ALTER TABLE study_log ADD COLUMN learning_stage_id uuid REFERENCES learning_stage(id);
ALTER TABLE weekly_plan ADD COLUMN learning_stage_id uuid REFERENCES learning_stage(id);
ALTER TABLE achievement ADD COLUMN learning_stage_id uuid REFERENCES learning_stage(id);
CREATE INDEX idx_task_learning_stage ON learning_task(learning_stage_id, scheduled_date);
CREATE INDEX idx_log_learning_stage ON study_log(learning_stage_id, study_date);
CREATE INDEX idx_weekly_plan_learning_stage ON weekly_plan(learning_stage_id, week_start);
CREATE INDEX idx_achievement_learning_stage ON achievement(learning_stage_id, achievement_date);
