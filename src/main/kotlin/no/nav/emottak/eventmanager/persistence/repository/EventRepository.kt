package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EventTable
import no.nav.emottak.eventmanager.persistence.table.EventTable.contentId
import no.nav.emottak.eventmanager.persistence.table.EventTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventData
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventTypeId
import no.nav.emottak.eventmanager.persistence.table.EventTable.messageId
import no.nav.emottak.utils.kafka.model.EventType
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.andWhere
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

    suspend fun findById(eventId: Uuid): Event? = withContext(Dispatchers.IO) {
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

    suspend fun findByRequestId(requestId: Uuid): List<Event> = withContext(Dispatchers.IO) {
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

    suspend fun findByRequestIds(requestIds: List<Uuid>): List<Event> = withContext(Dispatchers.IO) {
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

    suspend fun findByTimeInterval(from: Instant, to: Instant, pageable: Pageable? = null): Page<Event> = withContext(Dispatchers.IO) {
        transaction {
            val totalCount = EventTable.select(createdAt).where { createdAt.between(from, to) }.count()
            val list =
                EventTable.select(EventTable.columns)
                    .where { createdAt.between(from, to) }
                    .orderBy(createdAt, SortOrder.ASC)
                    .apply {
                        if (pageable != null) this.limit(pageable.pageSize, pageable.offset)
                    }
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
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, list.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, totalCount, list)
        }
    }

    suspend fun findByTimeIntervalJoinMessageDetail(
        from: Instant,
        to: Instant,
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): Page<Event> = withContext(Dispatchers.IO) {
        transaction {
            val totalCount = EventTable.select(createdAt).where { createdAt.between(from, to) }.count()
            val list =
                EventTable
                    .join(EbmsMessageDetailTable, JoinType.LEFT, EventTable.requestId, EbmsMessageDetailTable.requestId)
                    .select(EventTable.columns)
                    .where { createdAt.between(from, to) }
                    .apply {
                        if (role.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.fromRole eq role }
                        if (service.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.service eq service }
                        if (action.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.action eq action }
                        if (pageable != null) this.limit(pageable.pageSize, pageable.offset)
                    }
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
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, list.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, totalCount, list)
        }
    }
}
