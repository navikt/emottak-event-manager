package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import kotlin.uuid.Uuid

class EventRepositoryTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database
    lateinit var eventRepository: EventRepository

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        eventRepository = EventRepository(db)
    }

    afterSpec {
        dbContainer.stop()
    }

    "Should retrieve an event by eventId" {
        val testEvent = buildTestEvent()

        val eventId = eventRepository.insert(testEvent)
        val retrievedEvent = eventRepository.findEventById(eventId)

        retrievedEvent shouldBe testEvent.copy()
    }

    "Should retrieve an event with empty eventData by eventId" {
        val testEvent = Event(
            eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
            requestId = Uuid.random(),
            contentId = "test-content-id",
            messageId = "test-message-id"
        )

        val eventId = eventRepository.insert(testEvent)
        val retrievedEvent = eventRepository.findEventById(eventId)

        retrievedEvent shouldBe testEvent.copy()
    }

    "Should insert multiple Events with same requestId and retrieve them" {
        val sharedRequestId = Uuid.random()

        val event1 = buildTestEvent().copy(
            requestId = sharedRequestId
        )

        val event2 = buildTestEvent().copy(
            requestId = sharedRequestId
        )

        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findEventByRequestId(sharedRequestId)

        retrievedEvents shouldContainExactlyInAnyOrder listOf(event1.copy(), event2.copy())
    }

    "Should find events by time interval" {

        val eventInTimeInterval = buildTestEvent().copy(
            createdAt = Instant.parse("2025-04-01T14:54:45.386Z")
        )

        val eventOutOfTimeInterval = buildTestEvent().copy(
            createdAt = Instant.parse("2025-04-01T15:54:45.386Z")
        )

        eventRepository.insert(eventInTimeInterval)
        eventRepository.insert(eventOutOfTimeInterval)

        val retrievedEvents = eventRepository.findEventByTimeInterval(
            Instant.parse("2025-04-01T14:00:00Z"),
            Instant.parse("2025-04-01T15:00:00Z")
        )

        retrievedEvents.size shouldBe 1
        retrievedEvents shouldContain eventInTimeInterval
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

fun buildTestEvent(): Event = Event(
    eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
    requestId = Uuid.random(),
    contentId = "test-content-id",
    messageId = "test-message-id",
    eventData = "{\"juridisk_logg_id\":\"1_msg_20250401145445386\"}",
    createdAt = Instant.parse("2025-04-01T12:54:45.386Z")
)
