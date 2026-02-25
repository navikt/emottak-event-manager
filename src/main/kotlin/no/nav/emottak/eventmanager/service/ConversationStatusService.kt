package no.nav.emottak.eventmanager.service

import no.nav.emottak.eventmanager.model.ConversationStatusInfo
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.repository.ConversationStatusRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import no.nav.emottak.utils.common.toOsloZone
import org.slf4j.LoggerFactory
import java.time.Instant

class ConversationStatusService(private val conversationStatusRepository: ConversationStatusRepository) {
    private val log = LoggerFactory.getLogger(ConversationStatusService::class.java)

    suspend fun findByFilters(
        from: Instant? = null,
        to: Instant? = null,
        cpaIdPattern: String = "",
        service: String = "",
        statuses: List<EventStatusEnum> = listOf(ERROR, INFORMATION, PROCESSING_COMPLETED),
        pageable: Pageable? = null
    ): Page<ConversationStatusInfo> {
        log.debug("Finding ConversationStatus by filters...")
        val result = conversationStatusRepository.findByFilters(from, to, cpaIdPattern, service, statuses, pageable)
        val resultList = result.content.map { conversationStatus ->
            ConversationStatusInfo(
                createdAt = conversationStatus.createdAt.toOsloZone().toString(),
                readableIdList = conversationStatus.readableIdList,
                service = conversationStatus.service,
                cpaId = conversationStatus.cpaId,
                statusAt = conversationStatus.statusAt.toOsloZone().toString(),
                latestStatus = conversationStatus.latestStatus.dbValue
            )
        }
        return Page(result.page, result.size, result.sort, result.totalElements, resultList)
    }
}
