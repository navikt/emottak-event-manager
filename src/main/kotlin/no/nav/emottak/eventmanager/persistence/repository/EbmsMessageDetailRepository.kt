package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.Conversation
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.action
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.conversationId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.cpaId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.fromRole
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.messageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.readableId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refParam
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.refToMessageId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.requestId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.savedAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.senderName
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.sentAt
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.service
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toPartyId
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable.toRole
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import no.nav.emottak.eventmanager.persistence.table.EventTable
import no.nav.emottak.eventmanager.persistence.table.EventTypeTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnSet
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.VarCharColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.castTo
import org.jetbrains.exposed.sql.countDistinct
import org.jetbrains.exposed.sql.groupConcat
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.statements.UpdateBuilder
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.text.isNotEmpty
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

class EbmsMessageDetailRepository(private val database: Database) {
    private val log = LoggerFactory.getLogger(EbmsMessageDetailRepository::class.java)

    suspend fun insert(ebmsMessageDetail: EbmsMessageDetail): Uuid = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EbmsMessageDetailTable.insert {
                it[requestId] = ebmsMessageDetail.requestId.toJavaUuid()
                it.populateFrom(ebmsMessageDetail)
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
                    it.populateFrom(ebmsMessageDetail)
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
                    toEbmsMessageDetail(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByReadableId(readableId: String): EbmsMessageDetail? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { EbmsMessageDetailTable.readableId eq readableId }
                .mapNotNull {
                    toEbmsMessageDetail(it)
                }
                .singleOrNull()
        }
    }

    suspend fun findByReadableIdPattern(readableIdPattern: String, limit: Int? = null): EbmsMessageDetail? = withContext(Dispatchers.IO) {
        transaction {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where { readableId.lowerCase() like "%$readableIdPattern%".lowercase() }
                .apply {
                    if (limit != null) this.limit(limit)
                }
                .mapNotNull {
                    toEbmsMessageDetail(it)
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
                    toEbmsMessageDetail(it)
                }
                .toList()
                .associateBy { it.requestId }
        }
    }

    suspend fun findByTimeInterval(
        from: Instant,
        to: Instant,
        readableIdPattern: String = "",
        cpaIdPattern: String = "",
        messageIdPattern: String = "",
        role: String = "",
        service: String = "",
        action: String = "",
        pageable: Pageable? = null
    ): Page<EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction {
            val totalCount = EbmsMessageDetailTable.select(savedAt).where { savedAt.between(from, to) }
                .apply {
                    this.applyReadableIdCpaIdMessageIdFilters(readableIdPattern, cpaIdPattern, messageIdPattern)
                    this.applyRoleServiceActionFilters(role, service, action)
                }.count()
            val list =
                EbmsMessageDetailTable
                    .select(EbmsMessageDetailTable.columns)
                    .where { savedAt.between(from, to) }
                    .apply {
                        this.applyReadableIdCpaIdMessageIdFilters(readableIdPattern, cpaIdPattern, messageIdPattern)
                        this.applyRoleServiceActionFilters(role, service, action)
                        this.applyPagableLimit(pageable, savedAt)
                    }
                    .mapNotNull {
                        toEbmsMessageDetail(it)
                    }
                    .toList()
            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, list.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, list)
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

    suspend fun findRelatedReadableIds(conversationIds: List<String>, requestIds: List<Uuid>): Map<Uuid, String?> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            val relatedReadableIdsColumn = readableId.groupConcat(",").alias("related_readable_ids")

            val subQuery = EbmsMessageDetailTable
                .select(conversationId, relatedReadableIdsColumn)
                .where { conversationId.inList(conversationIds) }
                .groupBy(conversationId)
                .alias("related")

