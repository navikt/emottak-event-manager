package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class DatabaseTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
    }

    afterSpec {
        dbContainer.stop()
    }

    "Database should contain event_status enum" {
        val jdbcUrl = dbContainer.jdbcUrl
        val username = dbContainer.username
        val password = dbContainer.password

        val sql = """
            SELECT enumlabel 
            FROM pg_enum 
            JOIN pg_type ON pg_enum.enumtypid = pg_type.oid 
            WHERE pg_type.typname = 'event_status'
        """

        val expectedValues = listOf(
            "Opprettet",
            "Informasjon",
            "Manuell behandling",
            "Advarsel",
            "Feil",
            "Fatal feil",
            "Ferdigbehandlet"
        )

        val enumValues = mutableListOf<String>()
        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { resultSet ->
                    while (resultSet.next()) {
                        enumValues.add(resultSet.getString(1))
                    }
                }
            }
        }

        enumValues shouldContainAll expectedValues
    }
}) {
    companion object {
        fun PostgreSQLContainer<Nothing>.testConfiguration(): HikariConfig {
            return HikariConfig().apply {
                jdbcUrl = this@testConfiguration.jdbcUrl
                username = this@testConfiguration.username
                password = this@testConfiguration.password
                maximumPoolSize = 5
                minimumIdle = 1
                idleTimeout = 500001
                connectionTimeout = 10000
                maxLifetime = 600001
                initializationFailTimeout = 5000
            }
        }

        private fun buildDatabaseContainer(): PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:15").apply {
                withUsername("$EVENT_DB_NAME-admin")
                withReuse(true)
                withLabel("app-name", "emottak-event-manager")
                start()
            }
    }
}
