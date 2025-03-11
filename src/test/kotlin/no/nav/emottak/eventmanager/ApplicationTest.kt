package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import kotlin.test.assertEquals

class ApplicationTest {

    companion object {
        lateinit var dbContainer: PostgreSQLContainer<Nothing>

        @JvmStatic
        @BeforeAll
        fun setup() {
            dbContainer = buildDatabaseContainer()
            dbContainer.start()
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
                withUsername("$EVENT_DB_NAME-admin")
                withReuse(true)
                withLabel("app-name", "emottak-event-manager")
                start()
            }
    }

    @Test
    fun testRoot() = testApplication {
        application(
            eventManagerModule(
                dbContainer.testConfiguration(),
                dbContainer.testConfiguration()
            )
        )
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
