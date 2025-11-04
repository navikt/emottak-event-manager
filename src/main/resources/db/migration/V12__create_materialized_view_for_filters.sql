-- Create the view
CREATE MATERIALIZED VIEW IF NOT EXISTS distict_roles_services_actions AS
SELECT string_agg(distinct from_role::text, ',') AS roles,
       string_agg(distinct service::text, ',') AS services,
       string_agg(distinct action::text, ',') AS actions,
       now() AS refreshed_at
FROM (SELECT "from_role", "service", "action" FROM "ebms_message_details") sub;

-- Add a unique index so CONCURRENTLY works
CREATE UNIQUE INDEX IF NOT EXISTS distict_roles_services_actions_uid_idx
    ON distict_roles_services_actions (refreshed_at);

-- Refresh the view with CONCURRENTLY:
-- REFRESH MATERIALIZED VIEW CONCURRENTLY distict_roles_server_actions;