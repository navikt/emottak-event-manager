package no.nav.emottak.eventmanager

import java.time.LocalDateTime

class EventsService() {
    fun fetchevents(
        fom: LocalDateTime,
        tom: LocalDateTime
    ): List<EventInfo> =
        listOf(
            EventInfo(
                "2025-03-04 12:53:59.987",
                "Melding ferdig behandlet",
                "bla bla bla",
                "2503041253voll86196.1",
                "Sykmelder",
                "Sykmelding",
                "Registrering",
                "300682",
                "VOLL LEGESENTER AS, ( 999018424 )"
            ),
            EventInfo(
                "2025-03-04 12:53:59.987",
                "Melding ferdig behandlet",
                "bla bla bla",
                "2503041253voll86197.1",
                "Sykmelder",
                "Sykmelding",
                "Registrering",
                "300682",
                "VOLL LEGESENTER AS, ( 999018424 )"
            ),
            EventInfo(
                "2025-03-04 12:53:59.987",
                "Melding ferdig behandlet",
                "bla bla bla",
                "2503041253voll86198.1",
                "Sykmelder",
                "Sykmelding",
                "Registrering",
                "300682",
                "VOLL LEGESENTER AS, ( 999018424 )"
            )
        )
}
