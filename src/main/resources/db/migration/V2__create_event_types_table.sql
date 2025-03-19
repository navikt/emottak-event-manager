CREATE TABLE "event_types" (
    "event_type_id" NUMERIC(4,0) PRIMARY KEY,
    "description" VARCHAR(256) NOT NULL,
    "status" event_status NOT NULL
);
