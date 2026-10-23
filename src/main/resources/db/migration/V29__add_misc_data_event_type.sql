INSERT INTO "event_types" ("event_type_id", "description", "status") VALUES
(45, 'Generell hendelse', 'Informasjon')
ON CONFLICT DO NOTHING;
