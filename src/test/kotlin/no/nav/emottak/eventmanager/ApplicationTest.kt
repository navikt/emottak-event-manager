package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.eventmanager.service.EbmsMessageDetailsService
import no.nav.emottak.eventmanager.service.EventService
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import no.nav.emottak.utils.kafka.model.Event
import no.nav.emottak.utils.kafka.model.EventType
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.ZoneId
import kotlin.uuid.Uuid

class ApplicationTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database

    lateinit var eventRepository: EventsRepository
    lateinit var ebmsMessageDetailsRepository: EbmsMessageDetailsRepository

    lateinit var eventService: EventService
    lateinit var ebmsMessageDetailsService: EbmsMessageDetailsService

    val withTestApplication = fun (testBlock: suspend (HttpClient) -> Unit) {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            testBlock(httpClient)
        }
    }

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)

        eventRepository = EventsRepository(db)
        ebmsMessageDetailsRepository = EbmsMessageDetailsRepository(db)

        eventService = EventService(eventRepository, ebmsMessageDetailsRepository)
        ebmsMessageDetailsService = EbmsMessageDetailsService(ebmsMessageDetailsRepository)
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

    "Root endpoint should return OK" {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )
            client.get("/").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    "fetchevents endpoint should return list of events" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetails().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailsRepository.insert(testMessageDetails)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-01T14:00&toDate=2025-04-01T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK

            val events: List<EventInfo> = httpResponse.body()
            events[0].hendelsedato shouldBe testEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            events[0].hendelsedeskr shouldBe testEvent.eventType.toString()
            events[0].tillegsinfo shouldBe testEvent.eventData
            events[0].mottakid shouldBe testEvent.requestId.toString()
            events[0].role shouldBe testMessageDetails.fromRole
            events[0].service shouldBe testMessageDetails.service
            events[0].action shouldBe testMessageDetails.action
            events[0].referanse shouldBe testMessageDetails.refParam
            events[0].avsender shouldBe testMessageDetails.sender
        }
    }

    "fetchevents endpoint should return list of events when message details are not found" {
        withTestApplication { httpClient ->
            val testEvent = buildTestEvent()

            eventRepository.insert(testEvent)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-01T14:00&toDate=2025-04-01T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK

            val events: List<EventInfo> = httpResponse.body()
            events[0].hendelsedato shouldBe testEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            events[0].hendelsedeskr shouldBe testEvent.eventType.toString()
            events[0].tillegsinfo shouldBe testEvent.eventData
            events[0].mottakid shouldBe testEvent.requestId.toString()
            events[0].role shouldBe null
            events[0].service shouldBe null
            events[0].action shouldBe null
            events[0].referanse shouldBe null
            events[0].avsender shouldBe null
        }
    }

    "fetchevents endpoint should return empty list if no events found" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetails().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailsRepository.insert(testMessageDetails)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-02T14:00&toDate=2025-04-02T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<EventInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "fetchevents endpoint should return BadRequest if required parameters are missing" {
        withTestApplication { httpClient ->
            forAll(
                row("/fetchevents?toDate=2025-04-02T15:00"),
                row("/fetchevents?fromDate=2025-04-02T14:00"),
                row("/fetchevents?fromDate=2025-4-01T14:00&toDate=2025-04-01T15:00"),
                row("/fetchevents?fromDate=2025-04-01T14:00&toDate=2025-04-1T15:00"),
                row("/fetchevents")
            ) { url ->
                val httpResponse = httpClient.get(url)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "fetchMessageDetails endpoint should return list of message details" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetails()
            ebmsMessageDetailsRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/fetchMessageDetails?fromDate=2025-05-08T14:00&toDate=2025-05-08T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageInfo> = httpResponse.body()
            messageInfoList[0].mottakidliste shouldBe messageDetails.requestId.toString()
            messageInfoList[0].datomottat shouldBe messageDetails.savedAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referanse shouldBe messageDetails.refParam
            messageInfoList[0].avsender shouldBe messageDetails.sender
        }
    }

    "fetchMessageDetails endpoint should return empty list if no message details found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetails()
            ebmsMessageDetailsRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/fetchMessageDetails?fromDate=2025-05-09T14:00&toDate=2025-05-09T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<MessageInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "fetchMessageDetails endpoint should return BadRequest if required parameters are missing" {
        withTestApplication { httpClient ->
            forAll(
                row("/fetchMessageDetails?toDate=2025-05-08T15:00"),
                row("/fetchMessageDetails?fromDate=2025-05-08T14:00"),
                row("/fetchMessageDetails?fromDate=2025-5-08T14:00&toDate=2025-05-08T15:00\""),
                row("/fetchMessageDetails?fromDate=2025-05-08T14:00&toDate=2025-05-8T15:00\""),
                row("/fetchMessageDetails")
            ) { url ->
                val httpResponse = httpClient.get(url)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
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

fun buildTestEvent(): Event = Event(
    eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
    requestId = Uuid.random(),
    contentId = "content-1",
    messageId = "message-1",
    eventData = "{\"juridisk_logg_id\":\"1_msg_20250401145445386\"}",
    createdAt = Instant.parse("2025-04-01T12:54:45.386Z")
)

fun buildTestEbmsMessageDetails(): EbmsMessageDetails {
    return EbmsMessageDetails(
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
