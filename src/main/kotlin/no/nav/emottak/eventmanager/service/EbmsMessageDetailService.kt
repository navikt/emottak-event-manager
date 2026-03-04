package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.configuration.config
import no.nav.emottak.eventmanager.constants.Constants
import no.nav.emottak.eventmanager.model.DistinctRolesServicesActions
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.model.ReadableIdInfo
import no.nav.emottak.eventmanager.persistence.repository.DistinctRolesServicesActionsRepository
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import org.jetbrains.annotations.TestOnly
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail as TransportEbmsMessageDetail

class EbmsMessageDetailService(
    private val eventRepository: EventRepository,
    private val ebmsMessageDetailRepository: EbmsMessageDetailRepository,
    private val eventTypeRepository: EventTypeRepository,
    private val distinctRolesServicesActionsRepository: DistinctRolesServicesActionsRepository,
    private var clock: Clock = Clock.system(ZoneId.of(Constants.ZONE_ID_OSLO))
) {
    private val log = LoggerFactory.getLogger(EbmsMessageDetailService::class.java)

    suspend fun process(value: ByteArray) {
        try {
            log.debug("EBMS message details read from Kafka: ${String(value)}")

            val transportEbmsMessageDetail: TransportEbmsMessageDetail = Json.decodeFromString(String(value))
            val ebmsMessageDetail: EbmsMessageDetail = EbmsMessageDetail.fromTransportModel(transportEbmsMessageDetail)
            ebmsMessageDetailRepository.insert(ebmsMessageDetail)
            log.info(ebmsMessageDetail.marker, "EBMS message details processed successfully: $ebmsMessageDetail")
        } catch (e: Exception) {
            log.error("Exception while processing EBMS message details:${String(value)}", e)
        }
    }

    suspend fun fetchEbmsMessageDetails(
        from: Instant,
        to: Instant,
        readableId: String = "",
        cpaId: String = "",
        messageId: String = "",
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): Page<MessageInfo> {
        val filterMsg = createFilterLogMessage(from, to, readableId, cpaId, messageId, role, service, action, pageable)
        log.info("Fetching message details by time and filter: $filterMsg")
        val messageDetailsPage = ebmsMessageDetailRepository.findByTimeInterval(from, to, readableId, cpaId, messageId, role, service, action, pageable)
        val messageDetailsList = messageDetailsPage.content

        log.debug("Finding related readable id's for coresponding conversation id's...")
        val relatedReadableIds = ebmsMessageDetailRepository.findRelatedReadableIds(messageDetailsList.map { it.conversationId }, messageDetailsList.map { it.requestId })

        log.debug("Finding events for related readable id's...")
        val relatedEvents = eventRepository.findByRequestIds(messageDetailsList.map { it.requestId })

        val resultList = messageDetailsList.map { msgDetail ->
            val senderName = msgDetail.getReadableSenderName() ?: findSenderName(msgDetail.requestId, relatedEvents)
            val refParam = msgDetail.refParam ?: findRefParam(msgDetail.requestId, relatedEvents)
            val messageStatus = getMessageStatus(msgDetail.requestId, relatedEvents)

            MessageInfo(
                receivedDate = msgDetail.savedAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString(),
                readableIdList = relatedReadableIds[msgDetail.requestId] ?: "",
                role = msgDetail.fromRole,
                service = msgDetail.service,
                action = msgDetail.action,
                referenceParameter = refParam,
                senderName = senderName,
                cpaId = msgDetail.cpaId,
                count = relatedEvents.count(),
                status = messageStatus
            )
        }
        log.debug("Returning ${messageDetailsPage.size} message details")
        return Page(messageDetailsPage.page, messageDetailsPage.size, messageDetailsPage.sort, messageDetailsPage.totalElements, resultList)
    }

    suspend fun fetchEbmsMessageDetails(id: String): List<ReadableIdInfo> {
        val (idType, messageDetails) = if (Validation.isValidUuid(id)) {
            log.info("Fetching message details by Request ID: $id")
            Pair("Request ID", ebmsMessageDetailRepository.findByRequestId(Uuid.parse(id)))
        } else {
            log.info("Fetching message details by Readable ID: $id")
            Pair("Readable ID", ebmsMessageDetailRepository.findByReadableIdPattern(id, 1000))
        }

        if (messageDetails == null) {
            log.warn("No EBMS message details found for $idType: $id")
            return emptyList()
        }

        val relatedEvents = eventRepository.findByRequestId(messageDetails.requestId)

        val senderName = messageDetails.getReadableSenderName() ?: findSenderName(messageDetails.requestId, relatedEvents)
        val refParam = messageDetails.refParam ?: findRefParam(messageDetails.requestId, relatedEvents)
        val messageStatus = getMessageStatus(messageDetails.requestId, relatedEvents)

        return listOf(
            ReadableIdInfo(
                receivedDate = messageDetails.savedAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString(),
                readableId = messageDetails.readableId ?: "",
                cpaId = messageDetails.cpaId,
                role = messageDetails.fromRole,
                service = messageDetails.service,
                action = messageDetails.action,
                referenceParameter = refParam,
                senderName = senderName,
                status = messageStatus
            )
        )
    }

    suspend fun isDuplicate(
        messageId: String,
        conversationId: String,
        cpaId: String
    ): Boolean {
        return ebmsMessageDetailRepository
            .findByMessageIdConversationIdAndCpaId(messageId, conversationId, cpaId)
            .isNotEmpty()
    }

    suspend fun getDistinctRolesServicesActions(): DistinctRolesServicesActions {
        val filterValues = distinctRolesServicesActionsRepository.getDistinctRolesServicesActions()
        val refreshRate = Instant.now(clock).minus(config().database.distinctValuesRefreshRateInHours.value, ChronoUnit.HOURS)
        if (filterValues == null || refreshRate.isAfter(filterValues.refreshedAt)) {
            log.info("Requesting refresh of distict_roles_services_actions")
            return distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions()
        }
        return filterValues
    }

    private fun findSenderName(requestId: Uuid, events: List<Event>): String {
        events.firstOrNull {
            it.requestId == requestId && it.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA
        }?.let { event ->
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData[EventDataType.SENDER_NAME.value]
        }?.let {
            return it
        }
        return Constants.UNKNOWN
    }

    private fun findRefParam(requestId: Uuid, events: List<Event>): String {
        events.firstOrNull {
            it.requestId == requestId && it.eventType == EventType.REFERENCE_RETRIEVED
        }?.let { event ->
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData[EventDataType.REFERENCE_PARAMETER.value]
        }?.let {
            return it
        }
        return Constants.UNKNOWN
    }

    private suspend fun getMessageStatus(requestId: Uuid, relatedEvents: List<Event>): String {
        val relatedEventTypeIds = relatedEvents.filter { event ->
            event.requestId == requestId
        }.map { event ->
            event.eventType.value
        }
        val relatedEventTypes = eventTypeRepository.findEventTypesByIds(relatedEventTypeIds)

        return when {
            relatedEventTypes.any { type -> type.status == EventStatusEnum.PROCESSING_COMPLETED }
            -> EventStatusEnum.PROCESSING_COMPLETED.description

            relatedEventTypes.any { type -> type.status == EventStatusEnum.ERROR }
            -> EventStatusEnum.ERROR.description

            relatedEventTypes.any { type -> type.status == EventStatusEnum.INFORMATION }
            -> EventStatusEnum.INFORMATION.description

            else -> Constants.UNKNOWN
        }
    }

    private fun createFilterLogMessage(
        from: Instant,
        to: Instant,
        readableId: String,
        cpaId: String,
        messageId: String,
        role: String,
        service: String,
        action: String,
        pageable: Pageable? = null
    ): String {
        val filters = mutableListOf<String>()
        filters.add("from:'$from'")
        filters.add("to:'$to'")
        if (readableId.isNotBlank()) filters.add("readableId:'$readableId'")
        if (cpaId.isNotBlank()) filters.add("cpaId:'$cpaId'")
        if (messageId.isNotBlank()) filters.add("messageId:'$messageId'")
        if (role.isNotBlank()) filters.add("role:'$role'")
        if (service.isNotBlank()) filters.add("service:'$service'")
        if (action.isNotBlank()) filters.add("action:'$action'")
        if (pageable != null) {
            filters.add("pageSize:'${pageable.pageSize}'")
            filters.add("pageNumber:'${pageable.pageNumber}'")
        }
        return filters.joinToString(", ")
    }

    @TestOnly
    fun setClockForTests(testClock: Clock) {
        clock = testClock
    }
}
