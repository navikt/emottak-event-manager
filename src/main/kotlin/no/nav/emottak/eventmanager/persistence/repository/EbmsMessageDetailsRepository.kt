package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.action
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.cpaId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.fromPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.fromRole
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.messageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.refParam
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.refToMessageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.requestId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.savedAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.sender
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.sentAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.service
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.toPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailsTable.toRole
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class EbmsMessageDetailsRepository(private val database: Database) {

    suspend fun insert(ebmsMessageDetails: EbmsMessageDetails): Uuid = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EbmsMessageDetailsTable.insert {
                it[requestId] = ebmsMessageDetails.requestId.toJavaUuid()
                it[cpaId] = ebmsMessageDetails.cpaId
                it[conversationId] = ebmsMessageDetails.conversationId
                it[messageId] = ebmsMessageDetails.messageId
                it[refToMessageId] = ebmsMessageDetails.refToMessageId
                it[fromPartyId] = ebmsMessageDetails.fromPartyId
                it[fromRole] = ebmsMessageDetails.fromRole
                it[toPartyId] = ebmsMessageDetails.toPartyId
                it[toRole] = ebmsMessageDetails.toRole
                it[service] = ebmsMessageDetails.service
                it[action] = ebmsMessageDetails.action
                it[refParam] = ebmsMessageDetails.refParam
                it[sender] = ebmsMessageDetails.sender
                it[sentAt] = ebmsMessageDetails.sentAt?.truncatedTo(ChronoUnit.MICROS)
                it[savedAt] = ebmsMessageDetails.savedAt.truncatedTo(ChronoUnit.MICROS)
            }
        }
        ebmsMessageDetails.requestId
    }

    suspend fun update(ebmsMessageDetails: EbmsMessageDetails): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val updatedRows = EbmsMessageDetailsTable
                .update({
                    requestId eq ebmsMessageDetails.requestId.toJavaUuid()
                }) {
                    it[cpaId] = ebmsMessageDetails.cpaId
                    it[conversationId] = ebmsMessageDetails.conversationId
                    it[messageId] = ebmsMessageDetails.messageId
                    it[refToMessageId] = ebmsMessageDetails.refToMessageId
                    it[fromPartyId] = ebmsMessageDetails.fromPartyId
                    it[fromRole] = ebmsMessageDetails.fromRole
                    it[toPartyId] = ebmsMessageDetails.toPartyId
                    it[toRole] = ebmsMessageDetails.toRole
                    it[service] = ebmsMessageDetails.service
                    it[action] = ebmsMessageDetails.action
                    it[refParam] = ebmsMessageDetails.refParam
                    it[sender] = ebmsMessageDetails.sender
                    it[sentAt] = ebmsMessageDetails.sentAt?.truncatedTo(ChronoUnit.MICROS)
                    it[savedAt] = ebmsMessageDetails.savedAt.truncatedTo(ChronoUnit.MICROS)
                }
            updatedRows > 0
        }
    }

    suspend fun findByRequestId(requestId: Uuid): EbmsMessageDetails? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where { EbmsMessageDetailsTable.requestId eq requestId.toJavaUuid() }
                .mapNotNull {
                    EbmsMessageDetails(
                        requestId = it[EbmsMessageDetailsTable.requestId].toKotlinUuid(),
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

    suspend fun findByRequestIds(requestIds: List<Uuid>): Map<Uuid, EbmsMessageDetails> = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where { requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    EbmsMessageDetails(
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

    suspend fun findByTimeInterval(from: Instant, to: Instant): List<EbmsMessageDetails> = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailsTable
                .select(EbmsMessageDetailsTable.columns)
                .where { savedAt.between(from, to) }
                .mapNotNull {
                    EbmsMessageDetails(
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
}
