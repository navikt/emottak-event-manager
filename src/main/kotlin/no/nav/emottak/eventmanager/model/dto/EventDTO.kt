package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class EventDTO(
    val eventDate: String,
    val description: String,
    val eventData: String,
    val readableId: String,
    val role: String? = null,
    val service: String? = null,
    val action: String? = null,
    val referenceParameter: String? = null,
    val senderName: String? = null
)
