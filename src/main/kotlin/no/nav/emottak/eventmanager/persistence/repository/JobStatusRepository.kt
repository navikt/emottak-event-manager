package no.nav.emottak.eventmanager.persistence.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    suspend fun callDeleteServiceEventsProcedure(service: String, jobName: String) = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.prepareCall("CALL delete_service_events(?, ?, ?)").use { stmt ->
                stmt.setString(1, service)
                stmt.setString(2, jobName)
                stmt.setInt(3, 10000)
                stmt.execute()
            }
        }
    }

    suspend fun callEventsCleanupProcedure(hours: Int, jobName: String) = withContext(Dispatchers.IO) {
        database.dataSource.connection.use { connection ->
            connection.autoCommit = true
            connection.prepareCall("CALL events_cleanup(?, ?, ?)").use { stmt ->
                stmt.setInt(1, hours)
                stmt.setString(2, jobName)
                stmt.setInt(3, 100000)
                stmt.execute()
            }
        }
    }
}
