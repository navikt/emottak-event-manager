package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.kafka.model.Event
import java.time.Instant
import java.time.ZoneId

class EventService(
    private val eventsRepository: EventsRepository,
    private val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
) {
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
        val eventsList = eventsRepository.findEventByTimeInterval(from, to)
        val requestIds = eventsList.map { it.requestId }.distinct()
        val messageDetailsMap = ebmsMessageDetailsRepository.findByRequestIds(requestIds)

        return eventsList.map {
            val ebmsMessageDetails = messageDetailsMap[it.requestId]
            EventInfo(
                hendelsedato = it.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                hendelsedeskr = it.eventType.toString(),
                tillegsinfo = it.eventData,
                mottakid = it.requestId.toString(),
                role = ebmsMessageDetails?.fromRole,
                service = ebmsMessageDetails?.service,
                action = ebmsMessageDetails?.action,
                referanse = ebmsMessageDetails?.refParam,
                avsender = ebmsMessageDetails?.sender
            )
        }.toList()
    }
}
