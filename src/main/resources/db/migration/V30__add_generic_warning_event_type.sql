INSERT INTO "event_types" ("event_type_id", "description", "status") VALUES
(46, 'Generell advarsel', 'Advarsel'),
(47, 'Generell feil', 'Feil')
ON CONFLICT DO NOTHING;
