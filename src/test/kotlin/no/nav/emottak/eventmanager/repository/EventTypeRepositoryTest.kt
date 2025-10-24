package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum

class EventTypeRepositoryTest : RepositoryTestBase({

    "Should retrieve an event type by event type ID" {
        val retrievedEventType = eventTypeRepository.findEventTypeById(1)

        retrievedEventType shouldNotBe null
        // These values are from the migration script
        retrievedEventType?.eventTypeId shouldBe 1
        retrievedEventType?.description shouldBe "Melding mottatt via SMTP"
        retrievedEventType?.status shouldBe EventStatusEnum.INFORMATION
    }

    "Should retrieve a list of event types by a list of event type IDs" {
        val retrievedEventTypes = eventTypeRepository.findEventTypesByIds(listOf(1, 2, 3))

        retrievedEventTypes.size shouldBe 3
        // These values are from the migration script
        retrievedEventTypes shouldContainExactlyInAnyOrder listOf(
            EventType(1, "Melding mottatt via SMTP", EventStatusEnum.INFORMATION),
            EventType(2, "Feil ved mottak av melding via SMTP", EventStatusEnum.ERROR),
            EventType(3, "Melding sendt via SMTP", EventStatusEnum.PROCESSING_COMPLETED)
        )
    }
})
