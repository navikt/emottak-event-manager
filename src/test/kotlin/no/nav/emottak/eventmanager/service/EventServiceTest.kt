package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.model.Event
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
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

        coEvery { eventRepository.findEventByTimeInterval(from, to) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        val eventsList = eventService.fetchEvents(from, to)
        eventsList.size shouldBe 1
        eventsList[0].hendelsedato shouldBe testEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
        eventsList[0].hendelsedeskr shouldBe testEvent.eventType.description
        eventsList[0].tillegsinfo shouldBe testEvent.eventData
        eventsList[0].requestId shouldBe testEvent.requestId.toString()

        coVerify { eventRepository.findEventByTimeInterval(from, to) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }

    "Should call EventRepository on fetching events related to a specific message" {
        val testEvent = buildTestEvent()

        coEvery { eventRepository.findEventsByRequestId(testEvent.requestId) } returns listOf(testEvent)

        val eventsList = eventService.fetchMessageLoggInfo(testEvent.requestId)

        eventsList.size shouldBe 1
        eventsList[0].hendelsesdato shouldBe testEvent.createdAt.atZone(ZoneId.of("Europe/Oslo")).toString()
        eventsList[0].hendelsesbeskrivelse shouldBe testEvent.eventType.description
        eventsList[0].hendelsesid shouldBe testEvent.eventType.value.toString()

        coVerify { eventRepository.findEventsByRequestId(testEvent.requestId) }
    }
})
