package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.kafka.model.Event
import java.time.Instant
import java.time.ZoneId

class EventService(private val eventsRepository: EventsRepository) {
    suspend fun process(value: ByteArray) {
        try {
            log.info("Event read from Kafka: ${String(value)}")

            val event: Event = Json.decodeFromString(String(value))
            eventsRepository.insert(event)
            log.info("Event processed successfully: $event")
        } catch (e: Exception) {
            log.error("Exception while processing event:${String(value)}", e)
        }
    }

    suspend fun fetchEvents(from: Instant, to: Instant): List<EventInfo> {
        return eventsRepository.findEventByTimeInterval(from, to).map {
            EventInfo(
                hendelsedato = it.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                hendelsedeskr = it.eventType.toString(),
                tillegsinfo = it.eventData,
                mottakid = it.requestId.toString()
            )
        }
    }
}
