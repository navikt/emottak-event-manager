package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypesRepository
import no.nav.emottak.eventmanager.persistence.repository.EventsRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetails
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails
import no.nav.emottak.utils.kafka.model.EventDataType
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
        coEvery { eventTypesRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        coEvery { ebmsMessageDetailsRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventsRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        coVerify { ebmsMessageDetailsRepository.findByTimeInterval(from, to) }
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
                requestId = testDetails.requestId,
                eventData = Json.encodeToString(mapOf("sender" to "Test EPJ AS"))
            )
        )

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { ebmsMessageDetailsRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventsRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

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
                eventType = EventTypeEnum.REFERENCE_RETRIEVED,
                requestId = testDetails.requestId,
                eventData = Json.encodeToString(mapOf(EventDataType.REFERENCE.value to reference))
            )
        )

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { ebmsMessageDetailsRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventsRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        result.first().referanse shouldBe reference
    }

    "Should find related messages" {
        val commonReferenceId = "commonRef123"
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val testDetails1 = buildTestEbmsMessageDetails().copy(conversationId = commonReferenceId)
        val testDetails2 = buildTestEbmsMessageDetails().copy(conversationId = commonReferenceId)
        val testDetails3 = buildTestEbmsMessageDetails().copy(conversationId = "differentRef456")

        coEvery { ebmsMessageDetailsRepository.findByTimeInterval(from, to) } returns listOf(testDetails1, testDetails2, testDetails3)
        coEvery {
            ebmsMessageDetailsRepository.findRelatedRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns
            mapOf(
                testDetails1.requestId to "${testDetails1.requestId},${testDetails2.requestId}",
                testDetails2.requestId to "${testDetails1.requestId},${testDetails2.requestId}",
                testDetails3.requestId to "${testDetails3.requestId}"
            )
        coEvery {
            eventsRepository.findEventsByRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns listOf()

        val result = ebmsMessageDetailsService.fetchEbmsMessageDetails(from, to)

        result.size shouldBe 3
        result[0].mottakidliste shouldBe "${testDetails1.requestId},${testDetails2.requestId}"
        result[1].mottakidliste shouldBe "${testDetails1.requestId},${testDetails2.requestId}"
        result[2].mottakidliste shouldBe "${testDetails3.requestId}"
    }
})
