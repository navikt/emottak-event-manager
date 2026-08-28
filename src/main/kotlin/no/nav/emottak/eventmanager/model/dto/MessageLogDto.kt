package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class MessageLogDto(
    val eventDate: String,
    val eventDescription: String,
    val eventId: String,
    val eventData: String? = null,
    val eventStatus: String
)
