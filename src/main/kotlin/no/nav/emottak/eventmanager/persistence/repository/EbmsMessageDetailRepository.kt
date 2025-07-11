package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.action
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.cpaId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromRole
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.messageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refParam
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refToMessageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.requestId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.savedAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.sender
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.sentAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.service
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toRole
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.groupConcat
import org.jetbrains.exposed.sql.insert
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
                it[cpaId] = ebmsMessageDetail.cpaId
                it[conversationId] = ebmsMessageDetail.conversationId
                it[messageId] = ebmsMessageDetail.messageId
                it[refToMessageId] = ebmsMessageDetail.refToMessageId
                it[fromPartyId] = ebmsMessageDetail.fromPartyId
                it[fromRole] = ebmsMessageDetail.fromRole
                it[toPartyId] = ebmsMessageDetail.toPartyId
                it[toRole] = ebmsMessageDetail.toRole
                it[service] = ebmsMessageDetail.service
                it[action] = ebmsMessageDetail.action
                it[refParam] = ebmsMessageDetail.refParam
                it[sender] = ebmsMessageDetail.sender
                it[sentAt] = ebmsMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
                it[savedAt] = ebmsMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
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
                    it[cpaId] = ebmsMessageDetail.cpaId
                    it[conversationId] = ebmsMessageDetail.conversationId
                    it[messageId] = ebmsMessageDetail.messageId
                    it[refToMessageId] = ebmsMessageDetail.refToMessageId
                    it[fromPartyId] = ebmsMessageDetail.fromPartyId
                    it[fromRole] = ebmsMessageDetail.fromRole
                    it[toPartyId] = ebmsMessageDetail.toPartyId
                    it[toRole] = ebmsMessageDetail.toRole
                    it[service] = ebmsMessageDetail.service
                    it[action] = ebmsMessageDetail.action
                    it[refParam] = ebmsMessageDetail.refParam
                    it[sender] = ebmsMessageDetail.sender
                    it[sentAt] = ebmsMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
                    it[savedAt] = ebmsMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
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
                    EbmsMessageDetail(
                        requestId = it[EbmsMessageDetailTable.requestId].toKotlinUuid(),
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
                        sender = it[sender],
                        sentAt = it[sentAt],
                        savedAt = it[savedAt]
                    )
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
                    EbmsMessageDetail(
                        requestId = it[requestId].toKotlinUuid(),
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
                        sender = it[sender],
                        sentAt = it[sentAt],
                        savedAt = it[savedAt]
                    )
                }
                .toList()
                .associateBy { it.requestId }
        }
    }

    suspend fun findByTimeInterval(from: Instant, to: Instant): List<EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { savedAt.between(from, to) }
                .mapNotNull {
                    EbmsMessageDetail(
                        requestId = it[requestId].toKotlinUuid(),
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
                        sender = it[sender],
                        sentAt = it[sentAt],
                        savedAt = it[savedAt]
                    )
                }
                .toList()
        }
    }

    suspend fun findRelatedRequestIds(requestIds: List<Uuid>): Map<Uuid, String> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val relatedRequestIdsColumn = requestId.castTo<String>(
                columnType = TextColumnType()
            ).groupConcat(",").alias("related_request_ids")

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
}
