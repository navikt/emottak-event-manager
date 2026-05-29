package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.withContext
import no.nav.emottak.eventmanager.model.ProcedureArgs
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.table.JobStatusTable
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction

class JobStatusRepository(private val database: Database) {

    suspend fun jobExists(jobName: String): Boolean = withContext(Dispatchers.IO) {
        transaction(database.db) {
            JobStatusTable.selectAll().where { JobStatusTable.jobName eq jobName }.any()
        }
    }

    // These procedures use explicit COMMIT statements for batch processing, so they must be
    // called outside any client-managed transaction (autoCommit=true). Wrapping in transaction {}
    // would cause PostgreSQL to throw "invalid transaction termination" on the internal COMMITs.
    // networkTimeout is set to 0 (unlimited) because these procedures can run for a long time on
    // large datasets — the default socketTimeout=30s in the JDBC URL would otherwise kill them.

    suspend fun callDeleteServiceEventsProcedure(jobName: String, args: ProcedureArgs.DeleteServiceArgs) = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.setNetworkTimeout(Dispatchers.IO.asExecutor(), 0)
            connection.prepareCall("CALL delete_service_events(?, ?, ?)").use { stmt ->
                stmt.setString(1, args.service)
                stmt.setString(2, jobName)
                stmt.setInt(3, args.batchSize)
                stmt.execute()
            }
        }
    }

    suspend fun callEventsCleanupProcedure(jobName: String, args: ProcedureArgs.CleanupArgs) = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.setNetworkTimeout(Dispatchers.IO.asExecutor(), 0)
            connection.prepareCall("CALL events_cleanup(?, ?, ?)").use { stmt ->
                stmt.setInt(1, args.hours)
                stmt.setString(2, jobName)
                stmt.setInt(3, args.batchSize)
                stmt.execute()
            }
        }
    }
}
