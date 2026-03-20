CREATE TABLE IF NOT EXISTS "job_status" (
    "job_name" VARCHAR(256) PRIMARY KEY,
    "started_at" TIMESTAMP DEFAULT NOW() NOT NULL,
    "updated_at" TIMESTAMP,
    "completed_at" TIMESTAMP,
    "result_text" VARCHAR(256)
);

CREATE INDEX IF NOT EXISTS "job_status_job_name_idx" ON "job_status" ("job_name");
