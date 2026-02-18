package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.ConversationStatus
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.ConversationStatusRepository
import no.nav.emottak.eventmanager.persistence.repository.DistinctRolesServicesActionsRepository
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail as KafkaEbmsMessageDetail
import no.nav.emottak.utils.kafka.model.Event as KafkaEvent
import no.nav.emottak.utils.kafka.model.EventDataType as KafkaEventDataType
import no.nav.emottak.utils.kafka.model.EventType as KafkaEventType

abstract class RepositoryTestBase(
    init: RepositoryTestBase.() -> Unit = {}
) : StringSpec({}) {

    private lateinit var dbContainer: PostgreSQLContainer<Nothing>
    private lateinit var db: Database
    internal lateinit var eventRepository: EventRepository
    internal lateinit var eventTypeRepository: EventTypeRepository
    internal lateinit var ebmsMessageDetailRepository: EbmsMessageDetailRepository
    internal lateinit var distinctRolesServicesActionsRepository: DistinctRolesServicesActionsRepository
    internal lateinit var conversationStatusRepository: ConversationStatusRepository

    override suspend fun beforeSpec(spec: Spec) {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()

        val migrationDb = Database(dbContainer.testConfiguration())
        migrationDb.migrate(migrationDb.dataSource)
        migrationDb.dataSource.close()
        db = Database(dbContainer.testConfiguration(user = "user"))

        eventRepository = EventRepository(db)
        eventTypeRepository = EventTypeRepository(db)
        ebmsMessageDetailRepository = EbmsMessageDetailRepository(db)
        distinctRolesServicesActionsRepository = DistinctRolesServicesActionsRepository(db)
        conversationStatusRepository = ConversationStatusRepository(db)
    }

    override suspend fun afterSpec(spec: Spec) {
        db.dataSource.close()
        dbContainer.stop()
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        db.dataSource.connection.use { conn ->
            conn.createStatement().execute("DELETE FROM events")
            conn.createStatement().execute("DELETE FROM ebms_message_details")
            conn.createStatement().execute("DELETE FROM distict_roles_services_actions")
            conn.createStatement().execute("DELETE FROM conversation_status")
        }
    }

    init {
        this.init()
    }
}

fun PostgreSQLContainer<Nothing>.testConfiguration(user: String = "admin"): HikariConfig {
    val (username, password) = when (user) {
        "admin" -> this@testConfiguration.username to this@testConfiguration.password
        "user" -> "$EVENT_DB_NAME-user" to "app_pass"
        else -> error("Unsupported user: $user")
    }
    return HikariConfig().apply {
        jdbcUrl = this@testConfiguration.jdbcUrl
        this.username = username
        this.password = password
        maximumPoolSize = 5
        minimumIdle = 1
        idleTimeout = 500001
        connectionTimeout = 10000
        maxLifetime = 600001
        initializationFailTimeout = 5000
    }
}

fun buildDatabaseContainer(): PostgreSQLContainer<Nothing> {
    return PostgreSQLContainer<Nothing>("postgres:15").apply {
        withInitScript("init_roles.sql")
        withUsername("$EVENT_DB_NAME-admin")
        withReuse(true)
        withLabel("app-name", "emottak-event-manager")
        start()
    }
}

fun buildTestEvent(requestId: Uuid = Uuid.random()): Event {
    return Event.fromTransportModel(buildTestTransportEvent(requestId))
}

fun buildTestTransportEvent(requestId: Uuid = Uuid.random()): KafkaEvent = KafkaEvent(
    eventType = KafkaEventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
    requestId = requestId,
    contentId = "test-content-id",
    messageId = "test-message-id",
    eventData = "{\"juridisk_logg_id\":\"1_msg_20250401145445386\"}",
    createdAt = Instant.parse("2025-04-01T12:54:45.386Z"),
    conversationId = "test-conversation-id"
)

fun buildTestEbmsMessageDetail(requestId: Uuid = Uuid.random()): EbmsMessageDetail {
    return EbmsMessageDetail.fromTransportModel(buildTestTransportMessageDetail(requestId))
}

