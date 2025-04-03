package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EventsTable
import no.nav.emottak.eventmanager.persistence.table.EventsTable.contentId
import no.nav.emottak.eventmanager.persistence.table.EventsTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.EventsTable.eventData
import no.nav.emottak.eventmanager.persistence.table.EventsTable.eventTypeId
import no.nav.emottak.eventmanager.persistence.table.EventsTable.messageId
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import no.nav.emottak.eventmanager.persistence.table.EventsTable.requestId as requestIdColumn

class EventsRepository(private val database: Database) {

    suspend fun insert(event: Event): Uuid = withContext(Dispatchers.IO) {
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
        newEventId.toKotlinUuid()
    }

    suspend fun findEventById(eventId: Uuid): Event? = withContext(Dispatchers.IO) {
        transaction {
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

    suspend fun findEventByRequestId(requestId: Uuid): List<Event> = withContext(Dispatchers.IO) {
        transaction {
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

    suspend fun findEventByTimeInterval(from: Instant, to: Instant): List<Event> = withContext(Dispatchers.IO) {
        transaction {
            EventsTable.select(EventsTable.columns)
                .where { createdAt.between(from, to) }
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
