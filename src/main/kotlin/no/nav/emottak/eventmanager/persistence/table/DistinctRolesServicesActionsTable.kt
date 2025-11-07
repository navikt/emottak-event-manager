package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DistinctRolesServicesActionsTable : Table("distict_roles_services_actions") {
    val id: Column<Int> = integer("id")
    val roles: Column<String> = text("roles")
    val services: Column<String> = text("services")
    val actions: Column<String> = text("actions")
    val refreshedAt: Column<Instant> = timestamp("refreshed_at")

    override val primaryKey = PrimaryKey(id)
}
