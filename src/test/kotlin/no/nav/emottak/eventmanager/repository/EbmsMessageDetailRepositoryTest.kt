package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail as TransportEbmsMessageDetail

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

        retrievedDetails?.requestId shouldBe messageDetails.requestId
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

    "Should retrieve message details by Readable ID" {
        val messageDetails = buildTestEbmsMessageDetail()

        repository.insert(messageDetails)
        val retrievedDetails = repository.findByReadableId(messageDetails.generateReadableId())

        retrievedDetails?.requestId shouldBe messageDetails.requestId
    }

    "Should retrieve message details by Readable ID pattern" {
        val messageDetails = buildTestEbmsMessageDetail()

        repository.insert(messageDetails)

        forAll(
            row(messageDetails.generateReadableId().substring(0, 6)),
            row(messageDetails.generateReadableId().substring(0, 6).lowercase()),
            row(messageDetails.generateReadableId().substring(0, 6).uppercase()),
            row(messageDetails.generateReadableId().takeLast(6)),
            row(messageDetails.generateReadableId().substring(6, 12))
        ) { readableIdPattern ->
            val retrievedDetails = repository.findByReadableIdPattern(readableIdPattern)

            retrievedDetails?.requestId shouldBe messageDetails.requestId
        }
    }

    "Should retrieve records by time interval" {
        val (md1, md2, md3, md4) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z")
        )
        retrievedDetails.size shouldBe 4
        retrievedDetails[0].requestId shouldBe md1.requestId
        retrievedDetails[1].requestId shouldBe md2.requestId
        retrievedDetails[2].requestId shouldBe md3.requestId
        retrievedDetails[3].requestId shouldBe md4.requestId
    }

    "Should retrieve records by time interval and filtered by readableId" {
        val (messageDetailsInInterval1, _, _, _) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableId = messageDetailsInInterval1.generateReadableId()
        )
        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetailsInInterval1.requestId
    }

    "Should retrieve records by time interval and filtered by cpaId" {
        val (_, messageDetailsInInterval2, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            cpaId = "another-cpa-id"
        )
        retrievedDetails.size shouldBe 2
        retrievedDetails[0].requestId shouldBe messageDetailsInInterval2.requestId
        retrievedDetails[1].requestId shouldBe messageDetailsOutOfInterval2.requestId
    }

    "Should retrieve records by time interval and filtered by readableId and cpaId" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableId = messageDetailsOutOfInterval2.generateReadableId(),
            cpaId = "another-cpa-id"
        )
        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetailsOutOfInterval2.requestId
    }

    "Should retrieve empty list if no message details within given time interval" {
        buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T13:00:00Z"),
            Instant.parse("2025-04-30T14:00:00Z")
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given readableId in the given time interval" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T12:57:00Z"),
            readableId = messageDetailsOutOfInterval2.generateReadableId()
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given cpaId in the given time interval" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:55:00Z"),
            Instant.parse("2025-04-30T12:57:00Z"),
            cpaId = messageDetailsOutOfInterval2.cpaId
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given readableId and cpaId in the given time interval" {
        val (_, _, messageDetailsOutOfInterval1, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableId = messageDetailsOutOfInterval2.generateReadableId(),
            cpaId = messageDetailsOutOfInterval1.cpaId
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve records by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(fromRole = roleFilter)

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)

        val retrievedDetails = repository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            role = roleFilter
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve records by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(service = serviceFilter)

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)

        val retrievedDetails = repository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            service = serviceFilter
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve records by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(action = actionFilter)

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)

        val retrievedDetails = repository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            action = actionFilter
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve related request IDs by request IDs" {
        val messageDetails1 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-2"
        )

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)
        repository.insert(messageDetails3)

        val requestIds = listOf(messageDetails1.requestId, messageDetails2.requestId, messageDetails3.requestId)
        val relatedRequestIds = repository.findRelatedRequestIds(requestIds)

        relatedRequestIds.size shouldBe 3
        relatedRequestIds shouldContainKey messageDetails1.requestId
        relatedRequestIds[messageDetails1.requestId] shouldBe "${messageDetails1.requestId},${messageDetails2.requestId}"
        relatedRequestIds shouldContainKey messageDetails2.requestId
        relatedRequestIds[messageDetails2.requestId] shouldBe "${messageDetails1.requestId},${messageDetails2.requestId}"
        relatedRequestIds shouldContainKey messageDetails3.requestId
        relatedRequestIds[messageDetails3.requestId] shouldBe messageDetails3.requestId.toString()
    }

    "Should retrieve related readable IDs by request IDs" {
        val messageDetails1 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-2"
        )

        repository.insert(messageDetails1)
        repository.insert(messageDetails2)
        repository.insert(messageDetails3)

        val requestIds = listOf(messageDetails1.requestId, messageDetails2.requestId, messageDetails3.requestId)
        val relatedReadableIds = repository.findRelatedReadableIds(requestIds)

        relatedReadableIds.size shouldBe 3
        relatedReadableIds[messageDetails1.requestId] shouldBe "${messageDetails1.generateReadableId()},${messageDetails2.generateReadableId()}"
        relatedReadableIds[messageDetails2.requestId] shouldBe "${messageDetails1.generateReadableId()},${messageDetails2.generateReadableId()}"
        relatedReadableIds[messageDetails3.requestId] shouldBe messageDetails3.generateReadableId()
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

        val retrievedDetails = repository.findByMessageIdConversationIdAndCpaId(
            messageId = messageDetails1.messageId,
            conversationId = messageDetails1.conversationId,
            cpaId = messageDetails1.cpaId
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails1.requestId
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
    return EbmsMessageDetail.fromTransportModel(buildTestTransportMessageDetail())
}

fun buildTestTransportMessageDetail(): TransportEbmsMessageDetail {
    return TransportEbmsMessageDetail(
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
        savedAt = Instant.parse("2025-05-08T12:54:45.386Z")
    )
}

suspend fun buildAndInsertTestEbmsMessageDetailFindData(repository: EbmsMessageDetailRepository): List<EbmsMessageDetail> {
    val messageDetailsInInterval1 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-A",
        savedAt = Instant.parse("2025-04-30T12:52:45.386Z")
    )
    val messageDetailsInInterval2 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-B",
        cpaId = "another-cpa-id",
        savedAt = Instant.parse("2025-04-30T12:54:46.386Z")
    )
    val messageDetailsOutOfInterval1 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-C",
        savedAt = Instant.parse("2025-04-30T12:56:47.386Z"),
        refToMessageId = "message-id-reference-C"
    )
    val messageDetailsOutOfInterval2 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-D",
        cpaId = "another-cpa-id",
        savedAt = Instant.parse("2025-04-30T12:58:48.386Z"),
        refToMessageId = "message-id-reference-D"
    )

    repository.insert(messageDetailsInInterval1)
    repository.insert(messageDetailsInInterval2)
    repository.insert(messageDetailsOutOfInterval1)
    repository.insert(messageDetailsOutOfInterval2)

    return listOf(messageDetailsInInterval1, messageDetailsInInterval2, messageDetailsOutOfInterval1, messageDetailsOutOfInterval2)
}
