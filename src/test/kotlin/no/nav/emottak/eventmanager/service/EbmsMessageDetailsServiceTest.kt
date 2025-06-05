package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.buildTestEbmsMessageDetails
import no.nav.emottak.eventmanager.buildTestEvent
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypesRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import java.time.Instant
import no.nav.emottak.utils.kafka.model.EventType as EventTypeEnum

class EbmsMessageDetailsServiceTest : StringSpec({

    val eventsRepository = mockk<EventsRepository>()
    val ebmsMessageDetailsRepository = mockk<EbmsMessageDetailsRepository>()
    val eventTypesRepository = mockk<EventTypesRepository>(relaxed = true)
    val ebmsMessageDetailsService = EbmsMessageDetailsService(eventsRepository, ebmsMessageDetailsRepository, eventTypesRepository)

    "Should call database repository on processing EBMS message details" {

        val testDetails = buildTestEbmsMessageDetails()
        val testDetailsJson = Json.encodeToString(EbmsMessageDetails.serializer(), testDetails)

        coEvery { ebmsMessageDetailsRepository.insert(testDetails) } returns testDetails.requestId

        ebmsMessageDetailsService.process(testDetailsJson.toByteArray())

        coVerify { ebmsMessageDetailsRepository.insert(testDetails) }
    }

    "Should call database repository on fetching EBMS message details" {
        val testDetails = buildTestEbmsMessageDetails()
        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventsRepository.findEventByRequestId(testDetails.requestId) } returns listOf(testEvent)
        coEvery { eventTypesRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        coVerify { ebmsMessageDetailsRepository.findByTimeInterval(from, to) }
        coVerify { eventsRepository.findEventByRequestId(testDetails.requestId) }
        coVerify { eventTypesRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }
    }

    "Should find sender from related events" {
        val testDetails = buildTestEbmsMessageDetails().copy(sender = null)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val relatedEvents = listOf(
            buildTestEvent(),
            buildTestEvent().copy(
                eventType = EventTypeEnum.MESSAGE_VALIDATED_AGAINST_CPA,
                eventData = Json.encodeToString(mapOf("sender" to "Test EPJ AS"))
            )
        )

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventsRepository.findEventByRequestId(testDetails.requestId) } returns relatedEvents

        val result = ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        result.first().avsender shouldBe "Test EPJ AS"
    }
})
