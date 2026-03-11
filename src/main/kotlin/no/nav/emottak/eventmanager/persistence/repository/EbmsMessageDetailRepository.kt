package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.action
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.cpaId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromRole
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.messageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.nullable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.readableId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refParam
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refToMessageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.requestId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.savedAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.senderName
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.sentAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.service
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toRole
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.groupConcat
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class EbmsMessageDetailRepository(private val database: Database) {

    suspend fun insert(ebmsMessageDetail: EbmsMessageDetail): Uuid = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EbmsMessageDetailTable.insert {
                it[requestId] = ebmsMessageDetail.requestId.toJavaUuid()
                it.populateFrom(ebmsMessageDetail)
            }
        }
        ebmsMessageDetail.requestId
    }

    suspend fun update(ebmsMessageDetail: EbmsMessageDetail): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val updatedRows = EbmsMessageDetailTable
                .update({
                    requestId eq ebmsMessageDetail.requestId.toJavaUuid()
                }) {
                    it.populateFrom(ebmsMessageDetail)
                }
            updatedRows > 0
        }
    }

    suspend fun findByRequestId(requestId: Uuid): EbmsMessageDetail? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { EbmsMessageDetailTable.requestId eq requestId.toJavaUuid() }
                .mapNotNull {
                    toEbmsMessageDetail(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByReadableId(readableId: String): EbmsMessageDetail? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { EbmsMessageDetailTable.readableId eq readableId }
                .mapNotNull {
                    toEbmsMessageDetail(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByReadableIdPattern(readableIdPattern: String, limit: Int? = null): EbmsMessageDetail? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { readableId.lowerCase() like "%$readableIdPattern%".lowercase() }
                .apply {
                    if (limit != null) this.limit(limit)
                }
                .mapNotNull {
                    toEbmsMessageDetail(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByRequestIds(requestIds: List<Uuid>): Map<Uuid, EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    toEbmsMessageDetail(it)
                }
                .toList()
                .associateBy { it.requestId }
        }
    }

    suspend fun findByTimeInterval(
        from: Instant,
        to: Instant,
        readableIdPattern: String = "",
        cpaIdPattern: String = "",
        messageIdPattern: String = "",
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): Page<EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction {
            val totalCount = EbmsMessageDetailTable.select(savedAt).where { savedAt.between(from, to) }
                .apply {
                    this.applyReadableIdCpaIdMessageIdFilters(readableIdPattern, cpaIdPattern, messageIdPattern)
                    this.applyRoleServiceActionFilters(role, service, action)
                }.count()
            val list =
                EbmsMessageDetailTable
                    .select(EbmsMessageDetailTable.columns)
                    .where { savedAt.between(from, to) }
                    .apply {
                        this.applyReadableIdCpaIdMessageIdFilters(readableIdPattern, cpaIdPattern, messageIdPattern)
                        this.applyRoleServiceActionFilters(role, service, action)
                        this.applyPagableLimitAndOrderBy(pageable, savedAt)
                    }
                    .mapNotNull {
                        toEbmsMessageDetail(it)
                    }
                    .toList()
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, list.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, list)
        }
    }

    suspend fun findRelatedRequestIds(requestIds: List<Uuid>): Map<Uuid, String> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val relatedRequestIdsColumn = requestId.castTo<String>(
                columnType = TextColumnType()
            ).groupConcat(
                separator = ",",
                orderBy = arrayOf(
                    savedAt to SortOrder.ASC
                )
            ).alias("related_request_ids")

            val subQuery = EbmsMessageDetailTable
                .select(conversationId, relatedRequestIdsColumn)
                .groupBy(conversationId)
                .alias("related")

            EbmsMessageDetailTable
                .join(subQuery, JoinType.INNER, conversationId, subQuery[conversationId])
                .select(requestId, subQuery[relatedRequestIdsColumn])
                .where { requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    Pair(it[requestId].toKotlinUuid(), it[subQuery[relatedRequestIdsColumn]])
                }
                .toMap()
        }
    }

    suspend fun findRelatedReadableIds(conversationIds: List<String>, requestIds: List<Uuid>): Map<Uuid, String?> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val relatedReadableIdsColumn = readableId.groupConcat(
                separator = ",",
                orderBy = arrayOf(
                    savedAt to SortOrder.ASC
                )
            ).alias("related_readable_ids")

            val subQuery = EbmsMessageDetailTable
                .select(conversationId, relatedReadableIdsColumn)
                .where { conversationId.inList(conversationIds) }
                .groupBy(conversationId)
                .alias("related")

            EbmsMessageDetailTable
                .join(subQuery, JoinType.INNER, conversationId, subQuery[conversationId])
                .select(requestId, subQuery[relatedReadableIdsColumn])
                .where { requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    Pair(it[requestId].toKotlinUuid(), it[subQuery[relatedReadableIdsColumn]])
                }
                .toMap()
        }
    }

    suspend fun findByMessageIdConversationIdAndCpaId(
        messageId: String,
        conversationId: String,
        cpaId: String
    ): List<EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where {
                    (EbmsMessageDetailTable.messageId eq messageId) and
                        (EbmsMessageDetailTable.conversationId eq conversationId) and
                        (EbmsMessageDetailTable.cpaId eq cpaId)
                }
                .mapNotNull {
                    EbmsMessageDetail(
                        requestId = it[requestId].toKotlinUuid(),
                        readableId = it[readableId],
                        cpaId = it[EbmsMessageDetailTable.cpaId],
                        conversationId = it[EbmsMessageDetailTable.conversationId],
                        messageId = it[EbmsMessageDetailTable.messageId],
                        refToMessageId = it[refToMessageId],
                        fromPartyId = it[fromPartyId],
                        fromRole = it[fromRole],
                        toPartyId = it[toPartyId],
                        toRole = it[toRole],
                        service = it[service],
                        action = it[action],
                        refParam = it[refParam],
                        senderName = it[senderName],
                        sentAt = it[sentAt],
                        savedAt = it[savedAt]
                    )
                }
                .toList()
        }
    }

    private fun toEbmsMessageDetail(it: ResultRow) =
        EbmsMessageDetail(
            requestId = it[requestId].toKotlinUuid(),
            readableId = it[readableId],
            cpaId = it[cpaId],
            conversationId = it[conversationId],
            messageId = it[messageId],
            refToMessageId = it[refToMessageId],
            fromPartyId = it[fromPartyId],
            fromRole = it[fromRole],
            toPartyId = it[toPartyId],
            toRole = it[toRole],
            service = it[service],
            action = it[action],
            refParam = it[refParam],
            senderName = it[senderName],
            sentAt = it[sentAt],
            savedAt = it[savedAt]
        )
}

