package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageLogInfo(
    val eventDate: String,
    val eventDescription: String,
    val eventId: String
)
