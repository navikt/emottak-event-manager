package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

object EventTypeTable : Table("event_types") {
    val eventTypeId: Column<Int> = integer("event_type_id")
    val description: Column<String> = varchar("description", 256)
    val status: Column<EventStatusEnum> = eventStatusEnumeration("status")

    override val primaryKey = PrimaryKey(eventTypeId)
}

fun Table.eventStatusEnumeration(name: String) = customEnumeration(
    name = name,
    sql = "event_status",
    fromDb = { EventStatusEnum.fromDbValue(it.toString()) },
    toDb = {
        PGobject().apply {
            type = "event_status"
            value = it.dbValue
        }
    }
)
