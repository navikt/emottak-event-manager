-- "Melding sendt via SMTP" og "Melding sendt til fagsystem" settes til status 'Informasjon' (tidligere 'Ferdigbehandlet'):
-- UPDATE "event_types"
-- SET "status" = 'Informasjon'
-- WHERE "event_type_id" IN (3, 33);

-- Nye hendelser for ferdigstilling/rekjøring av en melding:
INSERT INTO "event_types" ("event_type_id", "description", "status") VALUES
(43, 'Melding ferdigbehandlet', 'Ferdigbehandlet'),
(44, 'Melding rekjøres', 'Informasjon');
