package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.constants.Constants
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageLogInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.Event as TransportEvent

class EventService(
    private val eventRepository: EventRepository,
    private val ebmsMessageDetailRepository: EbmsMessageDetailRepository
) {

    private val log = LoggerFactory.getLogger(EventService::class.java)

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

    suspend fun fetchEvents(
        from: Instant,
        to: Instant,
        role: String = "",
        service: String = "",
        action: String = ""
    ): List<EventInfo> {
        val eventsList = if (role.isNotEmpty() || service.isNotEmpty() || action.isNotEmpty()) {
            eventRepository.findByTimeIntervalJoinMessageDetail(from, to, 1000, role, service, action)
        } else {
            eventRepository.findByTimeInterval(from, to, 1000)
        }

        val requestIds = eventsList.map { it.requestId }.distinct()
        log.debug("Number of different Request IDs: ${requestIds.size}")
        val messageDetailsMap = ebmsMessageDetailRepository.findByRequestIds(requestIds)
        var numberOfRequestIdsNotFound = 0
        return eventsList.map {
            val ebmsMessageDetail = messageDetailsMap[it.requestId]
            if (ebmsMessageDetail == null) numberOfRequestIdsNotFound++
            EventInfo(
                eventDate = it.createdAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString(),
                description = it.eventType.description,
                eventData = it.eventData,
                readableId = ebmsMessageDetail?.readableId ?: "",
                role = ebmsMessageDetail?.fromRole,
                service = ebmsMessageDetail?.service,
                action = ebmsMessageDetail?.action,
                referenceParameter = ebmsMessageDetail?.refParam,
                senderName = ebmsMessageDetail?.senderName
            )
        }.toList().also {
            if (numberOfRequestIdsNotFound > 0) log.warn("Number of requestIds not found: $numberOfRequestIdsNotFound")
        }
    }

    suspend fun fetchMessageLogInfo(id: String): List<MessageLogInfo> {
        val eventsList = if (Validation.isValidUuid(id)) {
            log.info("Fetching events by Request ID: $id")
            eventRepository.findByRequestId(Uuid.parse(id))
        } else {
            log.info("Fetching events by Readable ID: $id")
            val messageDetails = ebmsMessageDetailRepository.findByReadableId(id)

            if (messageDetails == null) {
                log.warn("No EbmsMessageDetail found for Readable ID: $id")
                emptyList()
            } else {
                eventRepository.findByRequestId(messageDetails.requestId)
            }
        }

        return eventsList.sortedBy { it.createdAt }
            .map {
                MessageLogInfo(
                    eventDate = it.createdAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString(),
                    eventDescription = it.eventType.description,
                    eventId = it.eventType.value.toString()
                )
            }.toList()
    }

    private suspend fun updateMessageDetails(event: Event) {
        if (event.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA) {
            val relatedMessageDetails = ebmsMessageDetailRepository.findByRequestId(event.requestId)
            if (relatedMessageDetails != null) {
                val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)

                eventData[EventDataType.SENDER_NAME.value]?.also {
                    val updatedMessageDetails = relatedMessageDetails.copy(senderName = it)
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

                eventData[EventDataType.REFERENCE_PARAMETER.value]?.also {
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
