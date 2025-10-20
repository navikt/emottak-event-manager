package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime
import java.time.LocalDateTime

object DistinctRolesServicesActionsTable : Table("distict_roles_services_actions") {
    val roles: Column<String?> = text("roles").nullable()
    val services: Column<String?> = text("services").nullable()
    val actions: Column<String?> = text("actions").nullable()
    val refreshedAt: Column<LocalDateTime> = datetime("refreshed_at")
}
