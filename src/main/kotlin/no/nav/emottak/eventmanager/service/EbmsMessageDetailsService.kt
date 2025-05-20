package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypesRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import java.time.Instant
import java.time.ZoneId

class EbmsMessageDetailsService(
    private val eventRepository: EventsRepository,
    private val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository,
    private val eventTypesRepository: EventTypesRepository
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
        return ebmsMessageDetailsRepository.findByTimeInterval(from, to).map { it ->
            val relatedEvents = eventRepository.findEventByRequestId(it.requestId)
            val relatedEventTypeIds = relatedEvents.map { event ->
                event.eventType.value
            }
            val relatedEventTypes = eventTypesRepository.findEventTypesByIds(relatedEventTypeIds)

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
                mottakidliste = it.requestId.toString(),
                role = it.fromRole,
                service = it.service,
                action = it.action,
                referanse = it.refParam,
                avsender = it.sender,
                cpaid = it.cpaId,
                antall = relatedEvents.count(),
                status = messageStatus
            )
        }
    }
}
