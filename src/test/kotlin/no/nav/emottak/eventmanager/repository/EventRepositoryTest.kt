package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.model.Pageable
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

        val eventInTimeInterval = buildTestEvent().copy(
            createdAt = Instant.parse("2025-04-01T14:54:45.386Z")
        )

        val eventOutOfTimeInterval = buildTestEvent().copy(
            createdAt = Instant.parse("2025-04-01T15:54:45.386Z")
        )

        eventRepository.insert(eventInTimeInterval)
        eventRepository.insert(eventOutOfTimeInterval)

        val retrievedEvents = eventRepository.findByTimeInterval(
            Instant.parse("2025-04-01T14:00:00Z"),
            Instant.parse("2025-04-01T15:00:00Z")
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents shouldContain eventInTimeInterval
    }

    "Should find events by time interval, page by page" {

        val events: MutableList<Event> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val event = buildTestEvent().copy(contentId = id, createdAt = Instant.parse(ts))
            eventRepository.insert(event)
            events.add(event)
        }

        val page1 = Pageable(1, 4)
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedEvents = eventRepository.findByTimeInterval(from, to, page1)
        retrievedEvents.page shouldBe 1
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[0]
        retrievedEvents.content shouldContain events[1]
        retrievedEvents.content shouldContain events[2]
        retrievedEvents.content shouldContain events[3]

        val page2 = page1.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, page2)
        retrievedEvents.page shouldBe 2
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[4]
        retrievedEvents.content shouldContain events[5]
        retrievedEvents.content shouldContain events[6]
        retrievedEvents.content shouldContain events[7]

        val page3 = page2.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, page3)
        retrievedEvents.page shouldBe 3
        retrievedEvents.content.size shouldBe 1
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[8]
    }

    "Should find events by time interval, page by page, DESCENDING" {

        val events: MutableList<Event> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val event = buildTestEvent().copy(contentId = id, createdAt = Instant.parse(ts))
            eventRepository.insert(event)
            events.add(event)
        }

        val page1 = Pageable(1, 4, "DESC")
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedEvents = eventRepository.findByTimeInterval(from, to, page1)
        retrievedEvents.page shouldBe 1
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[8]
        retrievedEvents.content shouldContain events[7]
        retrievedEvents.content shouldContain events[6]
        retrievedEvents.content shouldContain events[5]

        val page2 = page1.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, page2)
        retrievedEvents.page shouldBe 2
        retrievedEvents.content.size shouldBe 4
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[4]
        retrievedEvents.content shouldContain events[3]
        retrievedEvents.content shouldContain events[2]
        retrievedEvents.content shouldContain events[1]

        val page3 = page2.next()
        retrievedEvents = eventRepository.findByTimeInterval(from, to, page3)
        retrievedEvents.page shouldBe 3
        retrievedEvents.content.size shouldBe 1
        retrievedEvents.totalPages shouldBe 3
        retrievedEvents.totalElements shouldBe 9
        retrievedEvents.content shouldContain events[0]
    }

    "Should retrieve events by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(fromRole = roleFilter)

        val event1 = buildTestEvent().copy(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent().copy(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeIntervalJoinMessageDetail(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            role = roleFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents shouldContain event2
    }

    "Should retrieve events by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(service = serviceFilter)

        val event1 = buildTestEvent().copy(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent().copy(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeIntervalJoinMessageDetail(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            service = serviceFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents shouldContain event2
    }

    "Should retrieve events by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(action = actionFilter)

        val event1 = buildTestEvent().copy(requestId = messageDetails1.requestId)
        val event2 = buildTestEvent().copy(requestId = messageDetails2.requestId)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        eventRepository.insert(event1)
        eventRepository.insert(event2)

        val retrievedEvents = eventRepository.findByTimeIntervalJoinMessageDetail(
            Instant.parse("2025-04-01T12:00:00Z"),
            Instant.parse("2025-04-01T13:00:00Z"),
            action = actionFilter
        ).content

        retrievedEvents.size shouldBe 1
        retrievedEvents shouldContain event2
    }
})
