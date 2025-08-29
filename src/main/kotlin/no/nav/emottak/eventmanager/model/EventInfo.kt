package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class EventInfo(
    val eventDate: String,
    val description: String,
    val eventData: String,
    val readableId: String,
    val role: String? = null,
    val service: String? = null,
    val action: String? = null,
    val referenceId: String? = null,
    val senderName: String? = null
)
