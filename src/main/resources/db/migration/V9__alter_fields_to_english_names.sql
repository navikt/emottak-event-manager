ALTER TABLE "ebms_message_details"
    RENAME COLUMN "sender" to "sender_name";

DROP INDEX "ebms_message_details_mottak_id_idx";

ALTER TABLE "ebms_message_details"
    RENAME COLUMN "mottak_id" to "readable_id";

CREATE INDEX "ebms_message_details_readable_id_idx" ON "ebms_message_details" ("readable_id");
