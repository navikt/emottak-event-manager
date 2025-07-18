package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail
import no.nav.emottak.utils.kafka.model.EventDataType
import java.time.Instant
import no.nav.emottak.utils.kafka.model.EventType as EventTypeEnum

class EbmsMessageDetailServiceTest : StringSpec({

    val eventRepository = mockk<EventRepository>()
    val ebmsMessageDetailRepository = mockk<EbmsMessageDetailRepository>()
    val eventTypeRepository = mockk<EventTypeRepository>(relaxed = true)
    val ebmsMessageDetailService = EbmsMessageDetailService(eventRepository, ebmsMessageDetailRepository, eventTypeRepository)

    "Should call database repository on processing EBMS message details" {

        val testDetails = buildTestEbmsMessageDetail()
        val testDetailsJson = Json.encodeToString(EbmsMessageDetail.serializer(), testDetails)

        coEvery { ebmsMessageDetailRepository.insert(testDetails) } returns testDetails.requestId

        ebmsMessageDetailService.process(testDetailsJson.toByteArray())

        coVerify { ebmsMessageDetailRepository.insert(testDetails) }
    }

    "Should call database repository on fetching EBMS message details" {
        val testDetails = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )
        val from = Instant.now()
        val to = from.plusSeconds(60)

        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        coEvery { ebmsMessageDetailRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        coVerify { ebmsMessageDetailRepository.findByTimeInterval(from, to) }
        coVerify { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }
    }

    "Should find sender from related events" {
        val testDetails = buildTestEbmsMessageDetail().copy(sender = null)
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

        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { ebmsMessageDetailRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.first().avsender shouldBe "Test EPJ AS"
    }

    "Should find reference parameter from related events" {
        val reference = "abcd1234"
        val testDetails = buildTestEbmsMessageDetail().copy(refParam = null)
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

        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to) } returns listOf(testDetails)
        coEvery { ebmsMessageDetailRepository.findRelatedRequestIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.requestId.toString())
        coEvery { eventRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.first().referanse shouldBe reference
    }

    "Should find related messages" {
        val commonReferenceId = "commonRef123"
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val testDetails1 = buildTestEbmsMessageDetail().copy(conversationId = commonReferenceId)
        val testDetails2 = buildTestEbmsMessageDetail().copy(conversationId = commonReferenceId)
        val testDetails3 = buildTestEbmsMessageDetail().copy(conversationId = "differentRef456")

        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to) } returns listOf(testDetails1, testDetails2, testDetails3)
        coEvery {
            ebmsMessageDetailRepository.findRelatedRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns
            mapOf(
                testDetails1.requestId to "${testDetails1.requestId},${testDetails2.requestId}",
                testDetails2.requestId to "${testDetails1.requestId},${testDetails2.requestId}",
                testDetails3.requestId to "${testDetails3.requestId}"
            )
        coEvery {
            eventRepository.findEventsByRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns listOf()

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.size shouldBe 3
        result[0].mottakidliste shouldBe "${testDetails1.requestId},${testDetails2.requestId}"
        result[1].mottakidliste shouldBe "${testDetails1.requestId},${testDetails2.requestId}"
        result[2].mottakidliste shouldBe "${testDetails3.requestId}"
    }
})
