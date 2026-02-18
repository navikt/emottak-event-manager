package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.constants.Constants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.eventmanager.constants.Constants.NOT_APPLICABLE_ROLE
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageLogInfo
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.repository.ConversationStatusRepository
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.utils.common.toOsloZone
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.slf4j.LoggerFactory
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.Event as TransportEvent

class EventService(
    private val eventRepository: EventRepository,
    private val ebmsMessageDetailRepository: EbmsMessageDetailRepository,
    private val conversationStatusRepository: ConversationStatusRepository
) {
    private val log = LoggerFactory.getLogger(EventService::class.java)

    suspend fun process(value: ByteArray) {
        try {
            log.debug("Event read from Kafka: ${String(value)}")
            val transportEvent: TransportEvent = Json.decodeFromString(String(value))
            val event = Event.fromTransportModel(transportEvent)

            updateMessageDetail(event)
            updateConversationStatus(event)

            eventRepository.insert(event)
            log.info(event.marker, "Event processed successfully: $event")
        } catch (e: Exception) {
            log.error("Exception while processing event: ${String(value)}", e)
        }
    }

    suspend fun fetchEvents(
        from: Instant,
        to: Instant,
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): Page<EventInfo> {
        val eventsPage = if (role.isNotEmpty() || service.isNotEmpty() || action.isNotEmpty()) {
            eventRepository.findByTimeIntervalJoinMessageDetail(from, to, role, service, action, pageable)
        } else {
            eventRepository.findByTimeInterval(from, to, pageable)
        }
        val eventsList = eventsPage.content
        val requestIds = eventsList.map { it.requestId }.distinct()
        log.debug("Number of different Request IDs: ${requestIds.size}")
        val messageDetailsMap = ebmsMessageDetailRepository.findByRequestIds(requestIds)
        var numberOfRequestIdsNotFound = 0
        val resultList = eventsList.map {
            val ebmsMessageDetail = messageDetailsMap[it.requestId]
            if (ebmsMessageDetail == null) numberOfRequestIdsNotFound++
            EventInfo(
                eventDate = it.createdAt.toOsloZone().toString(),
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
        return Page(eventsPage.page, eventsPage.size, eventsPage.sort, eventsPage.totalElements, resultList)
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
                    eventDate = it.createdAt.toOsloZone().toString(),
                    eventDescription = it.eventType.description,
                    eventId = it.eventType.value.toString()
                )
            }.toList()
    }

    private suspend fun updateMessageDetail(event: Event) {
        // Oppdater eventData ved "Melding validert mot CPA":
        if (event.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA) {
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData[EventDataType.SENDER_NAME.value]?.also {
                val relatedMessageDetail = ebmsMessageDetailRepository.findByRequestId(event.requestId)
                if (relatedMessageDetail != null) {
                    val updatedMessageDetail = relatedMessageDetail.copy(senderName = it)
                    ebmsMessageDetailRepository.update(updatedMessageDetail)
                    log.info(event.marker, "Sender updated successfully for requestId: ${event.requestId}")
                } else {
                    log.warn(
                        event.marker,
                        "Cannot update sender! EbmsMessageDetail for requestId: ${event.requestId} not found"
                    )
                }
            }
        }

        // Oppdater eventData ved "Reference hentet":
        else if (event.eventType == EventType.REFERENCE_RETRIEVED) {
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData[EventDataType.REFERENCE_PARAMETER.value]?.also {
                val relatedMessageDetail = ebmsMessageDetailRepository.findByRequestId(event.requestId)
                if (relatedMessageDetail != null) {
                    val updatedMessageDetail = relatedMessageDetail.copy(refParam = it)
                    ebmsMessageDetailRepository.update(updatedMessageDetail)
                    log.info(event.marker, "Reference parameter updated successfully for requestId: ${event.requestId}")
                } else {
                    log.warn(
                        event.marker,
                        "Cannot update reference parameter! EbmsMessageDetail for requestId: ${event.requestId} not found"
                    )
                }
            }
        }
    }

    private suspend fun updateConversationStatus(event: Event) {
        // smtp-transport sender ikke med conversationId:
        val conversationId: String? = getConversationIdAtRelevantEventTypes(event)

        val eventStatus: EventStatusEnum? =
            if (event.eventType == EventType.RETRY_TRIGGED) {
                EventStatusEnum.INFORMATION
            } else if (event.eventType.isCompleteEvent()) {
                EventStatusEnum.PROCESSING_COMPLETED
            } else if (event.eventType.isErrorEvent()) {
                EventStatusEnum.ERROR
            } else {
                null
            }

        if (eventStatus != null && conversationId != null) {
            val success = conversationStatusRepository.update(
                id = conversationId,
                status = eventStatus
            )
            if (!success) log.warn(event.marker, "Cannot update conversation status! ConversationId: $conversationId not found")
        }
    }

    private suspend fun getConversationIdAtRelevantEventTypes(event: Event): String? {
        if (event.conversationId != null) return event.conversationId
        if (event.eventType == EventType.MESSAGE_SENT_VIA_SMTP ||
            event.eventType == EventType.MESSAGE_SENT_VIA_HTTP ||
            event.eventType.isErrorEvent()
        ) {
            log.info(event.marker, "Getting conversationId for requestId {} to update status", event.requestId)
            val relatedMessageDetail = ebmsMessageDetailRepository.findByRequestId(event.requestId)
            if (relatedMessageDetail != null) {
                when (event.eventType) {
                    EventType.MESSAGE_SENT_VIA_SMTP -> {
                        // Returner conversationId kun hvis Acknowledgment fra konsument
                        if (relatedMessageDetail.action == ACKNOWLEDGMENT_ACTION &&
                            relatedMessageDetail.fromRole != NOT_APPLICABLE_ROLE
                        ) {
                            return relatedMessageDetail.conversationId
                        }
                    }
                    else -> return relatedMessageDetail.conversationId
                }
            } else {
                log.warn(event.marker, "Cannot update status on conversation: EbmsMessageDetail for requestId {} not found", event.requestId)
            }
        }
        return null
    }
}

