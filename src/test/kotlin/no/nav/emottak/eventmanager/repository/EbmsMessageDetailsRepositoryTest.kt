package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

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
        val retrievedDetails = repository.findByRequestId(messageDetails.requestId)

        retrievedDetails shouldBe messageDetails.copy()
    }

    "Should update message details by requestId" {
        val messageDetails = buildTestEbmsMessageDetails()
        repository.insert(messageDetails)

        val updatedMessageDetails = messageDetails.copy(
            cpaId = "updated-cpa-id",
            conversationId = "updated-conversation-id",
            messageId = "updated-message-id",
            fromPartyId = "updated-from-party-id",
            toPartyId = "updated-to-party-id",
            service = "updated-service",
            action = "updated-action",
            refParam = "updated-ref-param",
            sentAt = Instant.parse("2025-05-26T14:54:45.386Z"),
            savedAt = Instant.parse("2025-05-26T15:54:50.386Z")
        )
        repository.update(updatedMessageDetails)

        val retrievedDetails = repository.findByRequestId(messageDetails.requestId)

        retrievedDetails?.requestId shouldBe messageDetails.requestId

        retrievedDetails?.cpaId shouldBe updatedMessageDetails.cpaId
        retrievedDetails?.conversationId shouldBe updatedMessageDetails.conversationId
        retrievedDetails?.messageId shouldBe updatedMessageDetails.messageId
        retrievedDetails?.requestId shouldBe updatedMessageDetails.requestId
        retrievedDetails?.fromPartyId shouldBe updatedMessageDetails.fromPartyId
        retrievedDetails?.toPartyId shouldBe updatedMessageDetails.toPartyId
        retrievedDetails?.service shouldBe updatedMessageDetails.service
        retrievedDetails?.action shouldBe updatedMessageDetails.action
        retrievedDetails?.savedAt shouldBe updatedMessageDetails.savedAt.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.sentAt shouldBe updatedMessageDetails.sentAt?.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.refParam shouldBe updatedMessageDetails.refParam
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

fun buildTestEbmsMessageDetails(): EbmsMessageDetails {
    return EbmsMessageDetails(
        requestId = Uuid.random(),
        cpaId = "test-cpa-id",
        conversationId = "test-conversation-id",
        messageId = "test-message-id",
        fromPartyId = "test-from-party-id",
        toPartyId = "test-to-party-id",
        service = "test-service",
        action = "test-action",
        savedAt = Instant.now().truncatedTo(ChronoUnit.MICROS)
    )
}
