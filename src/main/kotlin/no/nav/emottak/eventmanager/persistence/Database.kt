package no.nav.emottak.eventmanager.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import kotlin.time.Duration.Companion.seconds

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.persistence.Database")

class Database(
    dbConfig: HikariConfig
) {
    val dataSource = when (dbConfig) {
        is HikariDataSource -> dbConfig
        else -> HikariDataSource(dbConfig)
    }
    val db = Database.connect(dataSource)
    suspend fun migrate(migrationConfig: HikariConfig) = withContext(Dispatchers.IO) {
        log.info("Flyway: configuring with URL ${migrationConfig.jdbcUrl}")
        val flyway = Flyway.configure()
            .dataSource(migrationConfig.jdbcUrl, migrationConfig.username, migrationConfig.password)
            .initSql("SET ROLE \"$EVENT_DB_NAME-admin\"")
            .lockRetryCount(10)
            .load()
        log.info("Flyway: configuration loaded, starting migrate()")

        val migrateJob = async { flyway.migrate() }

        val watchdog = launch {
            delay(30.seconds)
            log.warn("Flyway migrate() still running after 30s — dumping stuck threads:")
            Thread.getAllStackTraces().forEach { (thread, stack) ->
                if (stack.any { it.className.contains("flyway", ignoreCase = true) || it.className.contains("postgresql", ignoreCase = true) }) {
                    log.warn(
                        "Thread [${thread.name} state=${thread.state}]:\n  " +
                            stack.take(20).joinToString("\n  ")
                    )
                }
            }
        }

        try {
            migrateJob.await()
            log.info("Flyway: migrate() completed successfully")
        } finally {
            watchdog.cancel()
        }
    }
}
