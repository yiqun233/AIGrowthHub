CREATE TABLE weekly_plan (
 id uuid PRIMARY KEY, week_start date NOT NULL UNIQUE CHECK(extract(isodow FROM week_start)=1),
 theme varchar(200) NOT NULL, budget_minutes integer NOT NULL CHECK(budget_minutes BETWEEN 0 AND 10080),
 notes text NOT NULL DEFAULT '', status varchar(20) NOT NULL CHECK(status IN ('ACTIVE','ARCHIVED')),
 version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
CREATE TABLE weekly_review (
 id uuid PRIMARY KEY, week_start date NOT NULL UNIQUE CHECK(extract(isodow FROM week_start)=1),
 source_snapshot jsonb NOT NULL, source_hash varchar(64) NOT NULL,
 final_content text NOT NULL DEFAULT '', status varchar(20) NOT NULL CHECK(status IN ('DRAFT','CONFIRMED')),
 confirmed_at timestamptz, version bigint NOT NULL DEFAULT 0, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL
);
