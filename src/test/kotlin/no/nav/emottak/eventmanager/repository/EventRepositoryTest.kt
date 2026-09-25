package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant
import kotlin.uuid.Uuid

class EventRepositoryTest : RepositoryTestBase({

    "Should retrieve an event by eventId" {
        val testEvent = buildTestEvent()

        val eventId = eventRepository.insert(testEvent)
        val retrievedEvent = eventRepository.findById(eventId)

        retrievedEvent shouldBe testEvent.copy()
    }

    "Should retrieve an event with empty eventData by eventId" {
        val testTransportEvent = buildTestTransportEvent().copy(
            eventData = "{}"
        )

        val testEvent = Event.fromTransportModel(testTransportEvent)

        val eventId = eventRepository.insert(testEvent)
        val retrievedEvent = eventRepository.findById(eventId)

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

        val retrievedEvents = eventRepository.findByRequestId(sharedRequestId)

        retrievedEvents shouldContainExactlyInAnyOrder listOf(event1.copy(), event2.copy())
    }

    "Should find events by time interval" {
        val messageDetails = buildTestEbmsMessageDetail()

        val eventInTimeInterval = buildTestEvent().copy(
            requestId = messageDetails.requestId,
            conversationId = messageDetails.conversationId,
            createdAt = Instant.parse("2025-04-01T14:54:45.386Z")
        )

        val eventOutOfTimeInterval = buildTestEvent().copy(
            requestId = messageDetails.requestId,
            conversationId = messageDetails.conversationId,
            createdAt = Instant.parse("2025-04-01T15:54:45.386Z")
        )

        eventRepository.insert(eventInTimeInterval)
        eventRepository.insert(eventOutOfTimeInterval)
        ebmsMessageDetailRepository.upsert(messageDetails)

        val retrievedEvents = eventRepository.findByTimeInterval(
            Instant.parse("2025-04-01T14:00:00Z"),
            Instant.parse("2025-04-01T15:00:00Z")
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event shouldBe eventInTimeInterval
        retrievedEvents[0].status shouldBe EventStatusEnum.INFORMATION
    }

    "Should find events by time interval, page by page" {
        val messageDetails = buildTestEbmsMessageDetail()
        ebmsMessageDetailRepository.upsert(messageDetails)

        val events: MutableList<Event> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val event = buildTestEvent().copy(
                requestId = messageDetails.requestId,
                conversationId = messageDetails.conversationId,
                contentId = id,
                createdAt = Instant.parse(ts)
            )
            eventRepository.insert(event)
            events.add(event)
        }

        val page1 = Pageable(1, 4)
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page1)
        retrievedEvents.page shouldBe 1
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[0]
        retrievedEvents.content[1].event shouldBe events[1]
        retrievedEvents.content[2].event shouldBe events[2]
        retrievedEvents.content[3].event shouldBe events[3]

        val page2 = page1.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page2)
        retrievedEvents.page shouldBe 2
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[4]
        retrievedEvents.content[1].event shouldBe events[5]
        retrievedEvents.content[2].event shouldBe events[6]
        retrievedEvents.content[3].event shouldBe events[7]

        val page3 = page2.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page3)
        retrievedEvents.page shouldBe 3
        retrievedEvents.content.size shouldBe 1
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[8]
    }

    "Should find events by time interval, page by page, DESCENDING" {
        val messageDetails = buildTestEbmsMessageDetail()
        ebmsMessageDetailRepository.upsert(messageDetails)

        val events: MutableList<Event> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val event = buildTestEvent().copy(
                requestId = messageDetails.requestId,
                conversationId = messageDetails.conversationId,
                contentId = id,
                createdAt = Instant.parse(ts)
            )
            eventRepository.insert(event)
            events.add(event)
        }

        val page1 = Pageable(1, 4, "DESC")
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page1)
        retrievedEvents.page shouldBe 1
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[8]
        retrievedEvents.content[1].event shouldBe events[7]
        retrievedEvents.content[2].event shouldBe events[6]
        retrievedEvents.content[3].event shouldBe events[5]

        val page2 = page1.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page2)
        retrievedEvents.page shouldBe 2
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[4]
        retrievedEvents.content[1].event shouldBe events[3]
        retrievedEvents.content[2].event shouldBe events[2]
        retrievedEvents.content[3].event shouldBe events[1]

        val page3 = page2.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, pageable = page3)
        retrievedEvents.page shouldBe 3
        retrievedEvents.content.size shouldBe 1
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content[0].event shouldBe events[0]
    }

    "Should retrieve events by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(fromRole = roleFilter)

        val event1 = buildTestEvent(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent().copy(
            requestId = messageDetails2.requestId,
            eventType = EventType.MESSAGE_SENT_VIA_HTTP
        )

        ebmsMessageDetailRepository.upsert(messageDetails1)
        ebmsMessageDetailRepository.upsert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeInterval(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            role = roleFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event shouldBe event2
        retrievedEvents[0].status shouldBe EventStatusEnum.PROCESSING_COMPLETED
    }

    "Should retrieve events by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(service = serviceFilter)

        val event1 = buildTestEvent(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent().copy(
            requestId = messageDetails2.requestId,
            eventType = EventType.ERROR_WHILE_SENDING_MESSAGE_TO_FAGSYSTEM
        )

        ebmsMessageDetailRepository.upsert(messageDetails1)
        ebmsMessageDetailRepository.upsert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeInterval(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            service = serviceFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event shouldBe event2
        retrievedEvents[0].status shouldBe EventStatusEnum.ERROR
    }

    "Should retrieve events by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(action = actionFilter)

        val event1 = buildTestEvent(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.upsert(messageDetails1)
        ebmsMessageDetailRepository.upsert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeInterval(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            action = actionFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event shouldBe event2
        retrievedEvents[0].status shouldBe EventStatusEnum.INFORMATION
    }

    "Should not retrieve events where conversation_id is null or blank" {
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(action = "EgenandelForesporsel")

        val event1 = buildTestEvent(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.upsert(messageDetails1)
        ebmsMessageDetailRepository.upsert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val eventConversationIdNull = Event(
            eventType = EventType.MESSAGE_READ_FROM_QUEUE,
            requestId = messageDetails1.requestId,
            messageId = "2af3496a-8d33-4af0-ab3e-fa1da4cd193e",
            eventData = "{\"queue_name\": \"team-emottak.smtp.out.ebxml.payload\"}",
            createdAt = Instant.parse("2025-04-01T16:59:59.000Z")
        )
        eventRepository.insert(eventConversationIdNull)

        val eventConversationIdBlank = eventConversationIdNull.copy(
            requestId = messageDetails1.requestId,
            messageId = "<L6ZTMPZR9TU4.MDI908S9AJ281@sender-97cbfdc68-p2kwl>",
            conversationId = ""
        )
        eventRepository.insert(eventConversationIdBlank)

        var retrievedEvents = eventRepository.findByTimeInterval(
            from = Instant.parse("2025-04-01T00:00:00Z"),
            to = Instant.parse("2025-04-02T00:00:00Z"),
            pageable = Pageable(1, 25, "DESC")
        ).content

        retrievedEvents.size shouldBe 2
        retrievedEvents[0].event.requestId shouldBe event2.requestId
        retrievedEvents[1].event.requestId shouldBe event1.requestId
    }

    "Should not retrieve events where request_id do not exists in message details-table" {
        val messageDetails1 = buildTestEbmsMessageDetail()
        val event1 = buildTestEvent(requestId = messageDetails1.requestId)

        ebmsMessageDetailRepository.upsert(messageDetails1)
        eventRepository.insert(event1)

        val eventConversationIdOnlyInEvents = Event(
            eventType = EventType.MESSAGE_READ_FROM_QUEUE,
            requestId = Uuid.random(),
            messageId = "2af3496a-8d33-4af0-ab3e-fa1da4cd193e",
            eventData = "{\"queue_name\": \"team-emottak.smtp.out.ebxml.payload\"}",
            createdAt = Instant.parse("2025-04-01T16:59:59.000Z"),
            conversationId = Uuid.random().toString()
        )
        eventRepository.insert(eventConversationIdOnlyInEvents)

        var retrievedEvents = eventRepository.findByTimeInterval(
            from = Instant.parse("2025-04-01T00:00:00Z"),
            to = Instant.parse("2025-04-02T00:00:00Z"),
            pageable = Pageable(1, 25, "DESC")
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event.requestId shouldBe event1.requestId
    }

    "Should not retrieve events where corresponding message details have blank conversationId" {
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(conversationId = "")

        val event1 = buildTestEvent(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.upsert(messageDetails1)
        ebmsMessageDetailRepository.upsert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        var retrievedEvents = eventRepository.findByTimeInterval(
            from = Instant.parse("2025-04-01T00:00:00Z"),
            to = Instant.parse("2025-04-02T00:00:00Z"),
            pageable = Pageable(1, 25, "DESC")
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents[0].event.requestId shouldBe event1.requestId
    }
})
