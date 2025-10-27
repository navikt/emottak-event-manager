package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.DistinctRolesServicesActionsRepository
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.utils.kafka.model.EventType
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import kotlin.uuid.Uuid

abstract class RepositoryTestBase(
    init: RepositoryTestBase.() -> Unit = {}
) : StringSpec({}) {

    private lateinit var dbContainer: PostgreSQLContainer<Nothing>
    private lateinit var db: Database
    internal lateinit var eventRepository: EventRepository
    internal lateinit var eventTypeRepository: EventTypeRepository
    internal lateinit var ebmsMessageDetailRepository: EbmsMessageDetailRepository
    internal lateinit var distinctRolesServicesActionsRepository: DistinctRolesServicesActionsRepository

    override suspend fun beforeSpec(spec: Spec) {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        eventRepository = EventRepository(db)
        eventTypeRepository = EventTypeRepository(db)
        ebmsMessageDetailRepository = EbmsMessageDetailRepository(db)
        distinctRolesServicesActionsRepository = DistinctRolesServicesActionsRepository(db)
    }

    override suspend fun afterSpec(spec: Spec) {
        dbContainer.stop()
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        db.dataSource.connection.use { conn ->
            conn.createStatement().execute("DELETE FROM events")
            conn.createStatement().execute("DELETE FROM ebms_message_details")
        }
    }

    init {
        this.init()
    }

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

        private fun buildDatabaseContainer(): PostgreSQLContainer<Nothing> {
            return PostgreSQLContainer<Nothing>("postgres:15").apply {
                withUsername("$EVENT_DB_NAME-admin")
                withReuse(true)
                withLabel("app-name", "emottak-event-manager")
                start()
            }
        }
    }
}

fun buildTestEvent(): Event {
    return Event.fromTransportModel(buildTestTransportEvent())
}

fun buildTestTransportEvent(): no.nav.emottak.utils.kafka.model.Event = no.nav.emottak.utils.kafka.model.Event(
    eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
    requestId = Uuid.random(),
    contentId = "test-content-id",
    messageId = "test-message-id",
    eventData = "{\"juridisk_logg_id\":\"1_msg_20250401145445386\"}",
    createdAt = Instant.parse("2025-04-01T12:54:45.386Z")
)

fun buildTestEbmsMessageDetail(): EbmsMessageDetail {
    return EbmsMessageDetail.fromTransportModel(buildTestTransportMessageDetail())
}

fun buildTestTransportMessageDetail(): no.nav.emottak.utils.kafka.model.EbmsMessageDetail {
    return no.nav.emottak.utils.kafka.model.EbmsMessageDetail(
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
        messageId = "another-message-id1",
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
        messageId = "another-message-id2",
        savedAt = Instant.parse("2025-04-30T12:58:48.386Z"),
        refToMessageId = "message-id-reference-D"
    )

    repository.insert(messageDetailsInInterval1)
    repository.insert(messageDetailsInInterval2)
    repository.insert(messageDetailsOutOfInterval1)
    repository.insert(messageDetailsOutOfInterval2)

    return listOf(messageDetailsInInterval1, messageDetailsInInterval2, messageDetailsOutOfInterval1, messageDetailsOutOfInterval2)
}

suspend fun buildAndInsertTestEbmsMessageDetailFilterData(repository: EbmsMessageDetailRepository) {
    val messageDetails1 = buildTestEbmsMessageDetail()
    val messageDetails2 = buildTestEbmsMessageDetail().copy(
        fromRole = "different-role"
    )
    val messageDetails3 = buildTestEbmsMessageDetail().copy(
        service = "different-service"
    )
    val messageDetails4 = buildTestEbmsMessageDetail().copy(
        action = "different-action"
    )

    repository.insert(messageDetails1)
    repository.insert(messageDetails2)
    repository.insert(messageDetails3)
    repository.insert(messageDetails4)
}
