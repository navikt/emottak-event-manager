ALTER TABLE events
ALTER COLUMN event_data TYPE jsonb USING event_data::jsonb;

CREATE INDEX IF NOT EXISTS events_event_data_gin ON events USING GIN (event_data);

CREATE OR REPLACE PROCEDURE rename_sender_batches(batch_size int DEFAULT 20000)
LANGUAGE plpgsql
AS $$
DECLARE
rows_updated bigint;
BEGIN
  LOOP
WITH c AS (
      SELECT ctid
      FROM events
      WHERE event_data ? 'sender'
      LIMIT batch_size
      FOR UPDATE SKIP LOCKED
    )
UPDATE events e
SET event_data = e.event_data - 'sender'
    || jsonb_build_object('sender_name', e.event_data->'sender')
    FROM c
WHERE e.ctid = c.ctid;

GET DIAGNOSTICS rows_updated = ROW_COUNT;
EXIT WHEN rows_updated = 0;

    -- end current transaction; a new one starts automatically
COMMIT; -- or: COMMIT AND CHAIN;
END LOOP;
END$$;

