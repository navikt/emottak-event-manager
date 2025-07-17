package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid

class EbmsMessageDetailRepositoryTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database
    lateinit var repository: EbmsMessageDetailRepository

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        repository = EbmsMessageDetailRepository(db)
    }

    afterSpec {
        dbContainer.stop()
    }

    afterTest {
        db.dataSource.connection.use { conn ->
            conn.createStatement().execute("DELETE FROM events")
            conn.createStatement().execute("DELETE FROM ebms_message_details")
        }
    }

    "Should insert and retrieve message details by requestId" {
        val messageDetails = buildTestEbmsMessageDetail()

        repository.insert(messageDetails)
        val retrievedDetails = repository.findByRequestId(messageDetails.requestId)

        retrievedDetails shouldBe messageDetails.copy()
    }

    "Should update message details by requestId" {
        val messageDetails = buildTestEbmsMessageDetail()
        repository.insert(messageDetails)

        val updatedMessageDetail = messageDetails.copy(
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
        repository.update(updatedMessageDetail)

        val retrievedDetails = repository.findByRequestId(messageDetails.requestId)

        retrievedDetails?.requestId shouldBe messageDetails.requestId

        retrievedDetails?.cpaId shouldBe updatedMessageDetail.cpaId
        retrievedDetails?.conversationId shouldBe updatedMessageDetail.conversationId
        retrievedDetails?.messageId shouldBe updatedMessageDetail.messageId
        retrievedDetails?.requestId shouldBe updatedMessageDetail.requestId
        retrievedDetails?.fromPartyId shouldBe updatedMessageDetail.fromPartyId
        retrievedDetails?.toPartyId shouldBe updatedMessageDetail.toPartyId
        retrievedDetails?.service shouldBe updatedMessageDetail.service
        retrievedDetails?.action shouldBe updatedMessageDetail.action
        retrievedDetails?.savedAt shouldBe updatedMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.sentAt shouldBe updatedMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.refParam shouldBe updatedMessageDetail.refParam
    }

    "Should retrieve records by time interval" {
        val messageDetailsInInterval = buildTestEbmsMessageDetail().copy(
            savedAt = Instant.parse("2025-04-30T12:54:45.386Z")
        )

        val messageDetailsOutOfInterval = buildTestEbmsMessageDetail().copy(
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

    "Should retrieve related request IDs by request IDs" {
        val messageDetails1 = buildTestEbmsMessageDetail().copy(
            requestId = Uuid.random(),
            conversationId = "conversationId-1"
        )
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            requestId = Uuid.random(),
            conversationId = "conversationId-1"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            requestId = Uuid.random(),
            conversationId = "conversationId-2"
        )

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)
        repository.insert(messageDetails3)

        val requestIds = listOf(messageDetails1.requestId, messageDetails3.requestId)
        val relatedRequestIds = repository.findRelatedRequestIds(requestIds)

        relatedRequestIds.size shouldBe 2
        relatedRequestIds shouldContainKey messageDetails1.requestId
        relatedRequestIds[messageDetails1.requestId] shouldBe "${messageDetails1.requestId},${messageDetails2.requestId}"
        relatedRequestIds shouldContainKey messageDetails3.requestId
        relatedRequestIds[messageDetails3.requestId] shouldBe messageDetails3.requestId.toString()
    }

    "Should retrieve records by message ID, conversation ID, and cpa ID" {
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            messageId = "different-message-id"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "different-conversation-id"
        )
        val messageDetails4 = buildTestEbmsMessageDetail().copy(
            cpaId = "different-cpa-id"
        )

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)
        repository.insert(messageDetails3)
        repository.insert(messageDetails4)

        val retrievedDetails = repository.findBySecondaryIdsSet(
            messageId = messageDetails1.messageId,
            conversationId = messageDetails1.conversationId,
            cpaId = messageDetails1.cpaId
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails shouldContain messageDetails1
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

fun buildTestEbmsMessageDetail(): EbmsMessageDetail {
    return EbmsMessageDetail(
        requestId = Uuid.random(),
        cpaId = "test-cpa-id",
        conversationId = "test-conversation-id",
        messageId = "test-message-id",
        fromPartyId = "test-from-party-id",
        fromRole = "test-from-role",
        toPartyId = "test-to-party-id",
        toRole = "test-to-role",
        service = "test-service",
        action = "test-action",
        refParam = "test-ref-param",
        sender = "test-sender",
        savedAt = Instant.parse("2025-05-08T12:54:45.386Z")
    )
}
