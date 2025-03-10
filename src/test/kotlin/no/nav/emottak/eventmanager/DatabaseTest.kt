package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EBMS_DB_NAME
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DatabaseTest {
    companion object {
        lateinit var dbContainer: PostgreSQLContainer<Nothing>
        lateinit var db: Database

        @JvmStatic
        @BeforeAll
        fun setup() {
            dbContainer = buildDatabaseContainer()
            dbContainer.start()
            db = Database(dbContainer.testConfiguration())
            db.migrate(db.dataSource)
        }

        @JvmStatic
        @AfterAll
        fun tearDown() {
            dbContainer.stop()
        }

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
                withUsername("$EBMS_DB_NAME-admin")
                withReuse(true)
                withLabel("app-navn", "ebms-provider")
                start()
                println(
                    "EBMS-databasen er startet opp, portnummer: $firstMappedPort, jdbcUrl: jdbc:postgresql://localhost:$firstMappedPort/test, credentials: test og test"
                )
            }
    }

    @Test
    fun `Database contains event_status enum`() {
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

        DriverManager.getConnection(jdbcUrl, username, password).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.executeQuery().use { resultSet ->
                    val enumValues = mutableListOf<String>()
                    while (resultSet.next()) {
                        enumValues.add(resultSet.getString(1))
                    }
                    Assertions.assertTrue(enumValues.containsAll(expectedValues), "Enum values should match expected ones")
                }
            }
        }
    }
}
