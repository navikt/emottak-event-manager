package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EventTypeTable
import no.nav.emottak.eventmanager.persistence.table.EventTypeTable.description
import org.jetbrains.exposed.sql.transactions.transaction

class EventTypeRepository(private val database: Database) {

    suspend fun findEventTypeById(eventTypeId: Int): EventType? = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EventTypeTable.select(EventTypeTable.columns)
                .where { EventTypeTable.eventTypeId eq eventTypeId }
                .mapNotNull {
                    EventType(
                        eventTypeId = it[EventTypeTable.eventTypeId],
                        description = it[description],
                        status = it[EventTypeTable.status]
                    )
                }
                .singleOrNull()
        }
    }

    suspend fun findEventTypesByIds(eventTypeIds: List<Int>): List<EventType> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EventTypeTable.select(EventTypeTable.columns)
                .where { EventTypeTable.eventTypeId.inList(eventTypeIds) }
                .mapNotNull {
                    EventType(
                        eventTypeId = it[EventTypeTable.eventTypeId],
                        description = it[description],
                        status = it[EventTypeTable.status]
                    )
                }
                .toList()
        }
    }
}
