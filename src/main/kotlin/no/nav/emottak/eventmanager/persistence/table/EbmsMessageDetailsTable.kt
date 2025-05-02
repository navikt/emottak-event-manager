package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.UUID

object EbmsMessageDetailsTable : Table("ebms_message_details") {
    val requestId: Column<UUID> = uuid("request_id")
    val cpaId: Column<String> = varchar("cpa_id", 256)
    val conversationId: Column<String> = varchar("conversation_id", 256)
    val messageId: Column<String> = varchar("message_id", 256)
    val refToMessageId: Column<String?> = varchar("ref_to_message_id", 256).nullable()
    val fromPartyId: Column<String> = varchar("from_party_id", 256)
    val fromRole: Column<String?> = varchar("from_role", 256).nullable()
    val toPartyId: Column<String> = varchar("to_party_id", 256)
    val toRole: Column<String?> = varchar("to_role", 256).nullable()
    val service: Column<String> = varchar("service", 256)
    val action: Column<String> = varchar("action", 256)
    val refParam: Column<String?> = varchar("ref_param", 256).nullable()
    val sender: Column<String?> = varchar("sender", 256).nullable()
    val sentAt: Column<java.time.Instant?> = timestamp("sent_at").nullable()
    val savedAt: Column<java.time.Instant> = timestamp("saved_at")

    override val primaryKey = PrimaryKey(requestId)
}
