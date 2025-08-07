package no.nav.emottak.eventmanager.model

import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.Event as TransportEvent

data class Event(
    val eventType: EventType,
    val requestId: Uuid,
    val contentId: String? = null,
    val messageId: String,
    val eventData: String,
    val createdAt: Instant
) {
    companion object {
        fun fromTransportModel(transportEvent: TransportEvent): Event {
            return Event(
                eventType = transportEvent.eventType,
                requestId = transportEvent.requestId,
                contentId = transportEvent.contentId,
                messageId = transportEvent.messageId,
                eventData = transportEvent.eventData,
                createdAt = transportEvent.createdAt
            )
        }
    }
}
