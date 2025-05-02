package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.buildTestEvent
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository

class EventServiceTest : StringSpec({

    val eventsRepository = mockk<EventsRepository>()
    val eventService = EventService(eventsRepository)

    "Should call database repository on processing en event" {

        val testEvent = buildTestEvent()

        coEvery { eventsRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(testEvent.toByteArray())

        coVerify { eventsRepository.insert(testEvent) }
    }
})
