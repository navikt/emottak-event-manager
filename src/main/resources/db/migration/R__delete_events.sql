DO $$
DECLARE
    /*  Oppgavebeskrivelse er primærnøkkel som vil sørge for at påbegynt migreringsbatchjobb
        ikke kjøres på nytt ved redeploy, med mindre beskrivelsen endres.
        NB: Nais-redeploy vil trolig skje når en jobb tar for lang tid.
        Dermed vil neste jobb risikere å kjøre i parallell med den forrige, så det må man være klar over at kan skje.
    */
    v_job_name_HarBorgerEgenandelFritak text := 'delete_service_events(HarBorgerEgenandelFritak) 1';
    v_job_name_HarBorgerFrikort text := 'delete_service_events(HarBorgerFrikort) 1';
    v_job_name_events_cleanup text := 'events_cleanup 1';
BEGIN
    IF NOT EXISTS (SELECT 1 FROM job_status WHERE job_name = v_job_name_HarBorgerEgenandelFritak) THEN
        CALL delete_service_events('HarBorgerEgenandelFritak', v_job_name_HarBorgerEgenandelFritak, 10000);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM job_status WHERE job_name = v_job_name_HarBorgerFrikort) THEN
        CALL delete_service_events('HarBorgerFrikort', v_job_name_HarBorgerFrikort, 10000);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM job_status WHERE job_name = v_job_name_events_cleanup) THEN
        CALL events_cleanup(2, v_job_name_events_cleanup, 100000);
    END IF;
END $$;