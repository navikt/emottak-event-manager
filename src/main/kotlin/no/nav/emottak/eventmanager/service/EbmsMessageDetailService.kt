package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid

class EbmsMessageDetailService(
    private val eventRepository: EventRepository,
    private val ebmsMessageDetailRepository: EbmsMessageDetailRepository,
    private val eventTypeRepository: EventTypeRepository
) {
    suspend fun process(value: ByteArray) {
        try {
            log.info("EBMS message details read from Kafka: ${String(value)}")

            val details: EbmsMessageDetail = Json.decodeFromString(String(value))
            ebmsMessageDetailRepository.insert(details)
            log.info("EBMS message details processed successfully: $details")
        } catch (e: Exception) {
            log.error("Exception while processing EBMS message details:${String(value)}", e)
        }
    }

    suspend fun fetchEbmsMessageDetails(from: Instant, to: Instant): List<MessageInfo> {
        val messageDetailsList = ebmsMessageDetailRepository.findByTimeInterval(from, to)
        val relatedRequestIds = ebmsMessageDetailRepository.findRelatedRequestIds(messageDetailsList.map { it.requestId })
        val relatedEvents = eventRepository.findEventsByRequestIds(messageDetailsList.map { it.requestId })

        return messageDetailsList.map {
            val sender = it.sender ?: findSender(it.requestId, relatedEvents)
            val refParam = it.refParam ?: findRefParam(it.requestId, relatedEvents)

            val relatedEventTypeIds = relatedEvents.map { event ->
                event.eventType.value
            }
            val relatedEventTypes = eventTypeRepository.findEventTypesByIds(relatedEventTypeIds)

            val messageStatus = when {
                relatedEventTypes.any { type -> type.status == EventStatusEnum.PROCESSING_COMPLETED }
                -> EventStatusEnum.PROCESSING_COMPLETED.description
                relatedEventTypes.any { type -> type.status == EventStatusEnum.ERROR }
                -> EventStatusEnum.ERROR.description
                relatedEventTypes.any { type -> type.status == EventStatusEnum.INFORMATION }
                -> EventStatusEnum.INFORMATION.description
                else -> "Status er ukjent"
            }

            MessageInfo(
                datomottat = it.savedAt.atZone(ZoneId.of("Europe/Oslo")).toString(),
                mottakidliste = relatedRequestIds[it.requestId] ?: "Not found",
                role = it.fromRole,
                service = it.service,
                action = it.action,
                referanse = refParam,
                avsender = sender,
                cpaid = it.cpaId,
                antall = relatedEvents.count(),
                status = messageStatus
            )
        }
    }

    private fun findSender(requestId: Uuid, events: List<Event>): String {
        events.firstOrNull {
            it.requestId == requestId && it.eventType == EventType.MESSAGE_VALIDATED_AGAINST_CPA
        }?.let { event ->
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData["sender"]
        }?.let {
            return it
        }
        return "Unknown"
    }

    private fun findRefParam(requestId: Uuid, events: List<Event>): String {
        events.firstOrNull {
            it.requestId == requestId && it.eventType == EventType.REFERENCE_RETRIEVED
        }?.let { event ->
            val eventData = Json.decodeFromString<Map<String, String>>(event.eventData)
            eventData[EventDataType.REFERENCE.value]
        }?.let {
            return it
        }
        return "Unknown"
    }
}
