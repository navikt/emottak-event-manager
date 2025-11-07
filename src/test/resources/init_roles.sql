CREATE ROLE "emottak-event-manager-db-user" WITH LOGIN PASSWORD 'app_pass';
GRANT CONNECT ON DATABASE postgres TO "emottak-event-manager-db-user";
GRANT USAGE ON SCHEMA public TO "emottak-event-manager-db-user";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO "emottak-event-manager-db-user";
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "emottak-event-manager-db-user";