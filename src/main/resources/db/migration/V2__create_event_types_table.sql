CREATE TABLE "event_types" (
    "event_type_id" SERIAL PRIMARY KEY,
    "description" VARCHAR(256) NOT NULL,
    "status" event_status NOT NULL
);
