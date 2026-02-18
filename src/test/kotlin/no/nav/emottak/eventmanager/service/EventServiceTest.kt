package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.constants.Constants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.eventmanager.constants.Constants.NOT_APPLICABLE_ROLE
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.repository.ConversationStatusRepository
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.eventmanager.repository.buildTestTransportEvent
import no.nav.emottak.utils.common.toOsloZone
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant

class EventServiceTest : StringSpec({

    val eventRepository = mockk<EventRepository>()
    val ebmsMessageDetailRepository = mockk<EbmsMessageDetailRepository>()
    val conversationStatusRepository = mockk<ConversationStatusRepository>()
    val eventService = EventService(eventRepository, ebmsMessageDetailRepository, conversationStatusRepository)

    beforeEach {
        clearAllMocks()
    }

    "Should call database repository on processing en event" {
        val testTransportEvent = buildTestTransportEvent()
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
    }

    "Should update message detail on EventType.MESSAGE_VALIDATED_AGAINST_CPA event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_VALIDATED_AGAINST_CPA,
            eventData = "{\"sender_name\":\"test\"}"
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail
        coEvery { ebmsMessageDetailRepository.update(any()) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.update(any()) }
    }

    "Should not update message detail on EventType.MESSAGE_VALIDATED_AGAINST_CPA event if eventData is empty" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_VALIDATED_AGAINST_CPA
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail
        coEvery { ebmsMessageDetailRepository.update(any()) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 0) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 0) { ebmsMessageDetailRepository.update(any()) }
    }

    "Should update conversation status on error event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_ENCRYPTION_FAILED
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.ERROR) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.ERROR) }
    }

    "Should update conversation status on retry event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.RETRY_TRIGGED
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.INFORMATION) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.INFORMATION) }
    }

    "Should update conversation status to complete on MESSAGE_SENT_VIA_HTTP event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_SENT_VIA_HTTP
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.PROCESSING_COMPLETED) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { conversationStatusRepository.update(testEvent.conversationId!!, EventStatusEnum.PROCESSING_COMPLETED) }
    }

    "Should update conversation status to complete on MESSAGE_SENT_VIA_SMTP event when message detail is Acknowledgment from consumer" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_SENT_VIA_SMTP,
            conversationId = null // smtp-transport sender ikke med conversationId
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId).copy(
            action = ACKNOWLEDGMENT_ACTION,
            fromRole = "Ytelsesutbetaler",
            conversationId = "my-conversation-id"
        )

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail
        coEvery { conversationStatusRepository.update(testMessageDetail.conversationId, EventStatusEnum.PROCESSING_COMPLETED) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 1) { conversationStatusRepository.update(testMessageDetail.conversationId, EventStatusEnum.PROCESSING_COMPLETED) }
    }

    "Should not update conversation status to complete on MESSAGE_SENT_VIA_SMTP event when message detail is Acknowledgment but from NAV" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_SENT_VIA_SMTP,
            conversationId = null // smtp-transport sender ikke med conversationId
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId).copy(
            action = ACKNOWLEDGMENT_ACTION,
            fromRole = NOT_APPLICABLE_ROLE, // From NAV
            conversationId = "my-conversation-id"
        )

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 0) { conversationStatusRepository.update(any(), any()) }
    }

    "Should not update conversation status on event types of status INFORMATION" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_VALIDATED_AGAINST_XSD
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
        coVerify(exactly = 0) { conversationStatusRepository.update(any(), any()) }
    }

    "Should call EventRepository and EbmsMessageDetailRepository on fetching events" {
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeInterval(from, to, any()) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsPage = eventService.fetchEvents(from, to)
        val eventsList = eventsPage.content
        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].description shouldBe testEvent.eventType.description
        eventsList[0].eventData shouldBe testEvent.eventData

        coVerify { eventRepository.findByTimeInterval(from, to) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, role = roleFilter) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsPage = eventService.fetchEvents(from, to, role = roleFilter)
        val eventsList = eventsPage.content
        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].description shouldBe testEvent.eventType.description
        eventsList[0].eventData shouldBe testEvent.eventData

        coVerify { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, role = roleFilter) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, service = serviceFilter) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsPage = eventService.fetchEvents(from, to, service = serviceFilter)
        val eventsList = eventsPage.content
        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].description shouldBe testEvent.eventType.description
        eventsList[0].eventData shouldBe testEvent.eventData

        coVerify { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, service = serviceFilter) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, action = actionFilter) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsPage = eventService.fetchEvents(from, to, action = actionFilter)
        val eventsList = eventsPage.content
        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].description shouldBe testEvent.eventType.description
        eventsList[0].eventData shouldBe testEvent.eventData

        coVerify { eventRepository.findByTimeIntervalJoinMessageDetail(from, to, action = actionFilter) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call EventRepository on fetching events related to a specific message by Request ID" {
        val testEvent = buildTestEvent()

        coEvery { eventRepository.findByRequestId(testEvent.requestId) } returns listOf(testEvent)

        val eventsList = eventService.fetchMessageLogInfo(testEvent.requestId.toString())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].eventDescription shouldBe testEvent.eventType.description
        eventsList[0].eventId shouldBe testEvent.eventType.value.toString()

        coVerify { eventRepository.findByRequestId(testEvent.requestId) }
    }

    "Should call database on fetching events related to a specific message by Readable ID" {
        val testMessageDetail = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent(requestId = testMessageDetail.requestId)

        coEvery { eventRepository.findByRequestId(testEvent.requestId) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) } returns testMessageDetail

        val eventsList = eventService.fetchMessageLogInfo(testMessageDetail.generateReadableId())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].eventDescription shouldBe testEvent.eventType.description
        eventsList[0].eventId shouldBe testEvent.eventType.value.toString()

        coVerify { eventRepository.findByRequestId(testEvent.requestId) }
        coVerify { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) }
    }

    "fetchMessageLogInfo should return empty list if message is not found by Readable ID" {
        val testMessageDetail = buildTestEbmsMessageDetail()

        coEvery { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) } returns null

        val eventsList = eventService.fetchMessageLogInfo(testMessageDetail.generateReadableId())

        eventsList.size shouldBe 0

        coVerify { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) }
    }
})
