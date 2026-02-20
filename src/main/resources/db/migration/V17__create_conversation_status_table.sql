CREATE TABLE "conversation_status" (
    "conversation_id" VARCHAR(256) PRIMARY KEY,
    "created_at" TIMESTAMP DEFAULT now(),
    "latest_status" event_status DEFAULT 'Informasjon',
    "status_at" TIMESTAMP DEFAULT now()
);

CREATE INDEX ON "conversation_status" ("conversation_id");