            EbmsMessageDetailTable
                .join(subQuery, JoinType.INNER, conversationId, subQuery[conversationId])
                .select(requestId, subQuery[relatedReadableIdsColumn])
                .where { requestId.inList(requestIds.map { it.toJavaUuid() }) }
                .mapNotNull {
                    Pair(it[requestId].toKotlinUuid(), it[subQuery[relatedReadableIdsColumn]])
                }
                .toMap()
        }
    }

    suspend fun findByMessageIdConversationIdAndCpaId(
        messageId: String,
        conversationId: String,
        cpaId: String
    ): List<EbmsMessageDetail> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            EbmsMessageDetailTable
                .select(EbmsMessageDetailTable.columns)
                .where {
                    (EbmsMessageDetailTable.messageId eq messageId) and
                        (EbmsMessageDetailTable.conversationId eq conversationId) and
                        (EbmsMessageDetailTable.cpaId eq cpaId)
                }
                .mapNotNull {
                    EbmsMessageDetail(
                        requestId = it[requestId].toKotlinUuid(),
                        readableId = it[readableId],
                        cpaId = it[EbmsMessageDetailTable.cpaId],
                        conversationId = it[EbmsMessageDetailTable.conversationId],
                        messageId = it[EbmsMessageDetailTable.messageId],
                        refToMessageId = it[refToMessageId],
                        fromPartyId = it[fromPartyId],
                        fromRole = it[fromRole],
                        toPartyId = it[toPartyId],
                        toRole = it[toRole],
                        service = it[service],
                        action = it[action],
                        refParam = it[refParam],
                        senderName = it[senderName],
                        sentAt = it[sentAt],
                        savedAt = it[savedAt]
                    )
                }
                .toList()
        }
    }

    suspend fun findConversationsByTimeInterval(
        from: Instant,
        to: Instant,
        readableIdPattern: String = "",
        cpaIdPattern: String = "",
        service: String = "",
        statuses: List<EventStatusEnum> = listOf(ERROR, INFORMATION, PROCESSING_COMPLETED),
        pageable: Pageable? = null
    ): Page<Conversation> = withContext(Dispatchers.IO) {
        transaction(database.db) {
            // 1) Siste status pr conversation_id innenfor angitt tidsperiode:
            log.debug("Creating latest status pr distinct conversation subquery...")
            val statusPrConversationAlias = getConversationsQuery(from, to, readableIdPattern, cpaIdPattern, service)
                .alias("latest_status_pr_conversation")

            /* 2) Telle antall conversation_id'er hvor siste status er forespurt status (i det gitte tidsrommet):
            SELECT count(conversation_id) FROM statusPrConversationAlias
            WHERE latest_status IN ('Feil', 'Informasjon');
            */
            log.debug("Counting distinct conversations with prefered statuses...")
            val countQuery = statusPrConversationAlias
                .select(statusPrConversationAlias[conversationId].countDistinct())
                .where {
                    statusPrConversationAlias[EventTypeTable.status]
                        .castTo<String>(VarCharColumnType()).inList(statuses.map { it.toString() })
                }
                .single()

            val totalCount = countQuery[statusPrConversationAlias[conversationId].countDistinct()]
            log.debug("Total distinct conversations: $totalCount")

            // 3) Hente alle request_id'er til conversation_id'er fra SUBQUERY:
            log.debug("Getting all conversationIds for page {}...", pageable?.pageNumber ?: 1)
            val conversationIds = getConversationsQuery(from, to, readableIdPattern, cpaIdPattern, service, selectConversationIdOnly = true, pageable)
                .map { it[conversationId] }
            log.debug("A list of {} conversationIds was returned.", conversationIds.size)

            // 4) Hente alle messagedetails (med siste status) til relevante conversationIds:
            log.debug("Getting all MessageDetails for given conversationIds...")
            val conversationsMap = getLatestStatusPrRequestQuery(conversationIds)
                .map {
                    toEbmsMessageDetail(it).copy(
                        latestEventAt = it[EventTable.createdAt],
                        latestEventStatus = it[EventTypeTable.status]
                    )
                }
                .groupBy { it.conversationId }
            log.debug("A map with {} conversations containing a total of {} MessageDetails was returned.", conversationsMap.size, conversationsMap.flatMap { (_, value) -> value }.size)

            val conversationList = conversationIds.mapNotNull { conversationId ->
                val msgList = conversationsMap[conversationId]?.sortedByDescending { it.latestEventAt } ?: return@mapNotNull null
                Conversation(
                    messageDetails = msgList.sortedBy { it.savedAt },
                    createdAt = msgList.last().savedAt,
                    latestEventAt = msgList.first().latestEventAt!!,
                    latestEventStatus = msgList.first().latestEventStatus!!
                )
            }

            var returnPageable = pageable
            if (returnPageable == null) returnPageable = Pageable(1, conversationList.size)
            Page(returnPageable.pageNumber, returnPageable.pageSize, returnPageable.sort, totalCount, conversationList)
        }
    }

    private fun toEbmsMessageDetail(it: ResultRow) =
        EbmsMessageDetail(
            requestId = it[requestId].toKotlinUuid(),
            readableId = it[readableId],
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
            senderName = it[senderName],
            sentAt = it[sentAt],
            savedAt = it[savedAt]
        )

    private fun getConversationsQuery(
        from: Instant,
        to: Instant,
        readableIdPattern: String = "",
        cpaIdPattern: String = "",
        service: String = "",
        selectConversationIdOnly: Boolean = false,
        pageable: Pageable? = null
    ): Query {
        /*
            SELECT DISTINCT ON (m.conversation_id)
                m.conversation_id  AS conversation_id,
                e.created_at       AS latest_event_timestamp,
                t.status           AS latest_status
            FROM   events e
                INNER JOIN   ebms_message_details m ON m.request_id = e.request_id
                INNER JOIN   event_types          t ON t.event_type_id = e.event_type_id
            WHERE  m.saved_at BETWEEN '2026-01-28T12:00:00.000Z' AND '2026-01-28T13:00:00.000Z'
            ORDER  BY m.conversation_id, e.created_at DESC;
        */
        return EventTable
            .join(EbmsMessageDetailTable, JoinType.INNER, onColumn = requestId, otherColumn = EventTable.requestId)
            .join(
                EventTypeTable,
                JoinType.INNER,
                onColumn = EventTypeTable.eventTypeId,
                otherColumn = EventTable.eventTypeId
            )
            .setSelectFields(selectConversationIdOnly)
            .withDistinctOn(conversationId)
            .where { savedAt.between(from, to) }
            .apply {
                this.applyReadableIdCpaIdMessageIdFilters(readableIdPattern, cpaIdPattern)
                if (service.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.service eq service }
                this.applyPagableLimit(pageable, savedAt)
            }
            .orderBy(
                conversationId to SortOrder.ASC,
                EventTable.createdAt to SortOrder.DESC
            )
    }

    private fun getLatestStatusPrRequestQuery(conversationIds: List<String>): Query {
        /*
        SELECT DISTINCT ON (e.request_id)
            m.*,
            e.created_at       AS latest_event_timestamp,
            t.status           AS latest_status
        FROM   events e
                   INNER JOIN   ebms_message_details m ON m.request_id = e.request_id
                   INNER JOIN   event_types          t ON t.event_type_id = e.event_type_id
        WHERE  e.conversation_id IN ('2224c91e-847c-4262-b481-9306636780d0', '191fdbe4-53c6-4028-a98c-d162abac0965')
        ORDER  BY e.request_id, e.created_at DESC;
        */
        return EventTable
            .join(EbmsMessageDetailTable, JoinType.INNER, onColumn = requestId, otherColumn = EventTable.requestId)
            .join(EventTypeTable, JoinType.INNER, onColumn = EventTypeTable.eventTypeId, otherColumn = EventTable.eventTypeId)
            .select(EbmsMessageDetailTable.columns + EventTable.createdAt + EventTypeTable.status)
            .withDistinctOn(EventTable.requestId)
            .where { conversationId inList conversationIds }
            .orderBy(
                EventTable.requestId to SortOrder.ASC,
                EventTable.createdAt to SortOrder.DESC
            )
    }
}

