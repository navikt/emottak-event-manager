package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp

object ConversationStatusTable : Table("conversation_status") {
    val conversationId: Column<String> = varchar("conversation_id", 256)
    val createdAt: Column<java.time.Instant> = timestamp("created_at")
        .defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp)
    val latestStatus: Column<EventStatusEnum> = eventStatusEnumeration("latest_status")
    val statusAt: Column<java.time.Instant> = timestamp("status_at")
        .defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp)

    override val primaryKey = PrimaryKey(conversationId)
}
