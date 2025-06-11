package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetails
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import no.nav.emottak.utils.kafka.model.EventDataType
import no.nav.emottak.utils.kafka.model.EventType
import java.time.Instant

class EbmsMessageDetailsServiceTest : StringSpec({

    val eventsRepository = mockk<EventsRepository>()
    val ebmsMessageDetailsRepository = mockk<EbmsMessageDetailsRepository>()
    val ebmsMessageDetailsService = EbmsMessageDetailsService(eventsRepository, ebmsMessageDetailsRepository)

    "Should call database repository on processing EBMS message details" {

        val testDetails = buildTestEbmsMessageDetails()
        val testDetailsJson = Json.encodeToString(EbmsMessageDetails.serializer(), testDetails)

        coEvery { ebmsMessageDetailsRepository.insert(testDetails) } returns testDetails.requestId

        ebmsMessageDetailsService.process(testDetailsJson.toByteArray())

        coVerify { ebmsMessageDetailsRepository.insert(testDetails) }
    }

    "Should call database repository on fetching EBMS message details" {
        val testDetails = buildTestEbmsMessageDetails()
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventsRepository.findEventByRequestId(testDetails.requestId) } returns listOf()

        ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        coVerify { ebmsMessageDetailsRepository.findByTimeInterval(from, to) }
    }

    "Should find sender from related events" {
        val testDetails = buildTestEbmsMessageDetails().copy(sender = null)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val relatedEvents = listOf(
            buildTestEvent(),
            buildTestEvent().copy(
                eventType = EventType.MESSAGE_VALIDATED_AGAINST_CPA,
                eventData = Json.encodeToString(mapOf("sender" to "Test EPJ AS"))
            )
        )

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventsRepository.findEventByRequestId(testDetails.requestId) } returns relatedEvents

        val result = ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        result.first().avsender shouldBe "Test EPJ AS"
    }

    "Should find reference parameter from related events" {
        val reference = "abcd1234"
        val testDetails = buildTestEbmsMessageDetails().copy(refParam = null)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val relatedEvents = listOf(
            buildTestEvent(),
            buildTestEvent().copy(
                eventType = EventType.REFERENCE_RETRIEVED,
                eventData = Json.encodeToString(mapOf(EventDataType.REFERENCE.value to reference))
            )
        )

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventsRepository.findEventByRequestId(testDetails.requestId) } returns relatedEvents

        val result = ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        result.first().referanse shouldBe reference
    }
})
