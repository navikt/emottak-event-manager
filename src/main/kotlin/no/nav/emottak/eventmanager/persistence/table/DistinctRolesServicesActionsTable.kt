package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object DistinctRolesServicesActionsTable : Table("distict_roles_services_actions") {
    val roles: Column<String?> = text("roles").nullable()
    val services: Column<String?> = text("services").nullable()
    val actions: Column<String?> = text("actions").nullable()
    val refreshedAt: Column<Instant> = timestamp("refreshed_at")
}