private fun UpdateBuilder<*>.populateFrom(ebmsMessageDetail: EbmsMessageDetail) {
    this[readableId] = ebmsMessageDetail.generateReadableId()
    this[cpaId] = ebmsMessageDetail.cpaId
    this[conversationId] = ebmsMessageDetail.conversationId
    this[messageId] = ebmsMessageDetail.messageId
    this[refToMessageId] = ebmsMessageDetail.refToMessageId
    this[fromPartyId] = ebmsMessageDetail.fromPartyId
    this[fromRole] = ebmsMessageDetail.fromRole
    this[toPartyId] = ebmsMessageDetail.toPartyId
    this[toRole] = ebmsMessageDetail.toRole
    this[service] = ebmsMessageDetail.service
    this[action] = ebmsMessageDetail.action
    this[refParam] = ebmsMessageDetail.refParam
    this[senderName] = ebmsMessageDetail.senderName
    this[sentAt] = ebmsMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
    this[savedAt] = ebmsMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
}

private fun Query.applyReadableIdCpaIdMessageIdFilters(readableIdPattern: String = "", cpaIdPattern: String = "", messageIdPattern: String = "") {
    this.applyPatternFilter(readableIdPattern, readableId)
    this.applyPatternFilter(cpaIdPattern, cpaId.nullable())
    this.applyPatternFilter(messageIdPattern, messageId.nullable())
}

internal fun Query.applyRoleServiceActionFilters(role: String = "", service: String = "", action: String = "") {
    this.applyFilter(role, EbmsMessageDetailTable.fromRole)
    this.applyFilter(service, EbmsMessageDetailTable.service.nullable())
    this.applyFilter(action, EbmsMessageDetailTable.action.nullable())
}
