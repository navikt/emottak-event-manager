package no.nav.emottak.eventmanager.model

import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum

data class EventWithStatus(
    val event: Event,
    val status: EventStatusEnum
)
