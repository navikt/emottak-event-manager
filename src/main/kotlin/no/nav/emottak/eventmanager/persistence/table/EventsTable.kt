package no.nav.emottak.eventmanager.persistence.table

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.json
import java.util.UUID

object EventsTable : Table("events") {
    val eventId: Column<UUID> = uuid("event_id")
    val eventTypeId: Column<Int> = integer("event_type_id")
        .references(EventTypesTable.eventTypeId)
    val requestId: Column<UUID> = uuid("request_id")
    val contentId: Column<String?> = varchar("content_id", 256).nullable()
    val messageId: Column<String> = varchar("message_id", 256)
    val eventData: Column<Map<String, String>> = json(
        "event_data",
        Json,
        MapSerializer(String.serializer(), String.serializer())
    )

    val createdAt: Column<java.time.Instant> = timestamp("created_at")
        .defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentTimestamp())

    override val primaryKey = PrimaryKey(eventId)
}
