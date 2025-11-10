CREATE TABLE "distict_roles_services_actions" (
    "id" NUMERIC(1,0) PRIMARY KEY,
    "roles" TEXT NOT NULL,
    "services" TEXT NOT NULL,
    "actions" TEXT NOT NULL,
    "refreshed_at" TIMESTAMP DEFAULT now()
);