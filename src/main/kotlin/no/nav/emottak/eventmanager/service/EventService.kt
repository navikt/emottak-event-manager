package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.events.model.Event

class EventService(private val eventsRepository: EventsRepository) {
    suspend fun process(key: String, value: ByteArray) {
        try {
            log.info("Event read from Kafka: key:$key, value:${String(value)}")

            val event: Event = Json.decodeFromString(String(value))
            eventsRepository.insert(event)
            log.info("Event processed successfully: key:$key")
        } catch (e: Exception) {
            log.error("Exception while processing event key:$key, value:${String(value)}", e)
        }
    }
}
