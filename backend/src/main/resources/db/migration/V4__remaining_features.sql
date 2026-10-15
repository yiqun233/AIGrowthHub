ALTER TABLE learning_task ADD COLUMN weekly_plan_id uuid REFERENCES weekly_plan(id);
CREATE INDEX idx_task_weekly_plan ON learning_task(weekly_plan_id);
CREATE FUNCTION check_task_week() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.weekly_plan_id IS NOT NULL AND NOT EXISTS(SELECT 1 FROM weekly_plan p WHERE p.id=NEW.weekly_plan_id AND NEW.scheduled_date BETWEEN p.week_start AND p.week_start+6) THEN
  RAISE EXCEPTION 'Task date outside weekly plan' USING ERRCODE='23514';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER task_week_guard BEFORE INSERT OR UPDATE ON learning_task FOR EACH ROW EXECUTE FUNCTION check_task_week();
CREATE TABLE achievement (
 id uuid PRIMARY KEY,title varchar(200) NOT NULL,description text NOT NULL,url varchar(2000) NOT NULL,
 kind varchar(30) NOT NULL,achievement_date date NOT NULL,goal_id uuid REFERENCES learning_goal(id),task_id uuid REFERENCES learning_task(id),
 archived boolean NOT NULL DEFAULT false,version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL
);
CREATE INDEX idx_achievement_date ON achievement(achievement_date DESC);
CREATE TABLE feed_source (
 id varchar(30) PRIMARY KEY,label varchar(200) NOT NULL,url varchar(2000) NOT NULL,
 last_attempt_at timestamptz,last_success_at timestamptz,error_code varchar(50),item_count integer NOT NULL DEFAULT 0
);
INSERT INTO feed_source(id,label,url) VALUES
 ('spring-boot','Spring Boot Releases','https://github.com/spring-projects/spring-boot/releases.atom'),
 ('pgvector','pgvector Releases','https://github.com/pgvector/pgvector/releases.atom');
CREATE TABLE feed_item (
 id uuid PRIMARY KEY,source_id varchar(30) NOT NULL REFERENCES feed_source(id),external_id varchar(2000) NOT NULL,
 url varchar(2000) NOT NULL UNIQUE,title varchar(500) NOT NULL,content text NOT NULL,published_at timestamptz,
 favorite boolean NOT NULL DEFAULT false,fingerprint varchar(64) NOT NULL,
 version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL,UNIQUE(source_id,external_id)
);
CREATE INDEX idx_feed_published ON feed_item(published_at DESC);
CREATE TABLE feed_candidate (
 id uuid PRIMARY KEY,item_id uuid NOT NULL UNIQUE REFERENCES feed_item(id),title varchar(200) NOT NULL,
 detail text NOT NULL,acceptance_criteria text NOT NULL,estimate_minutes integer NOT NULL CHECK(estimate_minutes BETWEEN 1 AND 1440),
 scheduled_date date NOT NULL,status varchar(20) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED','REJECTED')),
 task_id uuid REFERENCES learning_task(id),version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL
);
CREATE TABLE ai_work (
 id uuid PRIMARY KEY,kind varchar(30) NOT NULL,target_id uuid NOT NULL,provider varchar(20) NOT NULL,model varchar(100) NOT NULL,
 request_key varchar(128) NOT NULL UNIQUE,request_hash varchar(64) NOT NULL,input_snapshot jsonb NOT NULL,source_hash varchar(64) NOT NULL,
 output_json jsonb,status varchar(20) NOT NULL CHECK(status IN ('PENDING','RUNNING','SUCCEEDED','FAILED')),
 attempt_count integer NOT NULL DEFAULT 0,input_tokens bigint,output_tokens bigint,duration_ms bigint,
 error_code varchar(50),error_message varchar(300),started_at timestamptz,finished_at timestamptz,
 version bigint NOT NULL DEFAULT 0,created_at timestamptz NOT NULL,updated_at timestamptz NOT NULL
);
CREATE UNIQUE INDEX idx_ai_work_active ON ai_work((1)) WHERE status IN ('PENDING','RUNNING');
CREATE INDEX idx_ai_work_created ON ai_work(created_at DESC);

ALTER TABLE weekly_review ADD COLUMN ai_draft jsonb;
