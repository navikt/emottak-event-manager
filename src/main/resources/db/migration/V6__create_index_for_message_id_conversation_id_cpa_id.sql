CREATE INDEX secondary_ids
    ON ebms_message_details (message_id, conversation_id, cpa_id);
