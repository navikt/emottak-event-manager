CREATE INDEX "duplicate_check_idx" ON "ebms_message_details" ("message_id", "conversation_id", "cpa_id");

CREATE INDEX "events_created_at_idx" ON "events" ("created_at");
