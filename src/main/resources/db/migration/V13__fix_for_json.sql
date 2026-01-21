CREATE OR REPLACE PROCEDURE rename_events_data_json_key(
    p_old_key         text,          -- key that exists now
    p_new_key         text,          -- key you want to create
    p_batch_size      int DEFAULT 20000
)
LANGUAGE plpgsql
AS $$
DECLARE
    v_rows_updated bigint;
BEGIN
    LOOP
        WITH cte AS (
            SELECT ctid
            FROM events
            WHERE event_data ? p_old_key
            LIMIT p_batch_size
            FOR UPDATE SKIP LOCKED
        )
        UPDATE events e
        SET event_data = e.event_data - p_old_key
            || jsonb_build_object(p_new_key, e.event_data -> p_old_key)
        FROM cte
        WHERE e.ctid = cte.ctid;

        GET DIAGNOSTICS v_rows_updated = ROW_COUNT;

        EXIT WHEN v_rows_updated = 0;

        COMMIT;
    END LOOP;
END$$;

