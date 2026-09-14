package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.model.dto.PageDto
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EventTable
import no.nav.emottak.eventmanager.persistence.table.EventTable.contentId
import no.nav.emottak.eventmanager.persistence.table.EventTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.EventTable.createdAt
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventData
import no.nav.emottak.eventmanager.persistence.table.EventTable.eventTypeId
import no.nav.emottak.eventmanager.persistence.table.EventTable.messageId
import no.nav.emottak.utils.kafka.model.EventType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.not
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class EventRepository(private val database: Database) {

    suspend fun insert(event: Event): Uuid = withContext(Dispatchers.IO) {
        val newEventId = UUID.randomUUID()
        transaction(database.db) {
            EventTable.insert {
                it[eventId] = newEventId
                it[eventTypeId] = event.eventType.value
                it[EventTable.requestId] = event.requestId.toJavaUuid()
                it[contentId] = event.contentId
                it[messageId] = event.messageId
                it[eventData] = Json.decodeFromString<Map<String, String>>(event.eventData)
                it[createdAt] = event.createdAt.truncatedTo(ChronoUnit.MICROS)
                it[conversationId] = event.conversationId
            }
        }
        newEventId.toKotlinUuid()
    }

    suspend fun findById(eventId: Uuid): Event? = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { EventTable.eventId eq eventId.toJavaUuid() }
                .mapNotNull {
                    toEvent(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByRequestId(requestId: Uuid): List<Event> = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { EventTable.requestId eq requestId.toJavaUuid() }
                .mapNotNull {
                    toEvent(it)
                }
                .toList()
        }
    }

    suspend fun findByRequestIds(requestIds: List<Uuid>): List<Event> = withContext(Dispatchers.IO) {
        transaction {
            EventTable.select(EventTable.columns)
                .where { EventTable.requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    toEvent(it)
                }
                .toList()
        }
    }

    suspend fun findByTimeInterval(
        from: Instant,
        to: Instant,
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): PageDto<Event> = withContext(Dispatchers.IO) {
        transaction {
            val query = EventTable
                .join(EbmsMessageDetailTable, JoinType.INNER, EventTable.requestId, EbmsMessageDetailTable.requestId)
                .select(EventTable.columns)
                .where { createdAt.between(from, to) }
                .andWhere { not(conversationId.isNullOrEmpty()) }
                .andWhere { not(EbmsMessageDetailTable.conversationId.isNullOrEmpty()) }
                .apply {
                    this.applyRoleServiceActionFilters(role, service, action)
                }
            val totalCount = query.count()
            val list = query.apply {
                if (pageable != null) {
                    this.limit(pageable.pageSize).offset(pageable.offset)
                    this.orderBy(createdAt, pageable.getSortOrder())
                }
            }
                .mapNotNull {
                    toEvent(it)
                }
                .toList()
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, list.size)
            PageDto(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, list)
        }
    }

    private fun toEvent(it: ResultRow) =
        Event(
            eventType = EventType.fromInt(it[eventTypeId]),
            requestId = it[EventTable.requestId].toKotlinUuid(),
            contentId = it[contentId],
            messageId = it[messageId],
            eventData = Json.encodeToString(it[eventData]),
            createdAt = it[createdAt],
            conversationId = it[conversationId]
        )
}
