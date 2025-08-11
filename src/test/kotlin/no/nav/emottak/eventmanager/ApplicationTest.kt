package no.nav.emottak.eventmanager

import com.nimbusds.jwt.SignedJWT
import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.model.MessageLoggInfo
import no.nav.emottak.eventmanager.model.MottakIdInfo
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import no.nav.emottak.utils.common.model.DuplicateCheckResponse
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.testcontainers.containers.PostgreSQLContainer
import java.time.ZoneId
import kotlin.uuid.Uuid

class ApplicationTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database

    lateinit var mockOAuth2Server: MockOAuth2Server

    lateinit var eventRepository: EventRepository
    lateinit var ebmsMessageDetailRepository: EbmsMessageDetailRepository
    lateinit var eventTypeRepository: EventTypeRepository

    lateinit var eventService: EventService
    lateinit var ebmsMessageDetailService: EbmsMessageDetailService

    val getToken: (String) -> SignedJWT = { audience: String ->
        mockOAuth2Server.issueToken(
            issuerId = AZURE_AD_AUTH,
            audience = audience,
            subject = "testUser"
        )
    }

    val withTestApplication = fun (testBlock: suspend (HttpClient) -> Unit) {
        testApplication {
            application(
                eventManagerModule(eventService, ebmsMessageDetailService)
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

        mockOAuth2Server = MockOAuth2Server().also { it.start(port = 3344) }

        eventRepository = EventRepository(db)
        ebmsMessageDetailRepository = EbmsMessageDetailRepository(db)
        eventTypeRepository = EventTypeRepository(db)

        eventService = EventService(eventRepository, ebmsMessageDetailRepository)
        ebmsMessageDetailService = EbmsMessageDetailService(eventRepository, ebmsMessageDetailRepository, eventTypeRepository)
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
        withTestApplication { httpClient ->
            httpClient.get("/").apply {
                status shouldBe HttpStatusCode.OK
            }
        }
    }

    "fetchevents endpoint should return list of events" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

            val httpResponse = httpClient.get("/fetchevents?fromDate=2025-04-01T14:00&toDate=2025-04-01T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK

            val events: List<EventInfo> = httpResponse.body()
            events[0].hendelsedato shouldBe testEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            events[0].hendelsedeskr shouldBe testEvent.eventType.description
            events[0].tillegsinfo shouldBe testEvent.eventData
            events[0].requestid shouldBe testMessageDetails.requestId.toString()
            events[0].mottakid shouldBe testMessageDetails.calculateMottakId()
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
            events[0].hendelsedeskr shouldBe testEvent.eventType.description
            events[0].tillegsinfo shouldBe testEvent.eventData
            events[0].requestid shouldBe testEvent.requestId.toString()
            events[0].mottakid shouldBe ""
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
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

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
            val messageDetails = buildTestEbmsMessageDetail()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.get("/fetchMessageDetails?fromDate=2025-05-08T14:00&toDate=2025-05-08T15:00")

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageInfo> = httpResponse.body()
            messageInfoList[0].mottakidliste shouldBe messageDetails.calculateMottakId()
            messageInfoList[0].datomottat shouldBe messageDetails.savedAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referanse shouldBe "Unknown"
            messageInfoList[0].avsender shouldBe "Unknown"
            messageInfoList[0].cpaid shouldBe messageDetails.cpaId
            messageInfoList[0].antall shouldBe 1
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "fetchMessageDetails endpoint should return empty list if no message details found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

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

    "fetchMessageLoggInfo endpoint should return list of related events info" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val relatedEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(relatedEvent)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.get("/fetchMessageLoggInfo?requestId=${messageDetails.requestId}")

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageLoggInfo> = httpResponse.body()
            messageInfoList.size shouldBe 1
            messageInfoList[0].hendelsesdato shouldBe relatedEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            messageInfoList[0].hendelsesbeskrivelse shouldBe relatedEvent.eventType.description
            messageInfoList[0].hendelsesid shouldBe relatedEvent.eventType.value.toString()
        }
    }

    "fetchMessageLoggInfo endpoint should return empty list if no related events found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.get("/fetchMessageLoggInfo?requestId=${messageDetails.requestId}")

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageLoggInfo> = httpResponse.body()
            messageInfoList.size shouldBe 0
        }
    }

    "fetchMessageLoggInfo endpoint should return BadRequest if parameters are missing or invalid" {
        withTestApplication { httpClient ->
            forAll(
                row("/fetchMessageLoggInfo?requestId=invalid-uuid"),
                row("/fetchMessageLoggInfo")
            ) { url ->
                val httpResponse = httpClient.get(url)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "duplicateCheck endpoint should return DuplicateCheckResponse if message is duplicated" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()

            ebmsMessageDetailRepository.insert(messageDetails)

            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = messageDetails.messageId,
                conversationId = messageDetails.conversationId,
                cpaId = messageDetails.cpaId
            )

            val httpResponse = httpClient.post("/duplicateCheck") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
                contentType(ContentType.Application.Json)
                setBody(duplicateCheckRequest)
            }

            httpResponse.status shouldBe HttpStatusCode.OK

            val duplicateCheckResponse: DuplicateCheckResponse = httpResponse.body()
            duplicateCheckResponse.requestId shouldBe duplicateCheckRequest.requestId
            duplicateCheckResponse.isDuplicate shouldBe true
        }
    }

    "duplicateCheck endpoint should return DuplicateCheckResponse if message is not duplicated" {
        withTestApplication { httpClient ->
            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/duplicateCheck") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
                contentType(ContentType.Application.Json)
                setBody(duplicateCheckRequest)
            }

            httpResponse.status shouldBe HttpStatusCode.OK

            val duplicateCheckResponse: DuplicateCheckResponse = httpResponse.body()
            duplicateCheckResponse.requestId shouldBe duplicateCheckRequest.requestId
            duplicateCheckResponse.isDuplicate shouldBe false
        }
    }

    "duplicateCheck endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val invalidAudience = "api://dev-fss.team-emottak.some-other-service/.default"

            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/duplicateCheck") {
                header(
                    "Authorization",
                    "Bearer ${getToken(invalidAudience).serialize()}"
                )
                contentType(ContentType.Application.Json)
                setBody(duplicateCheckRequest)
            }

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "duplicateCheck endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/duplicateCheck") {
                contentType(ContentType.Application.Json)
                setBody(duplicateCheckRequest)
            }

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "duplicateCheck endpoint should return BadRequest if DuplicateCheckRequest is invalid" {
        withTestApplication { httpClient ->
            val invalidJson = "{\"invalid\":\"request\"}"

            val httpResponse = httpClient.post("/duplicateCheck") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
                contentType(ContentType.Application.Json)
                setBody(invalidJson)
            }

            httpResponse.status shouldBe HttpStatusCode.BadRequest

            val errorResponse = httpResponse.body<String>()
            errorResponse shouldStartWith "DuplicateCheckRequest is not valid"
        }
    }

    "duplicateCheck endpoint should return BadRequest if required fields are missing" {
        withTestApplication { httpClient ->
            forAll(
                row(mapOf("requestId" to "", "messageId" to "test-message-id", "conversationId" to "test-conversation-id", "cpaId" to "test-cpa-id")),
                row(mapOf("requestId" to "test-request-id", "messageId" to "", "conversationId" to "test-conversation-id", "cpaId" to "test-cpa-id")),
                row(mapOf("requestId" to "test-request-id", "messageId" to "test-message-id", "conversationId" to "", "cpaId" to "test-cpa-id")),
                row(mapOf("requestId" to "test-request-id", "messageId" to "test-message-id", "conversationId" to "test-conversation-id", "cpaId" to "")),
                row(mapOf("requestId" to "test-request-id", "messageId" to "test-message-id", "conversationId" to "test-conversation-id", "cpaId" to "    "))
            ) { duplicateCheckRequest ->
                val httpResponse = httpClient.post("/duplicateCheck") {
                    header(
                        "Authorization",
                        "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                    )
                    contentType(ContentType.Application.Json)
                    setBody(duplicateCheckRequest)
                }

                httpResponse.status shouldBe HttpStatusCode.BadRequest

                val errorResponse = httpResponse.body<String>()
                errorResponse shouldStartWith "Required request parameter is missing"
            }
        }
    }

    "fetchMottakIdInfo endpoint should return list of message details" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.get("/fetchMottakIdInfo?requestId=${messageDetails.requestId}")

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MottakIdInfo> = httpResponse.body()
            messageInfoList[0].mottakid shouldBe messageDetails.requestId.toString()
            messageInfoList[0].datomottat shouldBe messageDetails.savedAt.atZone(ZoneId.of("Europe/Oslo")).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referanse shouldBe "Unknown"
            messageInfoList[0].avsender shouldBe "Unknown"
            messageInfoList[0].cpaid shouldBe messageDetails.cpaId
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "fetchMottakIdInfo endpoint should return empty list if no message details found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/fetchMottakIdInfo?requestId=${Uuid.random()}")

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<MessageInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "fetchMottakIdInfo endpoint should return BadRequest if required parameters are missing or invalid" {
        withTestApplication { httpClient ->
            forAll(
                row("/fetchMottakIdInfo?requestId=invalid-uuid"),
                row("/fetchMottakIdInfo")
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
