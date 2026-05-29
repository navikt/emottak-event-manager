package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.dto.DistinctRolesServicesActionsDto
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.DistinctRolesServicesActionsTable
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class DistinctRolesServicesActionsRepository(private val database: Database) {

    @Volatile private var roles: Set<String> = emptySet()

    @Volatile private var services: Set<String> = emptySet()

    @Volatile private var actions: Set<String> = emptySet()

    suspend fun initialize() = loadFromDb()

    fun getAll(): DistinctRolesServicesActionsDto = DistinctRolesServicesActionsDto(
        roles = roles.sortedWith(String.CASE_INSENSITIVE_ORDER),
        services = services.sortedWith(String.CASE_INSENSITIVE_ORDER),
        actions = actions.sortedWith(String.CASE_INSENSITIVE_ORDER)
    )

    suspend fun addIfAbsent(role: String?, service: String, action: String) = withContext(Dispatchers.IO) {
        val isNew = (role != null && role !in roles) || service !in services || action !in actions
        if (isNew) {
            transaction(database.db) {
                role?.let { r ->
                    DistinctRolesServicesActionsTable.insertIgnore {
                        it[type] = "role"
                        it[value] = r
                    }
                }
                DistinctRolesServicesActionsTable.insertIgnore {
                    it[type] = "service"
                    it[value] = service
                }
                DistinctRolesServicesActionsTable.insertIgnore {
                    it[type] = "action"
                    it[value] = action
                }
            }
            loadFromDb()
        }
    }

    private suspend fun loadFromDb() = withContext(Dispatchers.IO) {
        val newRoles = mutableSetOf<String>()
        val newServices = mutableSetOf<String>()
        val newActions = mutableSetOf<String>()
        transaction(database.db) {
            DistinctRolesServicesActionsTable.selectAll().forEach { row ->
                when (row[DistinctRolesServicesActionsTable.type]) {
                    "role" -> newRoles.add(row[DistinctRolesServicesActionsTable.value])
                    "service" -> newServices.add(row[DistinctRolesServicesActionsTable.value])
                    "action" -> newActions.add(row[DistinctRolesServicesActionsTable.value])
                }
            }
        }
        roles = newRoles
        services = newServices
        actions = newActions
    }
}
