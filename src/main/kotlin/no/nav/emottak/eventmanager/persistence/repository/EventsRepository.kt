package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EventsTable
import no.nav.emottak.eventmanager.persistence.table.EventsTable.contentId
import no.nav.emottak.eventmanager.persistence.table.EventsTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.EventsTable.eventData
import no.nav.emottak.eventmanager.persistence.table.EventsTable.eventTypeId
import no.nav.emottak.eventmanager.persistence.table.EventsTable.messageId
import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import no.nav.emottak.eventmanager.persistence.table.EventsTable.requestId as requestIdColumn

class EventsRepository(private val database: Database) {

    fun insert(event: Event): Uuid {
        val newEventId = UUID.randomUUID()
        transaction(database.db) {
            EventsTable.insert {
                it[eventId] = newEventId
                it[eventTypeId] = event.eventType.value
                it[requestId] = event.requestId.toJavaUuid()
                it[contentId] = event.contentId
                it[messageId] = event.messageId
                it[eventData] = event.eventData?.let { jsonData ->
                    Json.decodeFromString<Map<String, String>>(jsonData)
                } ?: emptyMap()
                it[createdAt] = event.createdAt
            }
        }
        return newEventId.toKotlinUuid()
    }

    fun findEventById(eventId: Uuid): Event? {
        return transaction {
            EventsTable.select(EventsTable.columns)
                .where { EventsTable.eventId eq eventId.toJavaUuid() }
                .mapNotNull {
                    Event(
                        eventType = EventType.fromInt(it[eventTypeId]),
                        requestId = it[requestIdColumn].toKotlinUuid(),
                        contentId = it[contentId],
                        messageId = it[messageId],
                        eventData = Json.encodeToString(it[eventData]),
                        createdAt = it[createdAt]
                    )
                }
                .singleOrNull()
        }
    }

    fun findEventByRequestId(requestId: Uuid): List<Event> {
        return transaction {
            EventsTable.select(EventsTable.columns)
                .where { EventsTable.requestId eq requestId.toJavaUuid() }
                .mapNotNull {
                    Event(
                        eventType = EventType.fromInt(it[eventTypeId]),
                        requestId = it[requestIdColumn].toKotlinUuid(),
                        contentId = it[contentId],
                        messageId = it[messageId],
                        eventData = Json.encodeToString(it[eventData]),
                        createdAt = it[createdAt]
                    )
                }
                .toList()
        }
    }
}
