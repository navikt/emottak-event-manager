package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.ConversationStatus
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.latestStatus
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.nullable
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.statusAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import no.nav.emottak.utils.common.nowOsloToInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit

class ConversationStatusRepository(private val database: Database) {

    suspend fun insert(id: String): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            ConversationStatusTable.insertIgnore {
                val now = nowOsloToInstant().truncatedTo(ChronoUnit.MICROS)
                it[conversationId] = id
                it[createdAt] = now
                it[latestStatus] = INFORMATION
                it[statusAt] = now
            }.insertedCount == 1
        }
    }

    suspend fun update(id: String, status: EventStatusEnum): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val updatedRows = ConversationStatusTable.update({
                conversationId eq id
            }) {
                it[latestStatus] = status
                it[statusAt] = nowOsloToInstant().truncatedTo(ChronoUnit.MICROS)
            }
            updatedRows == 1
        }
    }

    suspend fun get(id: String): ConversationStatus? = withContext(Dispatchers.IO) {
        transaction(database.db) {
            ConversationStatusTable
                .select(ConversationStatusTable.columns)
                .where { conversationId eq id }
                .mapNotNull {
                    ConversationStatus(
                        conversationId = it[conversationId],
                        createdAt = it[createdAt],
                        latestStatus = it[latestStatus],
                        statusAt = it[statusAt]

                    )
                }
                .singleOrNull()
        }
    }

    suspend fun findByFilters(
        from: Instant? = null,
        to: Instant? = null,
        cpaIdPattern: String = "",
        service: String = "",
        statuses: List<EventStatusEnum> = listOf(ERROR, INFORMATION, PROCESSING_COMPLETED),
        pageable: Pageable? = null
    ): Page<ConversationStatus> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val countColumn = conversationId.countDistinct()
            val totalCount = ConversationStatusTable
                .join(EbmsMessageDetailTable, JoinType.INNER, onColumn = conversationId, otherColumn = EbmsMessageDetailTable.conversationId)
                .select(countColumn)
                .where {
                    latestStatus.castTo<String>(VarCharColumnType())
                        .inList(statuses.map { it.toString() })
                }
                .apply {
                    this.applyDatetimeFilter(statusAt, from, to)
                    this.applyPatternFilter(cpaIdPattern, EbmsMessageDetailTable.cpaId.nullable())
                    this.applyFilter(service, EbmsMessageDetailTable.service.nullable())
                }
                .map {
                    it[countColumn]
                }
                .single()
            val conversations = ConversationStatusTable
                .join(EbmsMessageDetailTable, JoinType.INNER, onColumn = conversationId, otherColumn = EbmsMessageDetailTable.conversationId)
                .select(ConversationStatusTable.columns)
                .withDistinctOn(conversationId)
                .where {
                    latestStatus.castTo<String>(VarCharColumnType())
                        .inList(statuses.map { it.toString() })
                }
                .apply {
                    this.applyDatetimeFilter(statusAt, from, to)
                    this.applyPatternFilter(cpaIdPattern, EbmsMessageDetailTable.cpaId.nullable())
                    this.applyFilter(service, EbmsMessageDetailTable.service.nullable())
                    this.applyPagableLimit(pageable, statusAt)
                }
                .orderBy(
                    // conversationId to SortOrder.ASC
                    statusAt to (pageable?.getSortOrder() ?: SortOrder.ASC)
                )
                .mapNotNull {
                    ConversationStatus(
                        conversationId = it[conversationId],
                        createdAt = it[createdAt],
                        latestStatus = it[latestStatus],
                        statusAt = it[statusAt]
                    )
                }
                .toList()
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, conversations.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, conversations)
        }
    }
}
