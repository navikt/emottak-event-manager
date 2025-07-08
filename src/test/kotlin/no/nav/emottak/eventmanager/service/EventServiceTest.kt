package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.repository.buildTestEvent
import java.time.Instant

class EventServiceTest : StringSpec({

    val eventRepository = mockk<EventRepository>()
    val ebmsMessageDetailRepository = mockk<EbmsMessageDetailRepository>()
    val eventService = EventService(eventRepository, ebmsMessageDetailRepository)

    "Should call database repository on processing en event" {

        val testEvent = buildTestEvent()

        coEvery { eventRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testEvent.toByteArray())

        coVerify { eventRepository.insert(testEvent) }
    }

    "Should call EventRepository and EbmsMessageDetailRepository on fetching events" {
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { eventRepository.findEventByTimeInterval(from, to) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) } returns mapOf()

        eventService.fetchEvents(from, to)

        coVerify { eventRepository.findEventByTimeInterval(from, to) }
        coVerify { ebmsMessageDetailRepository.findByRequestIds(testRequestIds) }
    }
})