private fun UpdateBuilder<*>.populateFrom(ebmsMessageDetail: EbmsMessageDetail) {
    this[readableId] = ebmsMessageDetail.generateReadableId()
    this[cpaId] = ebmsMessageDetail.cpaId
    this[conversationId] = ebmsMessageDetail.conversationId
    this[messageId] = ebmsMessageDetail.messageId
    this[refToMessageId] = ebmsMessageDetail.refToMessageId
    this[fromPartyId] = ebmsMessageDetail.fromPartyId
    this[fromRole] = ebmsMessageDetail.fromRole
    this[toPartyId] = ebmsMessageDetail.toPartyId
    this[toRole] = ebmsMessageDetail.toRole
    this[service] = ebmsMessageDetail.service
    this[action] = ebmsMessageDetail.action
    this[refParam] = ebmsMessageDetail.refParam
    this[senderName] = ebmsMessageDetail.senderName
    this[sentAt] = ebmsMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
    this[savedAt] = ebmsMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
}

private fun Query.applyReadableIdCpaIdMessageIdFilters(readableIdPattern: String = "", cpaIdPattern: String = "", messageIdPattern: String = "") {
    if (readableIdPattern.isNotBlank()) this.andWhere { readableId.lowerCase() like "%$readableIdPattern%".lowercase() }
    if (cpaIdPattern.isNotBlank()) this.andWhere { cpaId.lowerCase() like "%$cpaIdPattern%".lowercase() }
    if (messageIdPattern.isNotBlank()) this.andWhere { messageId.lowerCase() like "%$messageIdPattern%".lowercase() }
}

internal fun Query.applyRoleServiceActionFilters(role: String = "", service: String = "", action: String = "") {
    if (role.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.fromRole eq role }
    if (service.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.service eq service }
    if (action.isNotEmpty()) this.andWhere { EbmsMessageDetailTable.action eq action }
}

internal fun Query.applyPagableLimit(pageable: Pageable?, orderByColumn: Column<Instant>) {
    if (pageable != null) {
        this.limit(pageable.pageSize)
            .offset(pageable.offset)
            .orderBy(orderByColumn, pageable.getSortOrder())
    }
}

private fun ColumnSet.setSelectFields(selectConversationIdOnly: Boolean) =
    if (selectConversationIdOnly) {
        this.select(conversationId)
    } else {
        this.select(conversationId, EventTable.createdAt, EventTypeTable.status)
    }
