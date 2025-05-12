package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.buildTestEvent
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import java.time.Instant

class EventServiceTest : StringSpec({

    val eventsRepository = mockk<EventsRepository>()
    val ebmsMessageDetailsRepository = mockk<EbmsMessageDetailsRepository>()
    val eventService = EventService(eventsRepository, ebmsMessageDetailsRepository)

    "Should call database repository on processing en event" {

        val testEvent = buildTestEvent()

        coEvery { eventsRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testEvent.toByteArray())

        coVerify { eventsRepository.insert(testEvent) }
    }

    "Should call EventsRepository and EbmsMessageDetailsRepository on fetching events" {
        val testEvent = buildTestEvent()
        val testRequestIds = listOf(testEvent.requestId)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { eventsRepository.findEventByTimeInterval(from, to) } returns listOf(testEvent)
        coEvery { ebmsMessageDetailsRepository.findByRequestIds(testRequestIds) } returns mapOf()

        eventService.fetchEvents(from, to)

        coVerify { eventsRepository.findEventByTimeInterval(from, to) }
        coVerify { ebmsMessageDetailsRepository.findByRequestIds(testRequestIds) }
    }
})
