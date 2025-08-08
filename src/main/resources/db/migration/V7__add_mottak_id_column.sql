ALTER TABLE "ebms_message_details"
    ADD COLUMN "mottak_id" VARCHAR(256);

CREATE INDEX "ebms_message_details_mottak_id_idx" ON "ebms_message_details" ("mottak_id");
