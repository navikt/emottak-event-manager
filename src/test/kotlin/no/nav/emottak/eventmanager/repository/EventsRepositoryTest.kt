package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventType
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

@OptIn(ExperimentalUuidApi::class)
class EventsRepositoryTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database
    lateinit var eventRepository: EventsRepository

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        eventRepository = EventsRepository(db)
    }

    afterSpec {
        dbContainer.stop()
    }

    "Should retrieve an event by eventId" {
        val testEvent = Event(
            eventType = EventType.MESSAGE_SENT,
            requestId = UUID.randomUUID().toKotlinUuid(),
            contentId = "test-content-id",
            messageId = "test-message-id",
            eventData = "{\"key\":\"value\"}"
        )

        val eventId = eventRepository.insert(testEvent)
        val retrievedEvent = eventRepository.findEventById(eventId)

        retrievedEvent shouldBe testEvent.copy()
    }

    "Should insert multiple Events with same requestId and retrieve them" {
        val sharedRequestId = Uuid.random()

        val event1 = Event(
            eventType = EventType.MESSAGE_SENT,
            requestId = sharedRequestId,
            contentId = "content-1",
            messageId = "message-1",
            eventData = "{\"key1\":\"value1\"}"
        )

        val event2 = Event(
            eventType = EventType.MESSAGE_SENT,
            requestId = sharedRequestId,
            contentId = "content-2",
            messageId = "message-2",
            eventData = "{\"key2\":\"value2\"}"
        )

        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findEventByRequestId(sharedRequestId)

        retrievedEvents shouldContainExactlyInAnyOrder listOf(event1.copy(), event2.copy())
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
