package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventType
import java.util.UUID
import kotlin.uuid.Uuid
import kotlin.uuid.toKotlinUuid

class EventServiceTest : StringSpec({

    val eventsRepository = mockk<EventsRepository>()
    val eventService = EventService(eventsRepository)

    "Should call database repository on processing en event" {

        val testEvent = Event(
            eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
            requestId = UUID.randomUUID().toKotlinUuid(),
            contentId = "test-content-id",
            messageId = "test-message-id",
            eventData = "{\"juridisk_logg_id\":\"1_msg_20250401145445386\"}"
        )

        coEvery { eventsRepository.insert(testEvent) } returns testEvent.requestId

        eventService.process(Uuid.random().toString(), testEvent.toByteArray())

        coVerify { eventsRepository.insert(testEvent) }
    }
})
