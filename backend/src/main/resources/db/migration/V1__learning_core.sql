CREATE TABLE learning_goal (
 id uuid PRIMARY KEY, title varchar(200) NOT NULL, description text NOT NULL DEFAULT '',
 start_date date, target_date date, priority integer NOT NULL CHECK(priority BETWEEN 1 AND 3),
 status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','ARCHIVED')),
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL,
 CHECK(target_date IS NULL OR start_date IS NULL OR target_date >= start_date)
);
CREATE TABLE learning_task (
 id uuid PRIMARY KEY, goal_id uuid REFERENCES learning_goal(id), title varchar(200) NOT NULL,
 detail text NOT NULL DEFAULT '', scheduled_date date NOT NULL,
 estimate_minutes integer NOT NULL CHECK(estimate_minutes BETWEEN 1 AND 1440),
 priority integer NOT NULL CHECK(priority BETWEEN 1 AND 3),
 status varchar(20) NOT NULL CHECK(status IN ('TODO','IN_PROGRESS','DONE','CANCELLED')),
 source varchar(20) NOT NULL DEFAULT 'MANUAL', acceptance_criteria text NOT NULL DEFAULT '', completed_at timestamptz,
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX idx_task_date_status ON learning_task(scheduled_date,status);
CREATE TABLE study_log (
 id uuid PRIMARY KEY, study_date date NOT NULL, content text NOT NULL,
 duration_minutes integer NOT NULL CHECK(duration_minutes BETWEEN 0 AND 1440),
 achievements text NOT NULL DEFAULT '', blockers text NOT NULL DEFAULT '',
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE INDEX idx_log_date ON study_log(study_date);
CREATE TABLE study_log_task (
 study_log_id uuid NOT NULL REFERENCES study_log(id), task_id uuid NOT NULL REFERENCES learning_task(id),
 PRIMARY KEY(study_log_id,task_id)
);
