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
            .ignoreMigrationPatterns("*:missing")
            .load()
        log.info("Flyway: configuration loaded, starting repair() then migrate()")
        try {
            flyway.repair()
            log.info("Flyway: repair() completed")
            flyway.migrate()
            log.info("Flyway: migrate() completed successfully")
        } catch (e: Exception) {
            log.error("Flyway: failed: ${e.message}")
            throw e
        }
    }
}
