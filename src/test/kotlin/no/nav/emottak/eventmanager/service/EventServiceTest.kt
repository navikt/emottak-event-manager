package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.Constants
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.eventmanager.repository.buildTestTransportEvent
import java.time.Instant
import java.time.ZoneId

class EventServiceTest : StringSpec({

    val eventRepository = mockk<EventRepository>()
    val ebmsMessageDetailRepository = mockk<EbmsMessageDetailRepository>()
    val eventService = EventService(eventRepository, ebmsMessageDetailRepository)

    "Should call database repository on processing en event" {
        val testTransportEvent = buildTestTransportEvent()
        val testEvent = Event.fromTransportModel(testTransportEvent)

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testTransportEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
    }

    "Should call EventRepository and EbmsMessageDetailRepository on fetching events" {
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { eventRepository.findByTimeInterval(from, to, any()) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsList = eventService.fetchEvents(from, to)
        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString()
        eventsList[0].description shouldBe testEvent.eventType.description
        eventsList[0].eventData shouldBe testEvent.eventData

        coVerify { eventRepository.findByTimeInterval(from, to, 1000) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call EventRepository on fetching events related to a specific message by Request ID" {
        val testEvent = buildTestEvent()

        coEvery { eventRepository.findByRequestId(testEvent.requestId) } returns listOf(testEvent)

        val eventsList = eventService.fetchMessageLogInfo(testEvent.requestId.toString())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString()
        eventsList[0].eventDescription shouldBe testEvent.eventType.description
        eventsList[0].eventId shouldBe testEvent.eventType.value.toString()

        coVerify { eventRepository.findByRequestId(testEvent.requestId) }
    }

    "Should call database on fetching events related to a specific message by Readable ID" {
        val testMessageDetail = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent().copy(requestId = testMessageDetail.requestId)

        coEvery { eventRepository.findByRequestId(testEvent.requestId) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailRepository.findByReadableId(testMessageDetail.generateReadableId()) } returns testMessageDetail

        val eventsList = eventService.fetchMessageLogInfo(testMessageDetail.generateReadableId())

        eventsList.size shouldBe 1
        eventsList[0].eventDate shouldBe testEvent.createdAt.atZone(ZoneId.of(Constants.ZONE_ID_OSLO)).toString()
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