fun buildTestTransportMessageDetail(requestId: Uuid = Uuid.random()): KafkaEbmsMessageDetail = KafkaEbmsMessageDetail(
    requestId = requestId,
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

suspend fun buildAndInsertTestEbmsMessageDetailConversation(
    ebmsMessageDetailRepository: EbmsMessageDetailRepository,
    eventRepository: EventRepository,
    conversationStatusRepository: ConversationStatusRepository
): Pair<List<EbmsMessageDetail>, List<List<Event>>> {
    val c1md1 = buildTestEbmsMessageDetail().copy(
        savedAt = Instant.parse("2025-04-30T12:52:45.000Z"),
        readableId = "readable-id-1"
    )
    val c1md2 = buildTestEbmsMessageDetail().copy(
        messageId = "another-message-id-1",
        savedAt = Instant.parse("2025-04-30T12:54:46.000Z"),
        readableId = "another-readable-id-1"
    )
    val c2md1 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-2",
        cpaId = "another-cpa-id",
        messageId = "another-message-id-2",
        savedAt = Instant.parse("2025-04-30T12:56:47.000Z"),
        refToMessageId = "message-id-reference-D",
        readableId = "another-readable-id-2"
    )
    val c1md3 = buildTestEbmsMessageDetail().copy(
        savedAt = Instant.parse("2025-04-30T12:58:48.000Z"),
        refToMessageId = "message-id-reference",
        readableId = "readable-id-2"
    )
    val c3md1 = buildTestEbmsMessageDetail().copy(
        conversationId = "conversation-id-3",
        savedAt = Instant.parse("2025-04-30T12:59:49.000Z"),
        service = "another-service"
    )

    ebmsMessageDetailRepository.insert(c1md1)
    ebmsMessageDetailRepository.insert(c1md2)
    ebmsMessageDetailRepository.insert(c2md1)
    ebmsMessageDetailRepository.insert(c1md3)
    ebmsMessageDetailRepository.insert(c3md1)

    conversationStatusRepository.insert(c1md1.conversationId)
    conversationStatusRepository.insert(c1md2.conversationId)
    conversationStatusRepository.insert(c2md1.conversationId)
    conversationStatusRepository.insert(c1md3.conversationId)
    conversationStatusRepository.insert(c3md1.conversationId)

    val events1 = buildAndInsertTestEvents(eventRepository, c1md1)
    val events2 = buildAndInsertTestEvents(eventRepository, c1md2)
    val events3 = buildAndInsertTestEvents(eventRepository, c2md1)
    val events4 = buildAndInsertTestEvents(eventRepository, c1md3, KafkaEventType.UNKNOWN_ERROR_OCCURRED)
    val events5 = buildAndInsertTestEvents(eventRepository, c3md1)

    return Pair(listOf(c1md1, c1md2, c2md1, c1md3, c3md1), listOf(events1, events2, events3, events4, events5))
}

suspend fun buildAndInsertTestEvents(
    repository: EventRepository,
    messageDetail: EbmsMessageDetail,
    lastEventType: KafkaEventType = KafkaEventType.MESSAGE_SENT_TO_FAGSYSTEM
): List<Event> {
    val event1 = buildTestEvent(messageDetail.requestId).copy(
        eventType = KafkaEventType.MESSAGE_RECEIVED_VIA_HTTP,
        createdAt = messageDetail.savedAt.plusMillis(250)
    )
    val event2 = buildTestEvent(messageDetail.requestId).copy(
        eventType = KafkaEventType.MESSAGE_VALIDATED_AGAINST_CPA,
        eventData = Json.encodeToString(mapOf(KafkaEventDataType.SENDER_NAME.value to "Test EPJ AS")),
        createdAt = messageDetail.savedAt.plusMillis(500)
    )
    val event3 = buildTestEvent(messageDetail.requestId).copy(
        eventType = KafkaEventType.REFERENCE_RETRIEVED,
        createdAt = messageDetail.savedAt.plusMillis(750)
    )
    val event4 = buildTestEvent(messageDetail.requestId).copy(
        eventType = lastEventType,
        createdAt = messageDetail.savedAt.plusMillis(1000)
    )

    repository.insert(event1)
    repository.insert(event2)
    repository.insert(event3)
    repository.insert(event4)

    return listOf(event1, event2, event3, event4)
}

fun buildTestConversationStatus(conversationId: String = Uuid.random().toString(), status: EventStatusEnum = EventStatusEnum.INFORMATION) = ConversationStatus(
    conversationId = conversationId,
    createdAt = Instant.now(),
    latestStatus = status,
    statusAt = Instant.now()
)
