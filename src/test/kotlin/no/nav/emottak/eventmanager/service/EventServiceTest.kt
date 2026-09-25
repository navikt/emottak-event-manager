package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.constants.Constants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.EventWithStatus
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.model.dto.PageDto
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

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
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

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
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

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 0) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 0) { ebmsMessageDetailRepository.update(any()) }
    }

    "Should update conversation status to ERROR on an error event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_ENCRYPTION_FAILED
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.ERROR,
                eventType = EventType.MESSAGE_ENCRYPTION_FAILED,
                datetime = any()
            )
        } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.ERROR,
                eventType = EventType.MESSAGE_ENCRYPTION_FAILED,
                datetime = any()
            )
        }
    }

    "Should update conversation status to INFORMATION on retry event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.RETRY_TRIGGED
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.INFORMATION,
                eventType = EventType.RETRY_TRIGGED,
                datetime = any()
            )
        } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.INFORMATION,
                eventType = EventType.RETRY_TRIGGED,
                datetime = any()
            )
        }
    }

    "Should not update conversation status on other INFORMATION events" {
        val testTransportEvent1 = buildTestTransportEvent().copy(eventType = EventType.MESSAGE_ENCRYPTED)
        val testEvent1 = Event.fromTransportModel(testTransportEvent1)

        val testTransportEvent2 = buildTestTransportEvent().copy(eventType = EventType.MESSAGE_VALIDATED_AGAINST_CPA)
        val testEvent2 = Event.fromTransportModel(testTransportEvent2)

        val testTransportEvent3 = buildTestTransportEvent().copy(eventType = EventType.REFERENCE_RETRIEVED)
        val testEvent3 = Event.fromTransportModel(testTransportEvent3)

        coEvery { eventRepository.insert(testEvent1) } returns testEvent1.requestId
        coEvery { eventRepository.insert(testEvent2) } returns testEvent2.requestId
        coEvery { eventRepository.insert(testEvent3) } returns testEvent3.requestId
        coEvery {
            conversationStatusRepository.update(any(), any(), any(), any())
        } returns true

        eventService.process(testTransportEvent1.toByteArray())
        eventService.process(testTransportEvent2.toByteArray())
        eventService.process(testTransportEvent3.toByteArray())

        coVerify(exactly = 3) { eventRepository.insert(any()) }
        coVerify(exactly = 0) {
            conversationStatusRepository.update(any(), any(), any(), any())
        }
    }

    "Should update conversation status to PROCESSING_COMPLETED on MESSAGE_SENT_VIA_HTTP event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_SENT_VIA_HTTP
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGE_SENT_VIA_HTTP,
                datetime = any()
            )
        } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) {
            conversationStatusRepository.update(
                id = testEvent.conversationId!!,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGE_SENT_VIA_HTTP,
                datetime = any()
            )
        }
    }

    "Should update conversation status to PROCESSING_COMPLETED on MESSAGEFLOW_COMPLETED event" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGEFLOW_COMPLETED,
            conversationId = "my-conversation-id"
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId).copy(
            action = ACKNOWLEDGMENT_ACTION,
            fromRole = "Ytelsesutbetaler",
            conversationId = "my-conversation-id"
        )

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail
        coEvery {
            conversationStatusRepository.update(
                id = testMessageDetail.conversationId,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGEFLOW_COMPLETED,
                datetime = any()
            )
        } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 0) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 1) {
            conversationStatusRepository.update(
                id = testMessageDetail.conversationId,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGEFLOW_COMPLETED,
                datetime = any()
            )
        }
    }

    "Should call ebmsMessageDetailRepository.findByRequestId when conversationId is null when update conversation status is needed" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGEFLOW_COMPLETED,
            conversationId = null
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)
        val testMessageDetail = buildTestEbmsMessageDetail(testTransportEvent.requestId).copy(
            action = ACKNOWLEDGMENT_ACTION,
            fromRole = "Ytelsesutbetaler",
            conversationId = "my-conversation-id"
        )

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns testMessageDetail
        coEvery {
            conversationStatusRepository.update(
                id = testMessageDetail.conversationId,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGEFLOW_COMPLETED,
                datetime = any()
            )
        } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 1) {
            conversationStatusRepository.update(
                id = testMessageDetail.conversationId,
                status = EventStatusEnum.PROCESSING_COMPLETED,
                eventType = EventType.MESSAGEFLOW_COMPLETED,
                datetime = any()
            )
        }
    }

    "Should not abort inserting event when ebmsMessageDetailRepository.findByRequestId does not find the corresponding message detail" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGEFLOW_COMPLETED,
            conversationId = null
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId
        coEvery { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) } returns null
        coEvery { conversationStatusRepository.update(any(), EventStatusEnum.PROCESSING_COMPLETED, any()) } returns true

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestId(testEvent.requestId) }
        coVerify(exactly = 0) { conversationStatusRepository.update(any(), EventStatusEnum.PROCESSING_COMPLETED, any()) }
        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
    }

    "Should not update conversation status on event types of status INFORMATION" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventType = EventType.MESSAGE_VALIDATED_AGAINST_XSD
        )
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testTransportEvent.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
        coVerify(exactly = 0) { conversationStatusRepository.update(any(), any(), any()) }
    }

    "Should call EventRepository and EbmsMessageDetailRepository on fetching events" {
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeInterval(from, to, pageable = any()) } returns PageDto(
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

        coVerify(exactly = 1) { eventRepository.findByTimeInterval(from, to) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeInterval(from, to, role = roleFilter) } returns PageDto(
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

        coVerify(exactly = 1) { eventRepository.findByTimeInterval(from, to, role = roleFilter) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeInterval(from, to, service = serviceFilter) } returns PageDto(
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

        coVerify(exactly = 1) { eventRepository.findByTimeInterval(from, to, service = serviceFilter) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call database repository on fetching events by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testEvent)
        val pageable = Pageable(1, list.size)
        coEvery { eventRepository.findByTimeInterval(from, to, action = actionFilter) } returns PageDto(
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

        coVerify(exactly = 1) { eventRepository.findByTimeInterval(from, to, action = actionFilter) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call EventRepository on fetching events related to a specific message by Request ID" {
        val testEvent = buildTestEvent()
        val testEventWithStatus = EventWithStatus(testEvent, EventStatusEnum.INFORMATION)

        coEvery { eventRepository.findByRequestIdJoinEventType(testEvent.requestId) } returns listOf(testEventWithStatus)

        val eventsList = eventService.fetchMessageLogInfo(testEvent.requestId.toString())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].eventDescription shouldBe testEvent.eventType.description
        eventsList[0].eventId shouldBe testEvent.eventType.value.toString()
        eventsList[0].eventStatus shouldBe EventStatusEnum.INFORMATION.dbValue

        coVerify(exactly = 1) { eventRepository.findByRequestIdJoinEventType(testEvent.requestId) }
    }

    "Should call database on fetching events related to a specific message by Readable ID" {
        val testMessageDetail = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent(requestId = testMessageDetail.requestId)
        val testEventWithStatus = EventWithStatus(testEvent, EventStatusEnum.PROCESSING_COMPLETED)

        coEvery { eventRepository.findByRequestIdJoinEventType(testEvent.requestId) } returns listOf(testEventWithStatus)
        coEvery { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) } returns testMessageDetail

        val eventsList = eventService.fetchMessageLogInfo(testMessageDetail.generateReadableId())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.toOsloZone().toString()
        eventsList[0].eventDescription shouldBe testEvent.eventType.description
        eventsList[0].eventId shouldBe testEvent.eventType.value.toString()
        eventsList[0].eventStatus shouldBe EventStatusEnum.PROCESSING_COMPLETED.dbValue

        coVerify(exactly = 1) { eventRepository.findByRequestIdJoinEventType(testEvent.requestId) }
        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) }
    }

    "fetchMessageLogInfo should return empty list if message is not found by Readable ID" {
        val testMessageDetail = buildTestEbmsMessageDetail()

        coEvery { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) } returns null

        val eventsList = eventService.fetchMessageLogInfo(testMessageDetail.generateReadableId())

        eventsList.size shouldBe 0

        coVerify(exactly = 1) { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) }
    }

    "Should ignore unknown keys for event" {
        val testTransportEvent = buildTestTransportEvent()
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        var byteArrayAsString = String(testTransportEvent.toByteArray())
        byteArrayAsString = byteArrayAsString.replace(
            oldValue = "\"eventType\":\"MESSAGE_SAVED_IN_JURIDISK_LOGG\"",
            newValue = "\"eventType\":\"MESSAGE_SAVED_IN_JURIDISK_LOGG\",\"unknownKey\":\"Some value\""
        )
        eventService.process(byteArrayAsString.toByteArray())

        coVerify(exactly = 1) { eventRepository.insert(testEvent) }
    }
})
