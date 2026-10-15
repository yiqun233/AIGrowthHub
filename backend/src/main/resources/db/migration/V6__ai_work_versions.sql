ALTER TABLE ai_work ADD COLUMN prompt_version varchar(30) NOT NULL DEFAULT 'work-v1';
ALTER TABLE ai_work ADD COLUMN schema_version varchar(30) NOT NULL DEFAULT 'work-v1';
