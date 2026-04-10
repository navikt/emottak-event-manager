DROP PROCEDURE IF EXISTS delete_service_events(text, int);
DROP PROCEDURE IF EXISTS delete_service_events(text, text, int);

CREATE OR REPLACE PROCEDURE delete_service_events(
    p_service text,
    p_job_name text,
    p_batch_size int DEFAULT 10000
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_deleted_count int;
    v_total_deleted_events BIGINT := 0;
    v_total_deleted_conversations BIGINT := 0;
    v_total_deleted_message_details BIGINT := 0;
    v_request_ids UUID[];
    v_conversation_ids text[];
    v_conversation_status_table_exists BOOLEAN;
    v_result_text text;
BEGIN
    INSERT INTO job_status (job_name) VALUES (p_job_name);
    COMMIT;
    RAISE NOTICE 'DELETING %: started', p_service;

    -- Hopp over sletting hvis ebms_message_details ikke lenger inneholder service som skal slettes:
    IF NOT EXISTS (SELECT 1 FROM ebms_message_details WHERE service = p_service LIMIT 1) THEN
        v_result_text := FORMAT('No rows found for service %s, skipping deletion', p_service);
        UPDATE job_status
        SET completed_at = NOW(), updated_at = NOW(), result_text = v_result_text
        WHERE job_name = p_job_name;
        RAISE NOTICE 'DELETING %: %', p_service, v_result_text;
        COMMIT;
        RETURN;
    END IF;

    -- Sjekk om conversation_status-tabellen eksisterer:
    v_conversation_status_table_exists := to_regclass('public.conversation_status') IS NOT NULL;

    LOOP
        RAISE NOTICE 'DELETING %: Getting request_ids and conversation_ids from ebms_message_details, limited to %', p_service, p_batch_size;
        -- Hent relevante request_id'er og conversation_id'er som skal slettes:
        SELECT ARRAY_AGG(request_id), ARRAY_AGG(conversation_id)
        INTO v_request_ids, v_conversation_ids
        FROM (
             SELECT request_id, conversation_id
             FROM ebms_message_details
             WHERE service = p_service
             ORDER BY saved_at
             LIMIT p_batch_size
         ) sub;

        -- Avslutt loop hvis ingen flere rader igjen:
        IF v_request_ids IS NULL OR array_length(v_request_ids, 1) IS NULL THEN
            RAISE NOTICE 'DELETING %: No more entries left - exiting the loop', p_service;
            EXIT;
        END IF;

        -- Slett hendelser:
        RAISE NOTICE 'DELETING %: Executing deletion from table events...', p_service;
        DELETE FROM events WHERE request_id = ANY(v_request_ids);

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        RAISE NOTICE 'DELETING %: Deleted % rows from events.', p_service, v_deleted_count;
        v_total_deleted_events := v_total_deleted_events + v_deleted_count;

        -- Slett conversations:
        IF v_conversation_status_table_exists THEN
            RAISE NOTICE 'DELETING %: Executing deletion from table conversation_status...', p_service;
            DELETE FROM conversation_status WHERE conversation_id = ANY(v_conversation_ids);

            GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
            RAISE NOTICE 'DELETING %: Deleted % rows from conversation_status.', p_service, v_deleted_count;
            v_total_deleted_conversations := v_total_deleted_conversations + v_deleted_count;
        END IF;

        -- Og til slutt slett meldingsdetaljer:
        RAISE NOTICE 'DELETING %: Executing deletion from table ebms_message_details...', p_service;
        DELETE FROM ebms_message_details WHERE request_id = ANY(v_request_ids);

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        RAISE NOTICE 'DELETING %: Deleted % rows from ebms_message_details.', p_service, v_deleted_count;
        v_total_deleted_message_details := v_total_deleted_message_details + v_deleted_count;

        v_result_text := FORMAT('Deleted %s rows from ebms_message_details, %s rows from events, and %s rows from conversation_status',
                     v_total_deleted_message_details, v_total_deleted_events, v_total_deleted_conversations);

        UPDATE job_status
        SET updated_at = NOW(), result_text = v_result_text
        WHERE job_name = p_job_name;

        COMMIT;
        RAISE NOTICE 'DELETING %: PERFORM pg_sleep(0.01)', p_service;
        PERFORM pg_sleep(0.01); -- Small pause to reduce lock contention
    END LOOP;

    v_result_text := FORMAT('Deleted %s rows from ebms_message_details, %s rows from events, and %s rows from conversation_status',
                 v_total_deleted_message_details, v_total_deleted_events, v_total_deleted_conversations);

    UPDATE job_status
    SET completed_at = NOW(), result_text = v_result_text
    WHERE job_name = p_job_name;

    RAISE NOTICE 'DELETING % COMPLETE: %', p_service, v_result_text;
    COMMIT;
END;
$$;