fun EventType.isErrorEvent() = this in listOf(
    EventType.ERROR_WHILE_RECEIVING_MESSAGE_VIA_SMTP,
    EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_SMTP,
    EventType.ERROR_WHILE_RECEIVING_MESSAGE_VIA_HTTP,
    EventType.ERROR_WHILE_SENDING_MESSAGE_VIA_HTTP,
    EventType.ERROR_WHILE_SAVING_PAYLOAD_INTO_DATABASE,
    EventType.ERROR_WHILE_READING_PAYLOAD_FROM_DATABASE,
    EventType.ERROR_WHILE_RECEIVING_PAYLOAD_VIA_HTTP,
    EventType.ERROR_WHILE_STORING_MESSAGE_IN_QUEUE,
    EventType.ERROR_WHILE_READING_MESSAGE_FROM_QUEUE,
    EventType.ERROR_WHILE_SAVING_MESSAGE_IN_JURIDISK_LOGG,
    EventType.MESSAGE_ENCRYPTION_FAILED,
    EventType.MESSAGE_DECRYPTION_FAILED,
    EventType.MESSAGE_COMPRESSION_FAILED,
    EventType.MESSAGE_DECOMPRESSION_FAILED,
    EventType.SIGNATURE_CHECK_FAILED,
    EventType.OCSP_CHECK_FAILED,
    EventType.ERROR_WHILE_SENDING_MESSAGE_TO_FAGSYSTEM,
    EventType.ERROR_WHILE_RECEIVING_MESSAGE_FROM_FAGSYSTEM,
    EventType.VALIDATION_AGAINST_CPA_FAILED,
    EventType.VALIDATION_AGAINST_XSD_FAILED,
    EventType.UNKNOWN_ERROR_OCCURRED
)

fun EventType.isCompleteEvent() = this in listOf(
    // Skal sette conversation til complete hvis kallet skjedde synkront:
    EventType.MESSAGE_SENT_VIA_HTTP,
    // Skal sette conversation til complete hvis det er avsluttende Acknowledgement fra konsument.
    // Denne sjekken skjer i getConversationIdForRelevantEvents() (smtp-transport sender ikke conversationId):
    EventType.MESSAGE_SENT_VIA_SMTP
)
