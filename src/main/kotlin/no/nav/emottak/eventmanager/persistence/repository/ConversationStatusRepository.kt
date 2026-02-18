package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.ConversationStatus
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.latestStatus
import no.nav.emottak.eventmanager.persistence.table.ConversationStatusTable.statusAt
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.utils.common.nowOsloToInstant
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.time.temporal.ChronoUnit

class ConversationStatusRepository(private val database: Database) {

    suspend fun insert(id: String): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            ConversationStatusTable.insertIgnore {
                val now = nowOsloToInstant().truncatedTo(ChronoUnit.MICROS)
                it[conversationId] = id
                it[createdAt] = now
                it[latestStatus] = EventStatusEnum.INFORMATION
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
}
