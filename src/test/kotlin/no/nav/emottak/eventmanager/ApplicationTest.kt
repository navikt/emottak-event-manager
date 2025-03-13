package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import org.testcontainers.containers.PostgreSQLContainer

class ApplicationTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
    }

    afterSpec {
        dbContainer.stop()
    }

    "Root endpoint should return OK" {
        testApplication {
            application(
                eventManagerModule(
                    dbContainer.testConfiguration(),
                    dbContainer.testConfiguration()
                )
            )
            client.get("/").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
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
