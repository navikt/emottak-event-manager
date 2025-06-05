package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant
import java.time.ZoneId

class EbmsMessageDetailsService(
    private val eventRepository: EventsRepository,
    private val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository
) {
    suspend fun process(value: ByteArray) {
        try {
            log.info("EBMS message details read from Kafka: ${String(value)}")

            val details: EbmsMessageDetails = Json.decodeFromString(String(value))
            ebmsMessageDetailsRepository.insert(details)
            log.info("EBMS message details processed successfully: $details")
        } catch (e: Exception) {
            log.error("Exception while processing EBMS message details:${String(value)}", e)
        }
    }

    suspend fun fetchEbmsMessageDetails(from: Instant, to: Instant): List<MessageInfo> {
        return ebmsMessageDetailsRepository.findByTimeInterval(from, to).map {
            val relatedEvents = eventRepository.findEventByRequestId(it.requestId)
            val sender = it.sender ?: findSender(relatedEvents)

            MessageInfo(
                datomottat = it.savedAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                mottakidliste = it.requestId.toString(),
                role = it.fromRole,
                service = it.service,
                action = it.action,
                referanse = it.refParam,
                avsender = sender,
                cpaid = it.cpaId,
                antall = relatedEvents.count(),
                status = "Unimplemented"
            )
        }
    }

    private fun findSender(events: List<Event>): String {
        events.firstOrNull {
            it.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA
        }?.let { event ->
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData["sender"]
        }?.let { sender ->
            return sender
        }
        return "Unknown"
    }
}
