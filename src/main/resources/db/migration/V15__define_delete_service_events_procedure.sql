CREATE OR REPLACE PROCEDURE delete_service_events(
    p_service text,
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
BEGIN
    -- Hopp over sletting hvis ebms_message_details ikke lenger inneholder service som skal slettes:
    IF NOT EXISTS (SELECT 1 FROM ebms_message_details m WHERE m.service = p_service LIMIT 1) THEN
        RAISE NOTICE 'No rows found for service %, skipping deletion', p_service;
        RETURN;
    END IF;

    -- Sjekk om conversation_status-tabellen eksisterer:
    v_conversation_status_table_exists := to_regclass('public.conversation_status') IS NOT NULL;

    LOOP
        -- Hent relevante request_id'er og conversation_id'er som skal slettes:
        SELECT ARRAY_AGG(m.request_id), ARRAY_AGG(m.conversation_id)
        INTO v_request_ids, v_conversation_ids
        FROM (
             SELECT m.request_id, m.conversation_id
             FROM ebms_message_details m
             WHERE m.service = p_service
             LIMIT p_batch_size
         ) sub;

        -- Avslutt loop hvis ingen flere rader igjen:
        IF v_request_ids IS NULL OR array_length(v_request_ids, 1) IS NULL THEN
            EXIT;
        END IF;

        -- Slett hendelser:
        DELETE FROM events e
        WHERE e.request_id = ANY(v_request_ids);

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        v_total_deleted_events := v_total_deleted_events + v_deleted_count;

        -- Slett conversations:
        IF v_conversation_status_table_exists THEN
            DELETE FROM conversation_status c
            WHERE c.conversation_id = ANY(v_conversation_ids);

            GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
            v_total_deleted_conversations := v_total_deleted_conversations + v_deleted_count;
        END IF;

        -- Og til slutt slett meldingsdetaljer:
        DELETE FROM ebms_message_details m
        WHERE m.request_id = ANY(v_request_ids);

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        v_total_deleted_message_details := v_total_deleted_message_details + v_deleted_count;

        COMMIT;
        PERFORM pg_sleep(0.01); -- Small pause to reduce lock contention
    END LOOP;

    RAISE NOTICE 'DELETING %: Deleted % rows from ebms_message_details, % rows from events, and % rows from conversation_status.',
                 p_service, v_total_deleted_message_details, v_total_deleted_events, v_total_deleted_conversations;
END;
$$;
