UPDATE "event_types"
SET "description" = 'Feil ved utsending via SMTP'
WHERE "event_type_id" = 4;

UPDATE "event_types"
SET "description" = 'Feil ved utsending via HTTP'
WHERE "event_type_id" = 8;

UPDATE "event_types"
SET "description" = 'Feil ved innsending til fagsystem'
WHERE "event_type_id" = 34;
