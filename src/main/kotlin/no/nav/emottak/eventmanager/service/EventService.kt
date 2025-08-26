package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.Validation
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageLoggInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.Event as TransportEvent

class EventService(
    private val eventRepository: EventRepository,
    private val ebmsMessageDetailRepository: EbmsMessageDetailRepository
) {
    suspend fun process(value: ByteArray) {
        try {
            log.info("Event read from Kafka: ${String(value)}")
            val transportEvent: TransportEvent = Json.decodeFromString(String(value))
            val event = Event.fromTransportModel(transportEvent)

            updateMessageDetails(event)

            eventRepository.insert(event)
            log.info("Event processed successfully: $event")
        } catch (e: Exception) {
            log.error("Exception while processing event:${String(value)}", e)
        }
    }

    suspend fun fetchEvents(from: Instant, to: Instant): List<EventInfo> {
        val startTime = System.currentTimeMillis()

        val eventsList = eventRepository.findEventByTimeInterval(from, to)
        log.info("Profiling: findEventByTimeInterval executed in ${System.currentTimeMillis() - startTime} ms")

        val requestIds = eventsList.map { it.requestId }.distinct()
        val messageDetailsMap = ebmsMessageDetailRepository.findByRequestIds(requestIds)
        log.info("Profiling: findByRequestIds executed in ${System.currentTimeMillis() - startTime} ms")

        return eventsList.map {
            val ebmsMessageDetail = messageDetailsMap[it.requestId]
            EventInfo(
                hendelsedato = it.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                hendelsedeskr = it.eventType.description,
                tillegsinfo = it.eventData,
                mottakid = ebmsMessageDetail?.mottakId ?: "",
                role = ebmsMessageDetail?.fromRole,
                service = ebmsMessageDetail?.service,
                action = ebmsMessageDetail?.action,
                referanse = ebmsMessageDetail?.refParam,
                avsender = ebmsMessageDetail?.sender
            )
        }.toList()
    }

    suspend fun fetchMessageLoggInfo(id: String): List<MessageLoggInfo> {
        val eventsList = if (Validation.isValidUuid(id)) {
            log.info("Fetching events by Request ID: $id")
            eventRepository.findEventsByRequestId(Uuid.parse(id))
        } else {
            log.info("Fetching events by Mottak ID: $id")
            val messageDetails = ebmsMessageDetailRepository.findByMottakId(id)

            if (messageDetails == null) {
                log.warn("No EbmsMessageDetail found for Mottak ID: $id")
                emptyList()
            } else {
                eventRepository.findEventsByRequestId(messageDetails.requestId)
            }
        }

        return eventsList.sortedBy { it.createdAt }
            .map {
                MessageLoggInfo(
                    hendelsesdato = it.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                    hendelsesbeskrivelse = it.eventType.description,
                    hendelsesid = it.eventType.value.toString()
                )
            }.toList()
    }

    private suspend fun updateMessageDetails(event: Event) {
        if (event.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA) {
            val relatedMessageDetails = ebmsMessageDetailRepository.findByRequestId(event.requestId)
            if (relatedMessageDetails != null) {
                val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)

                eventData["sender"]?.also {
                    val updatedMessageDetails = relatedMessageDetails.copy(sender = it)
                    ebmsMessageDetailRepository.update(updatedMessageDetails)
                    log.info("Sender updated successfully for requestId: ${event.requestId}")
                }
            } else {
                log.warn("Cannot update sender! EbmsMessageDetail for requestId: ${event.requestId} not found")
            }
        }

        if (event.eventType == EventType.REFERENCE_RETRIEVED) {
            val relatedMessageDetails = ebmsMessageDetailRepository.findByRequestId(event.requestId)
            if (relatedMessageDetails != null) {
                val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)

                eventData[EventDataType.REFERENCE.value]?.also {
                    val updatedMessageDetails = relatedMessageDetails.copy(refParam = it)
                    ebmsMessageDetailRepository.update(updatedMessageDetails)
                    log.info("Reference parameter updated successfully for requestId: ${event.requestId}")
                }
            } else {
                log.warn("Cannot update reference parameter! EbmsMessageDetail for requestId: ${event.requestId} not found")
            }
        }
    }
}
