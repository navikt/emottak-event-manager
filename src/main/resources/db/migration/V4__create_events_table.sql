CREATE TABLE "events" (
    "event_id" UUID PRIMARY KEY,
    "event_type_id" NUMERIC(4,0) NOT NULL,
    "request_id" UUID NOT NULL,
    "content_id" VARCHAR(256),
    "message_id" VARCHAR(256) NOT NULL,
    "event_data" JSON,
    "created_at" TIMESTAMP DEFAULT 'now()'
);

CREATE INDEX ON "events" ("request_id");
