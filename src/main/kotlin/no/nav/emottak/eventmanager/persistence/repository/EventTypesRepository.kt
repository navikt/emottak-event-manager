package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EventTypesTable
import no.nav.emottak.eventmanager.persistence.table.EventTypesTable.description
import no.nav.emottak.eventmanager.persistence.table.EventsTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction

class EventTypesRepository(private val database: Database) {

    suspend fun findEventTypeById(eventTypeId: Int): EventType? = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EventTypesTable.select(EventTypesTable.columns)
                .where { EventTypesTable.eventTypeId eq eventTypeId }
                .mapNotNull {
                    EventType(
                        eventTypeId = it[EventTypesTable.eventTypeId],
                        description = it[description],
                        status = it[EventTypesTable.status]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun findEventTypesByIds(eventTypeIds: List<Int>): List<EventType> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EventTypesTable.select(EventTypesTable.columns)
                .where { EventTypesTable.eventTypeId.inList(eventTypeIds) }
                .mapNotNull {
                    EventType(
                        eventTypeId = it[EventTypesTable.eventTypeId],
                        description = it[description],
                        status = it[EventTypesTable.status]
                    )
                }
                .toList()
        }
    }
}
