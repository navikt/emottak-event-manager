package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object EventTypeTable : Table("event_types") {
    val eventTypeId: Column<Int> = integer("event_type_id")
    val description: Column<String> = varchar("description", 256)
    val status: Column<EventStatusEnum> = customEnumeration(
        "status",
        "event_status",
        { EventStatusEnum.fromDbValue(it.toString()) },
        { it.toString() }
    )

    override val primaryKey = PrimaryKey(eventTypeId)
}
