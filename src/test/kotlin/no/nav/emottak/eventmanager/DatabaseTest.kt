package no.nav.emottak.eventmanager

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.repository.buildDatabaseContainer
import no.nav.emottak.eventmanager.repository.testConfiguration
import org.testcontainers.containers.PostgreSQLContainer
import java.sql.DriverManager

class DatabaseTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        val migrationDb = Database(dbContainer.testConfiguration())
        migrationDb.migrate(migrationDb.dataSource)
        migrationDb.dataSource.close()
        db = Database(dbContainer.testConfiguration(user = "user"))
    }

    afterSpec {
        db.dataSource.close()
        dbContainer.stop()
    }

    "Should run as emottak-event-manager-db-user" {
        db.dataSource.connection.use { conn ->
            var rs = conn.createStatement().executeQuery("SELECT current_user")
            rs.next() shouldBe true
            rs.getString("current_user") shouldBe "emottak-event-manager-db-user"
        }
    }

    "Table created by migration should be owned by emottak-event-manager-db-admin" {
        val sql = """
            SELECT pg_get_userbyid(relowner) AS owner_name 
            FROM pg_class 
            WHERE relkind = 'r' 
            AND relname = 'events'
        """
        db.dataSource.connection.use { conn ->
            var rs = conn.createStatement().executeQuery(sql)
            rs.next() shouldBe true
            rs.getString("owner_name") shouldBe "emottak-event-manager-db-admin"
        }
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
})
