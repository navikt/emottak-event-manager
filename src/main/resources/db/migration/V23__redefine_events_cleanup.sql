CREATE OR REPLACE PROCEDURE events_cleanup(
    p_hours int,                    -- Angir hvor mange timer en event må være "foreldreløs" for at event'en kan slettes.
                                    -- 2 betyr "foreldreløse events opprettet for mer enn 2 timer siden slettes".
    p_job_name text,                -- Navn på batch-jobben, for å ha et unikt innslag i job_status-tabellen
    p_batch_size int DEFAULT 100000 -- Hvor mange events som skal slettes om gangen.
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_request_ids UUID[];
    v_hours_ago TIMESTAMP := NOW() - (p_hours || ' hours')::INTERVAL;
    v_deleted_count int;
    v_deleted_count_total BIGINT := 0;
    v_result_text text;
BEGIN
    INSERT INTO job_status (job_name) VALUES (p_job_name);
    COMMIT;
    RAISE NOTICE 'EVENTS CLEANUP: Started';

    SELECT ARRAY_AGG(request_id) INTO v_request_ids FROM ebms_message_details;
    v_result_text := FORMAT('Loaded %s request ids into memory', array_length(v_request_ids, 1));
    UPDATE job_status SET updated_at = NOW(), result_text = v_result_text WHERE job_name = p_job_name;
    RAISE NOTICE 'EVENTS CLEANUP: %', v_result_text;
    COMMIT;

    LOOP
        RAISE NOTICE 'EVENTS CLEANUP: Executing deletion from table events...';
        WITH to_delete AS (
            SELECT e.event_id
            FROM events e
            WHERE e.created_at < v_hours_ago
            AND e.request_id <> ALL(v_request_ids) -- <> er det samme som NOT IN, men er eksplisitt designet for arrays.
            LIMIT p_batch_size
        )
        DELETE FROM events WHERE event_id IN (SELECT event_id FROM to_delete);

        GET DIAGNOSTICS v_deleted_count = ROW_COUNT;
        RAISE NOTICE 'EVENTS CLEANUP: Deleted % rows from events.', v_deleted_count;
        IF v_deleted_count = 0 THEN
            RAISE NOTICE 'EVENTS CLEANUP: No more entries left - exiting the loop';
            EXIT;
        END IF;

        v_deleted_count_total := v_deleted_count_total + v_deleted_count;
        v_result_text := FORMAT('%s events deleted', v_deleted_count_total);
        UPDATE job_status SET updated_at = NOW(), result_text = v_result_text WHERE job_name = p_job_name;
        RAISE NOTICE 'EVENTS CLEANUP: %', v_result_text;

        COMMIT;
        RAISE NOTICE 'EVENTS CLEANUP: PERFORM pg_sleep(0.01)';
        PERFORM pg_sleep(0.01); -- Small pause to reduce lock contention
    END LOOP;

    UPDATE job_status SET completed_at = NOW() WHERE job_name = p_job_name;
    RAISE NOTICE 'EVENTS CLEANUP: Complete';
    COMMIT;
END;
$$;
