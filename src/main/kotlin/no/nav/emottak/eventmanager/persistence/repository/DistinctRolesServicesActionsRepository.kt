package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.DistinctRolesServicesActions
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.DistinctRolesServicesActionsTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class DistinctRolesServicesActionsRepository(private val database: Database) {

    suspend fun getDistinctRolesServicesActions(): DistinctRolesServicesActions? = withContext(Dispatchers.IO) {
        transaction(database.db) {
            DistinctRolesServicesActionsTable
                .selectAll()
                .singleOrNull()
                ?.let { row ->
                    val roles = nullableStringToList(row[DistinctRolesServicesActionsTable.roles])
                    val services = nullableStringToList(row[DistinctRolesServicesActionsTable.services])
                    val actions = nullableStringToList(row[DistinctRolesServicesActionsTable.actions])
                    if (roles == null || services == null || actions == null) {
                        null
                    } else {
                        DistinctRolesServicesActions(
                            roles = roles.sorted(),
                            services = services.sorted(),
                            actions = actions.sorted(),
                            refreshedAt = row[DistinctRolesServicesActionsTable.refreshedAt]
                        )
                    }
                }
        }
    }

    suspend fun refreshDistinctRolesServicesActions() = withContext(Dispatchers.IO) {
        transaction(database.db) {
            exec("REFRESH MATERIALIZED VIEW CONCURRENTLY distict_roles_services_actions")
        }
    }

    private fun nullableStringToList(value: String?, delimeters: String = ",") = value
        ?.split(delimeters)
        ?.filter { it.isNotBlank() }
}
