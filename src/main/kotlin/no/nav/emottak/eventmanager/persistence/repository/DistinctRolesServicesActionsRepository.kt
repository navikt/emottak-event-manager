package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.DistinctRolesServicesActions
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.DistinctRolesServicesActionsTable
import no.nav.emottak.eventmanager.persistence.table.EbmsMessageDetailTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.alias
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.stringLiteral
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.upsert
import java.time.Instant
import kotlin.collections.sorted

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

    suspend fun refreshDistinctRolesServicesActions(): DistinctRolesServicesActions = withContext(Dispatchers.IO) {
        transaction(database.db) {
            /*
            SELECT string_agg(distinct from_role::text, ',') AS roles,
                   string_agg(distinct service::text, ',') AS services,
                   string_agg(distinct action::text, ',') AS actions,
                   now() AS refreshed_at
            FROM (SELECT "from_role", "service", "action" FROM "ebms_message_details") sub;
             */
            val rolesAlias = StringAggDistinct(EbmsMessageDetailTable.fromRole).alias("roles")
            val servicesAlias = StringAggDistinct(EbmsMessageDetailTable.service).alias("services")
            val actionsAlias = StringAggDistinct(EbmsMessageDetailTable.action).alias("actions")
            val refreshedAlias = CurrentTimestamp<Instant>().alias("refreshed_at")

            val distinctValues = EbmsMessageDetailTable
                .select(
                    rolesAlias,
                    servicesAlias,
                    actionsAlias,
                    refreshedAlias
                ).single()

            val roles = distinctValues[rolesAlias]
            val services = distinctValues[servicesAlias]
            val actions = distinctValues[actionsAlias]
            val refreshedAt = distinctValues[refreshedAlias]

            // Insert or update the existing row in the table:
            DistinctRolesServicesActionsTable.upsert {
                it[DistinctRolesServicesActionsTable.id] = 1
                it[DistinctRolesServicesActionsTable.roles] = roles
                it[DistinctRolesServicesActionsTable.services] = services
                it[DistinctRolesServicesActionsTable.actions] = actions
                it[DistinctRolesServicesActionsTable.refreshedAt] = refreshedAt
            }

            DistinctRolesServicesActions(
                roles = roles.split(",").sorted(),
                services = services.split(",").sorted(),
                actions = actions.split(",").sorted(),
                refreshedAt = refreshedAt
            )
        }
    }

    private fun nullableStringToList(value: String?, delimeters: String = ",") = value
        ?.split(delimeters)
        ?.filter { it.isNotBlank() }
}

// Wrapper that renders: string_agg(distinct <expr>::text, ',')
class StringAggDistinct(column: Column<*>) :
    CustomFunction<String>(
        functionName = "string_agg",
        columnType = TextColumnType(),
        column,
        stringLiteral(",")
    ) {
    private val colExpr: Expression<*> = column

    // Override the rendering so we can inject the `DISTINCT …::text` part.
    override fun toQueryBuilder(queryBuilder: QueryBuilder) {
        queryBuilder.append("string_agg(distinct ")
        colExpr.toQueryBuilder(queryBuilder)
        queryBuilder.append("::text, ',')")
    }
}
