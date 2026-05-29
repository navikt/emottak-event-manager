CREATE TABLE "distinct_roles_services_actions" (
    "type" VARCHAR(10) NOT NULL,
    "value" TEXT NOT NULL,
    PRIMARY KEY ("type", "value")
);

-- Migrate data from the old distict_roles_services_actions table:
INSERT INTO "distinct_roles_services_actions" ("type", "value")
SELECT DISTINCT 'role', unnest(string_to_array(roles, ','))
FROM "distict_roles_services_actions"
WHERE roles IS NOT NULL AND roles != ''
UNION
SELECT DISTINCT 'service', unnest(string_to_array(services, ','))
FROM "distict_roles_services_actions"
WHERE services IS NOT NULL AND services != ''
UNION
SELECT DISTINCT 'action', unnest(string_to_array(actions, ','))
FROM "distict_roles_services_actions"
WHERE actions IS NOT NULL AND actions != ''
ON CONFLICT DO NOTHING;

-- Delete the old distict_roles_services_actions table (typo in distinct):
DROP TABLE "distict_roles_services_actions";
