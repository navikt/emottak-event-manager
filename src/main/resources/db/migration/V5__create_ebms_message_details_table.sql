CREATE TABLE "ebms_message_details" (
    "request_id" UUID PRIMARY KEY,
    "cpa_id" VARCHAR(256) NOT NULL,
    "conversation_id" VARCHAR(256) NOT NULL,
    "message_id" VARCHAR(256) NOT NULL,
    "ref_to_message_id" VARCHAR(256),
    "from_party_id" VARCHAR(256) NOT NULL,
    "from_role" VARCHAR(256),
    "to_party_id" VARCHAR(256) NOT NULL,
    "to_role" VARCHAR(256),
    "service" VARCHAR(256) NOT NULL,
    "action" VARCHAR(256) NOT NULL,
    "ref_param" VARCHAR(256),
    "sender" VARCHAR(256),
    "sent_at" TIMESTAMP,
    "saved_at" TIMESTAMP
);

CREATE INDEX ON "ebms_message_details" ("saved_at");
