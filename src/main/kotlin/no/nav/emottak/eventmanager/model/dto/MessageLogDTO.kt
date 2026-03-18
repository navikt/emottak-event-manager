package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageLogDTO(
    val eventDate: String,
    val eventDescription: String,
    val eventId: String
)
