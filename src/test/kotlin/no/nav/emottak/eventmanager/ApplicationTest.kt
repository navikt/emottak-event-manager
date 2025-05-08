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

    lateinit var httpClient: HttpClient

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        eventRepository = EventsRepository(db)
        eventService = EventService(eventRepository)

        ebmsMessageDetailsRepository = EbmsMessageDetailsRepository(db)
        ebmsMessageDetailsService = EbmsMessageDetailsService(ebmsMessageDetailsRepository)
    }

    afterSpec {
        dbContainer.stop()
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
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val event = buildTestEvent()
            eventRepository.insert(event)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-01T14:00&toDate=2025-04-01T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<EventInfo> = httpResponse.body()
            events[0].mottakid shouldBe event.requestId.toString()
        }
    }

    "fetchevents endpoint should return empty list if no events found" {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val event = buildTestEvent()
            eventRepository.insert(event)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-02T14:00&toDate=2025-04-02T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<EventInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "fetchevents endpoint should return BadRequest if required parameters are missing" {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            forAll(
                row("/fetchevents?toDate=2025-04-02T15:00"),
                row("/fetchevents?fromDate=2025-04-02T14:00"),
                row("/fetchevents")
            ) { url ->
                val httpResponse = httpClient.get(url)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "fetchMessageDetails endpoint should return list of message details" {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

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
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            val messageDetails = buildTestEbmsMessageDetails()
            ebmsMessageDetailsRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/fetchMessageDetails?fromDate=2025-05-09T14:00&toDate=2025-05-09T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<MessageInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "fetchMessageDetails endpoint should return BadRequest if required parameters are missing" {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailsService)
            )

            val httpClient = createClient {
                install(ContentNegotiation) {
                    json()
                }
            }

            forAll(
                row("/fetchMessageDetails?toDate=2025-05-08T15:00"),
                row("/fetchMessageDetails?fromDate=2025-05-08T14:00"),
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
