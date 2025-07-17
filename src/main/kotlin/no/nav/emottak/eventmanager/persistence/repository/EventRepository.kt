package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EventTable
import no.nav.emottak.eventmanager.persistence.table.EventTable.contentId
import no.nav.emottak.eventmanager.persistence.table.EventTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventData
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventTypeId
import no.nav.emottak.eventmanager.persistence.table.EventTable.messageId
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid
import no.nav.emottak.eventmanager.persistence.table.EventTable.requestId as requestIdColumn

class EventRepository(private val database: Database) {

    suspend fun insert(event: Event): Uuid = withContext(Dispatchers.IO) {
        val newEventId = UUID.randomUUID()
        transaction(database.db) {
            EventTable.insert {
                it[eventId] = newEventId
                it[eventTypeId] = event.eventType.value
                it[requestId] = event.requestId.toJavaUuid()
                it[contentId] = event.contentId
                it[messageId] = event.messageId
                it[eventData] = Json.decodeFromString<Map<String, String>>(event.eventData)
                it[createdAt] = event.createdAt.truncatedTo(ChronoUnit.MICROS)
            }
        }
        newEventId.toKotlinUuid()
    }

    suspend fun findEventById(eventId: Uuid): Event? = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { EventTable.eventId eq eventId.toJavaUuid() }
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

    suspend fun findEventsByRequestId(requestId: Uuid): List<Event> = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { EventTable.requestId eq requestId.toJavaUuid() }
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

    suspend fun findEventsByRequestIds(requestIds: List<Uuid>): List<Event> = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { requestIdColumn.inList(requestIds.map { it.toJavaUuid() }) }
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
            EventTable.select(EventTable.columns)
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
