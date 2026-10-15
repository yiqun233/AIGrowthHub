CREATE TABLE app_setting (
 id integer PRIMARY KEY CHECK(id=1), default_provider varchar(20) NOT NULL DEFAULT 'OPENAI',
 default_model varchar(100) NOT NULL DEFAULT '', daily_minutes integer NOT NULL DEFAULT 60 CHECK(daily_minutes BETWEEN 0 AND 1440),
 preferences varchar(2000) NOT NULL DEFAULT '', version bigint NOT NULL DEFAULT 0
);
INSERT INTO app_setting(id) VALUES(1);
CREATE TABLE ai_analysis (
 id uuid PRIMARY KEY, source_log_id uuid NOT NULL REFERENCES study_log(id), target_date date NOT NULL,
 budget_minutes integer NOT NULL CHECK(budget_minutes BETWEEN 0 AND 1440), feedback varchar(2000) NOT NULL,
 provider varchar(20) NOT NULL, model varchar(100) NOT NULL, request_key varchar(128) NOT NULL UNIQUE,
 request_hash varchar(64) NOT NULL, input_snapshot jsonb NOT NULL, context_hash varchar(64) NOT NULL,
 output_json jsonb, status varchar(20) NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 prompt_version varchar(30) NOT NULL, schema_version varchar(30) NOT NULL,
 attempt_count integer NOT NULL DEFAULT 0, duration_ms bigint, input_tokens bigint, output_tokens bigint,
 error_code varchar(50), error_message varchar(300), started_at timestamptz, finished_at timestamptz,
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX idx_analysis_one_active ON ai_analysis((1)) WHERE status IN ('PENDING','RUNNING');
CREATE INDEX idx_analysis_created ON ai_analysis(created_at DESC);
CREATE TABLE plan_suggestion (
 id uuid PRIMARY KEY, analysis_id uuid NOT NULL UNIQUE REFERENCES ai_analysis(id), target_date date NOT NULL,
 proposed_tasks_json jsonb NOT NULL, status varchar(20) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','REJECTED','SUPERSEDED')),
 confirmed_at timestamptz, version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE suggestion_task (
 suggestion_id uuid NOT NULL REFERENCES plan_suggestion(id), item_id uuid NOT NULL,
 task_id uuid NOT NULL REFERENCES learning_task(id), PRIMARY KEY(suggestion_id,item_id)
);
