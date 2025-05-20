package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.buildTestEbmsMessageDetails
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import kotlin.uuid.toJavaUuid

class EbmsMessageDetailsRepositoryTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database
    lateinit var repository: EbmsMessageDetailsRepository

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        repository = EbmsMessageDetailsRepository(db)
    }

    afterSpec {
        dbContainer.stop()
    }

    "Should insert and retrieve message details by requestId" {
        val messageDetails = buildTestEbmsMessageDetails()

        repository.insert(messageDetails)
        val retrievedDetails = repository.findByRequestId(messageDetails.requestId.toJavaUuid())

        retrievedDetails shouldBe messageDetails.copy()
    }

    "Should retrieve records by time interval" {
        val messageDetailsInInterval = buildTestEbmsMessageDetails().copy(
            savedAt = Instant.parse("2025-04-30T12:54:45.386Z")
        )

        val messageDetailsOutOfInterval = buildTestEbmsMessageDetails().copy(
            savedAt = Instant.parse("2025-04-30T15:54:45.386Z")
        )

        repository.insert(messageDetailsInInterval)
        repository.insert(messageDetailsOutOfInterval)

        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z")
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails shouldContain messageDetailsInInterval
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
