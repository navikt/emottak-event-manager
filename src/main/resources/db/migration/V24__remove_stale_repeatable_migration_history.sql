-- R__delete_events.sql was removed from the codebase (logic moved to App.runSequentialDelayedTasks() in Kotlin).
-- This removes the stale entry so Flyway validation passes without ignoreMissingMigrations() going forward.
DELETE FROM flyway_schema_history WHERE script = 'R__delete_events.sql';
