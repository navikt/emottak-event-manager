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

    suspend fun callDeleteServiceEventsProcedure(service: String, jobName: String) = withContext(Dispatchers.IO) {
        transaction(database.db) {
            exec("CALL delete_service_events('$service', '$jobName', 10000)")
        }
    }

    suspend fun callEventsCleanupProcedure(hours: Int, jobName: String) = withContext(Dispatchers.IO) {
        transaction(database.db) {
            exec("CALL events_cleanup($hours, '$jobName', 100000)")
        }
    }
}
