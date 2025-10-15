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
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.auth.AZURE_AD_AUTH
import no.nav.emottak.eventmanager.auth.AuthConfig
import no.nav.emottak.eventmanager.constants.Constants.UNKNOWN
import no.nav.emottak.eventmanager.constants.Constants.ZONE_ID_OSLO
import no.nav.emottak.eventmanager.constants.QueryConstants.CONVERSATION_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.CPA_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.FROM_DATE
import no.nav.emottak.eventmanager.constants.QueryConstants.MESSAGE_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.READABLE_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.REQUEST_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.SORT
import no.nav.emottak.eventmanager.constants.QueryConstants.TO_DATE
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.EventInfo
import no.nav.emottak.eventmanager.model.MessageInfo
import no.nav.emottak.eventmanager.model.MessageLogInfo
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.ReadableIdInfo
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.repository.buildAndInsertTestEbmsMessageDetailFindData
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import no.nav.emottak.utils.common.model.DuplicateCheckResponse
import no.nav.security.mock.oauth2.MockOAuth2Server
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
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
    val invalidAudience = "api://dev-fss.team-emottak.some-other-service/.default"

    val withTestApplication = fun (testBlock: suspend (HttpClient) -> Unit) {
        testApplication {
            val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

            application(
                eventManagerModule(eventService, ebmsMessageDetailService, meterRegistry)
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

    "events endpoint should return list of events" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

            val httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-01T15:00", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val eventsPage: Page<EventInfo> = httpResponse.body()
            val events: List<EventInfo> = eventsPage.content
            events[0].eventDate shouldBe testEvent.createdAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            events[0].description shouldBe testEvent.eventType.description
            events[0].eventData shouldBe testEvent.eventData
            events[0].readableId shouldBe testMessageDetails.generateReadableId()
            events[0].role shouldBe testMessageDetails.fromRole
            events[0].service shouldBe testMessageDetails.service
            events[0].action shouldBe testMessageDetails.action
            events[0].referenceParameter shouldBe testMessageDetails.refParam
            events[0].senderName shouldBe testMessageDetails.senderName
        }
    }

    "events endpoint should return list of events page by page" {
        withTestApplication { httpClient ->
            val events: MutableList<Event> = ArrayList()
            val details: MutableList<EbmsMessageDetail> = ArrayList()
            for (i in 1..9) {
                val id = "id$i"
                val ts = "2025-04-01T12:0$i:00.000Z"
                val commonRequestId = Uuid.random()
                val event = buildTestEvent().copy(requestId = commonRequestId, createdAt = Instant.parse(ts))
                val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId, senderName = id)
                eventRepository.insert(event)
                ebmsMessageDetailRepository.insert(testMessageDetails)
                events.add(event)
                details.add(testMessageDetails)
            }

            // default should be descending, try both with explicit sorting and without
            var httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-01T15:00&page=1&size=3&$SORT=desc", getToken)
            httpResponse.status shouldBe HttpStatusCode.OK
            var eventsPage: Page<EventInfo> = httpResponse.body()
            eventsPage.page shouldBe 1
            eventsPage.content.size shouldBe 3
            eventsPage.totalPages shouldBe 3
            eventsPage.totalElements shouldBe 9
            var eventList: List<EventInfo> = eventsPage.content
            eventList[0].senderName shouldBe details[8].senderName
            eventList[1].senderName shouldBe details[7].senderName
            eventList[2].senderName shouldBe details[6].senderName
            httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-01T15:00&page=2&size=3", getToken)
            httpResponse.status shouldBe HttpStatusCode.OK
            eventsPage = httpResponse.body()
            eventsPage.page shouldBe 2
            eventsPage.content.size shouldBe 3
            eventsPage.totalPages shouldBe 3
            eventsPage.totalElements shouldBe 9
            eventList = eventsPage.content
            eventList[0].senderName shouldBe details[5].senderName
            eventList[1].senderName shouldBe details[4].senderName
            eventList[2].senderName shouldBe details[3].senderName
            httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-01T15:00&page=3&size=3", getToken)
            httpResponse.status shouldBe HttpStatusCode.OK
            eventsPage = httpResponse.body()
            eventsPage.page shouldBe 3
            eventsPage.content.size shouldBe 3
            eventsPage.totalPages shouldBe 3
            eventsPage.totalElements shouldBe 9
            eventList = eventsPage.content
            eventList[0].senderName shouldBe details[2].senderName
            eventList[1].senderName shouldBe details[1].senderName
            eventList[2].senderName shouldBe details[0].senderName
        }
    }

    "events endpoint should return list of events when message details are not found" {
        withTestApplication { httpClient ->
            val testEvent = buildTestEvent()

            eventRepository.insert(testEvent)

            val httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-01T15:00", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val eventsPage: Page<EventInfo> = httpResponse.body()
            val events: List<EventInfo> = eventsPage.content
            events[0].eventDate shouldBe testEvent.createdAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            events[0].description shouldBe testEvent.eventType.description
            events[0].eventData shouldBe testEvent.eventData
            events[0].readableId shouldBe ""
            events[0].role shouldBe null
            events[0].service shouldBe null
            events[0].action shouldBe null
            events[0].referenceParameter shouldBe null
            events[0].senderName shouldBe null
        }
    }

    "events endpoint should return empty list if no events found" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

            val httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-02T14:00&$TO_DATE=2025-04-02T15:00", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK
            val eventsPage: Page<EventInfo> = httpResponse.body()
            val events: List<EventInfo> = eventsPage.content
            events.size shouldBe 0
        }
    }

    "events endpoint should return BadRequest if required parameters are missing" {
        withTestApplication { httpClient ->
            forAll(
                row("/events?$TO_DATE=2025-04-02T15:00"),
                row("/events?$FROM_DATE=2025-04-02T14:00"),
                row("/events?$FROM_DATE=2025-4-01T14:00&$TO_DATE=2025-04-01T15:00"),
                row("/events?$FROM_DATE=2025-04-01T14:00&$TO_DATE=2025-04-1T15:00"),
                row("/events?$FROM_DATE=2025-04-01T15:00&$TO_DATE=2025-04-01T14:00"),
                row("/events")
            ) { url ->
                val httpResponse = httpClient.getWithAuth(url, getToken)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "events endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

            val httpResponse = httpClient.get("/events?$FROM_DATE=2025-04-02T14:00&$TO_DATE=2025-04-02T15:00")

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "events endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val commonRequestId = Uuid.random()
            val testEvent = buildTestEvent().copy(requestId = commonRequestId)
            val testMessageDetails = buildTestEbmsMessageDetail().copy(requestId = commonRequestId)

            eventRepository.insert(testEvent)
            ebmsMessageDetailRepository.insert(testMessageDetails)

            val httpResponse = httpClient.getWithAuth("/events?$FROM_DATE=2025-04-02T14:00&$TO_DATE=2025-04-02T15:00", getToken, invalidAudience)

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "message-details endpoint should return list of message details" {
        withTestApplication { httpClient ->
            val messageDetails = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository).first()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.getWithAuth("/message-details?$FROM_DATE=2025-04-30T14:00&$TO_DATE=2025-04-30T15:00&$SORT=asc", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageDetailsPage: Page<MessageInfo> = httpResponse.body()
            val messageInfoList: List<MessageInfo> = messageDetailsPage.content
            messageInfoList[0].readableIdList shouldBe messageDetails.generateReadableId()
            messageInfoList[0].receivedDate shouldBe messageDetails.savedAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referenceParameter shouldBe UNKNOWN
            messageInfoList[0].senderName shouldBe UNKNOWN
            messageInfoList[0].cpaId shouldBe messageDetails.cpaId
            messageInfoList[0].count shouldBe 1
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "message-details endpoint should return empty list if no message details found" {
        withTestApplication { httpClient ->
            val messageDetails = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository).first()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.getWithAuth("/message-details?$FROM_DATE=2025-05-09T14:00&$TO_DATE=2025-05-09T15:00", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK
            val messageDetailsPage: Page<MessageInfo> = httpResponse.body()
            val messageInfoList: List<MessageInfo> = messageDetailsPage.content
            messageInfoList.size shouldBe 0
        }
    }

    "message-details endpoint should return list of message details with time-, readable- and cpa-filter" {
        withTestApplication { httpClient ->
            val messageDetails = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository).first()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            eventRepository.insert(testEvent)

            val readableId = messageDetails.generateReadableId()
            val url = "/message-details?$FROM_DATE=2025-04-30T14:00&$TO_DATE=2025-04-30T15:00&$READABLE_ID=$readableId&$CPA_ID=${messageDetails.cpaId}"
            val httpResponse = httpClient.getWithAuth(url, getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageDetailsPage: Page<MessageInfo> = httpResponse.body()
            val messageInfoList: List<MessageInfo> = messageDetailsPage.content
            messageInfoList.size shouldBe 1
            messageInfoList[0].readableIdList shouldBe readableId
            messageInfoList[0].receivedDate shouldBe messageDetails.savedAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referenceParameter shouldBe UNKNOWN
            messageInfoList[0].senderName shouldBe UNKNOWN
            messageInfoList[0].cpaId shouldBe messageDetails.cpaId
            messageInfoList[0].count shouldBe 1
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "message-details endpoint should return BadRequest if required parameters are missing" {
        withTestApplication { httpClient ->
            forAll(
                row("/message-details?$TO_DATE=2025-05-08T15:00"),
                row("/message-details?$FROM_DATE=2025-05-08T14:00"),
                row("/message-details")
            ) { url ->
                val httpResponse = httpClient.getWithAuth(url, getToken)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "message-details endpoint should return BadRequest if date parameters are invalid" {
        withTestApplication { httpClient ->
            forAll(
                row("/message-details?$FROM_DATE=2025-5-08T14:00&$TO_DATE=2025-05-08T15:00"),
                row("/message-details?$FROM_DATE=2025-05-08T14:00&$TO_DATE=2025-05-8T15:00")
            ) { url ->
                val httpResponse = httpClient.getWithAuth(url, getToken)
                httpResponse.status shouldBe HttpStatusCode.BadRequest
            }
        }
    }

    "message-details endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/message-details?$FROM_DATE=2025-05-09T14:00&$TO_DATE=2025-05-09T15:00")
            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "message-details endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.getWithAuth("/message-details?$FROM_DATE=2025-05-09T14:00&$TO_DATE=2025-05-09T15:00", getToken, invalidAudience)
            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "message-details/<id>/events endpoint should return list of related events info by Request ID" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val relatedEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(relatedEvent)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.getWithAuth("/message-details/${messageDetails.requestId}/events", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageLogInfo> = httpResponse.body()
            messageInfoList.size shouldBe 1
            messageInfoList[0].eventDate shouldBe relatedEvent.createdAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].eventDescription shouldBe relatedEvent.eventType.description
            messageInfoList[0].eventId shouldBe relatedEvent.eventType.value.toString()
        }
    }

    "message-details/<id>/events endpoint should return list of related events info by Readable ID" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val relatedEvent = buildTestEvent().copy(requestId = messageDetails.requestId)
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(relatedEvent)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.getWithAuth("/message-details/${messageDetails.generateReadableId()}/events", getToken)

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageLogInfo> = httpResponse.body()
            messageInfoList.size shouldBe 1
            messageInfoList[0].eventDate shouldBe relatedEvent.createdAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].eventDescription shouldBe relatedEvent.eventType.description
            messageInfoList[0].eventId shouldBe relatedEvent.eventType.value.toString()
        }
    }

    "message-details/<id>/events endpoint should return empty list if no related events found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.get("/message-details/${messageDetails.requestId}/events") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<MessageLogInfo> = httpResponse.body()
            messageInfoList.size shouldBe 0
        }
    }

    "message-details/<id>/events endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.get("/message-details/${messageDetails.requestId}/events")

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "message-details/<id>/events endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val unrelatedEvent = buildTestEvent()

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(unrelatedEvent)

            val httpResponse = httpClient.get("/message-details/${messageDetails.requestId}/events") {
                header(
                    "Authorization",
                    "Bearer ${getToken(invalidAudience).serialize()}"
                )
            }
            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "duplicate-check endpoint should return DuplicateCheckResponse if message is duplicated" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()

            ebmsMessageDetailRepository.insert(messageDetails)

            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = messageDetails.messageId,
                conversationId = messageDetails.conversationId,
                cpaId = messageDetails.cpaId
            )

            val httpResponse = httpClient.post("/message-details/duplicate-check") {
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

    "duplicate-check endpoint should return DuplicateCheckResponse if message is not duplicated" {
        withTestApplication { httpClient ->
            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/message-details/duplicate-check") {
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

    "duplicate-check endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/message-details/duplicate-check") {
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

    "duplicate-check endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val duplicateCheckRequest = DuplicateCheckRequest(
                requestId = Uuid.random().toString(),
                messageId = "test-message-id",
                conversationId = "test-conversation-id",
                cpaId = "test-cpa-id"
            )

            val httpResponse = httpClient.post("/message-details/duplicate-check") {
                contentType(ContentType.Application.Json)
                setBody(duplicateCheckRequest)
            }

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "duplicate-check endpoint should return BadRequest if DuplicateCheckRequest is invalid" {
        withTestApplication { httpClient ->
            val invalidJson = "{\"invalid\":\"request\"}"

            val httpResponse = httpClient.post("/message-details/duplicate-check") {
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

    "duplicate-check endpoint should return BadRequest if required fields are missing" {
        withTestApplication { httpClient ->
            forAll(
                row(mapOf(REQUEST_ID to "", MESSAGE_ID to "test-message-id", CONVERSATION_ID to "test-conversation-id", CPA_ID to "test-cpa-id")),
                row(mapOf(REQUEST_ID to "test-request-id", MESSAGE_ID to "", CONVERSATION_ID to "test-conversation-id", CPA_ID to "test-cpa-id")),
                row(mapOf(REQUEST_ID to "test-request-id", MESSAGE_ID to "test-message-id", CONVERSATION_ID to "", CPA_ID to "test-cpa-id")),
                row(mapOf(REQUEST_ID to "test-request-id", MESSAGE_ID to "test-message-id", CONVERSATION_ID to "test-conversation-id", CPA_ID to "")),
                row(mapOf(REQUEST_ID to "test-request-id", MESSAGE_ID to "test-message-id", CONVERSATION_ID to "test-conversation-id", CPA_ID to "    "))
            ) { duplicateCheckRequest ->
                val httpResponse = httpClient.post("/message-details/duplicate-check") {
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

    "message-details/<id> endpoint should return list of message details by Request ID" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.get("/message-details/${messageDetails.requestId}") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<ReadableIdInfo> = httpResponse.body()
            messageInfoList[0].readableId shouldBe messageDetails.generateReadableId()
            messageInfoList[0].receivedDate shouldBe messageDetails.savedAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referenceParameter shouldBe UNKNOWN
            messageInfoList[0].senderName shouldBe UNKNOWN
            messageInfoList[0].cpaId shouldBe messageDetails.cpaId
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "message-details/<id> endpoint should return list of message details by Readable ID" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(testEvent)

            val httpResponse = httpClient.get("/message-details/${messageDetails.generateReadableId()}") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.OK

            val messageInfoList: List<ReadableIdInfo> = httpResponse.body()
            messageInfoList[0].readableId shouldBe messageDetails.generateReadableId()
            messageInfoList[0].receivedDate shouldBe messageDetails.savedAt.atZone(ZoneId.of(ZONE_ID_OSLO)).toString()
            messageInfoList[0].role shouldBe messageDetails.fromRole
            messageInfoList[0].service shouldBe messageDetails.service
            messageInfoList[0].action shouldBe messageDetails.action
            messageInfoList[0].referenceParameter shouldBe UNKNOWN
            messageInfoList[0].senderName shouldBe UNKNOWN
            messageInfoList[0].cpaId shouldBe messageDetails.cpaId
            messageInfoList[0].status shouldBe "Meldingen er under behandling"
        }
    }

    "message-details/<id> endpoint should return list of message details by Readable ID pattern" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            val testEvent = buildTestEvent().copy(requestId = messageDetails.requestId)

            ebmsMessageDetailRepository.insert(messageDetails)
            eventRepository.insert(testEvent)

            forAll(
                row("/message-details/${messageDetails.generateReadableId().substring(0, 6)}"),
                row("/message-details/${messageDetails.generateReadableId().substring(0, 6).lowercase()}"),
                row("/message-details/${messageDetails.generateReadableId().substring(0, 6).uppercase()}"),
                row("/message-details/${messageDetails.generateReadableId().takeLast(6)}"),
                row("/message-details/${messageDetails.generateReadableId().substring(6, 12)}")
            ) { url ->
                val httpResponse = httpClient.get(url) {
                    header(
                        "Authorization",
                        "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                    )
                }

                httpResponse.status shouldBe HttpStatusCode.OK

                val messageInfoList: List<ReadableIdInfo> = httpResponse.body()
                messageInfoList[0].readableId shouldBe messageDetails.generateReadableId()
            }
        }
    }

    "message-details/<id> endpoint should return empty list if no message details found" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/message-details/${Uuid.random()}") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.OK
            val events: List<MessageInfo> = httpResponse.body()
            events.size shouldBe 0
        }
    }

    "message-details/<id> endpoint should return NotFound if path-parameter is not present" {
        withTestApplication { httpClient ->
            val httpResponse = httpClient.get("/message-details/") {
                header(
                    "Authorization",
                    "Bearer ${getToken(AuthConfig.getScope()).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.NotFound
        }
    }

    "message-details/<id> endpoint should return Unauthorized if access token is missing" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/message-details/${Uuid.random()}")

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    "message-details/<id> endpoint should return Unauthorized if access token is invalid" {
        withTestApplication { httpClient ->
            val messageDetails = buildTestEbmsMessageDetail()
            ebmsMessageDetailRepository.insert(messageDetails)

            val httpResponse = httpClient.get("/message-details/${Uuid.random()}") {
                header(
                    "Authorization",
                    "Bearer ${getToken(invalidAudience).serialize()}"
                )
            }

            httpResponse.status shouldBe HttpStatusCode.Unauthorized
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

suspend fun HttpClient.getWithAuth(
    url: String,
    getToken: (String) -> SignedJWT,
    audience: String = AuthConfig.getScope()
): io.ktor.client.statement.HttpResponse {
    return this.get(url) {
        header(
            "Authorization",
            "Bearer ${getToken(audience).serialize()}"
        )
    }
}
