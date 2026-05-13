package no.nav.emottak.eventmanager.persistence

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory

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
            .ignoreMigrationPatterns("repeatable:missing")
            .load()
        log.info("Flyway: configuration loaded, starting migrate() with WATCHDOG")

        val watchdog = Thread {
            try {
                Thread.sleep(20_000)
                log.warn("=== WATCHDOG: Flyway migrate() still running after 20s — dumping ALL threads ===")
                Thread.getAllStackTraces().forEach { (thread, stack) ->
                    log.warn(
                        "Thread [${thread.name}] state=${thread.state}:\n  " +
                            stack.take(20).joinToString("\n  ")
                    )
                }
                log.warn("=== WATCHDOG END ===")
            } catch (_: InterruptedException) {
                // migrate() finished before watchdog fired — normal path
            }
        }.apply {
            name = "flyway-migrate-watchdog"
            isDaemon = true
            start()
        }

        try {
            flyway.migrate()
            log.info("Flyway: migrate() completed successfully")
        } catch (e: Exception) {
            log.info("Flyway: migrate() failed: ${e.message}")
            e.printStackTrace()
            throw e
        } finally {
            watchdog.interrupt()
        }
    }
}
