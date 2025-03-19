INSERT INTO "event_types" ("event_type_id", "description", "status") VALUES
(1, 'Melding mottatt via SMTP', 'Informasjon'),
(2, 'Feil ved mottak av melding via SMTP', 'Feil'),
(3, 'Melding sendt via SMTP', 'Ferdigbehandlet'),
(4, 'Feil ved utsending melding via SMTP', 'Feil'),
(5, 'Melding mottatt via HTTP', 'Informasjon'),
(6, 'Feil ved mottak av melding via HTTP', 'Feil'),
(7, 'Melding sendt via HTTP', 'Ferdigbehandlet'),
(8, 'Feil ved utsending melding via HTTP', 'Feil'),
(9, 'Payload lagret i database', 'Informasjon'),
(10, 'Feil ved lagring payload i database', 'Feil'),
(11, 'Payload lest fra database', 'Informasjon'),
(12, 'Feil ved lesing payload fra database', 'Feil'),
(13, 'Payload mottatt via HTTP', 'Informasjon'),
(14, 'Feil ved mottak av payload via HTTP', 'Feil'),
(15, 'Melding lagt på kø', 'Informasjon'),
(16, 'Feil ved lagring melding på kø', 'Feil'),
(17, 'Melding lest fra kø', 'Informasjon'),
(18, 'Feil ved lesing melding fra kø', 'Feil'),
(19, 'Melding lagret i juridisk logg', 'Informasjon'),
(20, 'Feil ved lagring melding i juridisk logg', 'Feil'),
(21, 'Melding kryptert', 'Informasjon'),
(22, 'Kryptering av melding mislykket', 'Feil'),
(23, 'Melding dekryptert', 'Informasjon'),
(24, 'Dekryptering av melding mislykket', 'Feil'),
(25, 'Melding komprimert', 'Informasjon'),
(26, 'Komprimering av melding mislykket', 'Feil'),
(27, 'Melding dekomprimert', 'Informasjon'),
(28, 'Dekomprimering av melding mislykket', 'Feil'),
(29, 'Signatursjekk vellykket', 'Informasjon'),
(30, 'Signatursjekk mislykket', 'Feil'),
(31, 'OCSP-sjekk vellykket', 'Informasjon'),
(32, 'OCSP-sjekk mislykket', 'Feil'),
(33, 'Melding sendt til fagsystem', 'Ferdigbehandlet'),
(34, 'Feil ved utsending melding til fagsystem', 'Feil'),
(35, 'Melding mottatt fra fagsystem', 'Informasjon'),
(36, 'Feil ved mottak av melding fra fagsystem', 'Feil'),
(37, 'Melding validert mot CPA', 'Informasjon'),
(38, 'Validering mot CPA mislykket', 'Feil'),
(39, 'Melding validert mot XSD', 'Informasjon'),
(40, 'Validering mot XSD mislykket', 'Feil'),
(41, 'Ukjent feil oppsto!', 'Feil'),
(42, 'Reference hentet', 'Informasjon');
