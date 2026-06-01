package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table

object DistinctRolesServicesActionsTable : Table("distinct_roles_services_actions") {
    val type: Column<String> = varchar("type", 10)
    val value: Column<String> = text("value")

    override val primaryKey = PrimaryKey(type, value)
}
