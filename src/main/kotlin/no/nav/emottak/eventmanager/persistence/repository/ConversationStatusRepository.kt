package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.ConversationStatus
import no.nav.emottak.eventmanager.model.ConversationStatusData
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
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.readableId
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import no.nav.emottak.utils.common.nowOsloToInstant
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.groupConcat
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit

class ConversationStatusRepository(private val database: Database) {

    suspend fun insert(id: String, datetime: Instant = nowOsloToInstant().truncatedTo(ChronoUnit.MICROS)): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            ConversationStatusTable.insertIgnore {
                it[conversationId] = id
                it[createdAt] = datetime
                it[latestStatus] = INFORMATION
                it[statusAt] = datetime
            }.insertedCount == 1
        }
    }

    suspend fun update(
        id: String,
        status: EventStatusEnum,
        datetime: Instant = nowOsloToInstant().truncatedTo(ChronoUnit.MICROS)
    ): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val updatedRows = ConversationStatusTable.update({
                conversationId eq id
            }) {
                it[latestStatus] = status
                it[statusAt] = datetime
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
    ): Page<ConversationStatusData> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val subqueryAlias = getConversationsQuery(from, to, cpaIdPattern, service, statuses)
                .alias("conversations_matching_filters")

            // Tell antall rader:
            val totalCount = subqueryAlias.select(subqueryAlias[conversationId]).count()

            // Hent relevante rader:
            val relatedReadableIdsColumn = readableId.groupConcat(
                ",",
                orderBy = arrayOf(
                    EbmsMessageDetailTable.savedAt to SortOrder.ASC
                )
            ).alias("related_readable_ids")
            val conversations = EbmsMessageDetailTable
                .join(subqueryAlias, JoinType.INNER, onColumn = subqueryAlias[conversationId], otherColumn = EbmsMessageDetailTable.conversationId)
                .select(
                    subqueryAlias[conversationId],
                    subqueryAlias[createdAt],
                    subqueryAlias[latestStatus],
                    subqueryAlias[statusAt],
                    subqueryAlias[EbmsMessageDetailTable.cpaId],
                    subqueryAlias[EbmsMessageDetailTable.service],
                    relatedReadableIdsColumn
                )
                .apply {
                    this.applyPagableLimitAndOrderBy(pageable, subqueryAlias[createdAt]) // TODO: Sortere på createdAt eller statusAt?
                }
                .groupBy(
                    subqueryAlias[conversationId],
                    subqueryAlias[createdAt],
                    subqueryAlias[latestStatus],
                    subqueryAlias[statusAt],
                    subqueryAlias[EbmsMessageDetailTable.cpaId],
                    subqueryAlias[EbmsMessageDetailTable.service]
                )
                .mapNotNull {
                    ConversationStatusData(
                        conversationId = it[subqueryAlias[conversationId]],
                        createdAt = it[subqueryAlias[createdAt]],
                        latestStatus = it[subqueryAlias[latestStatus]],
                        statusAt = it[subqueryAlias[statusAt]],
                        readableIdList = it[relatedReadableIdsColumn],
                        service = it[subqueryAlias[EbmsMessageDetailTable.service]],
                        cpaId = it[subqueryAlias[EbmsMessageDetailTable.cpaId]]
                    )
                }
                .toList()

            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, conversations.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, conversations)
        }
    }

    private fun getConversationsQuery(
        from: Instant? = null,
        to: Instant? = null,
        cpaIdPattern: String = "",
        service: String = "",
        statuses: List<EventStatusEnum> = listOf(ERROR, INFORMATION, PROCESSING_COMPLETED)
    ): Query {
        return ConversationStatusTable
            .join(EbmsMessageDetailTable, JoinType.INNER, onColumn = conversationId, otherColumn = EbmsMessageDetailTable.conversationId)
            .select(ConversationStatusTable.columns)
            .withDistinctOn(conversationId)
            .where {
                latestStatus.castTo<String>(VarCharColumnType())
                    .inList(statuses.map { it.toString() })
            }
            .apply {
                this.applyDatetimeFilter(createdAt, from, to) // TODO: Filtrere på createdAt eller statusAt?
                this.applyPatternFilter(cpaIdPattern, EbmsMessageDetailTable.cpaId.nullable())
                this.applyFilter(service, EbmsMessageDetailTable.service.nullable())
            }
            .orderBy(conversationId)
    }
}
