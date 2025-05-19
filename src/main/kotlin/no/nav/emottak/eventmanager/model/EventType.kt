package no.nav.emottak.eventmanager.model

import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum

data class EventType(
    val eventTypeId: Int,
    val description: String,
    val status: EventStatusEnum
)
