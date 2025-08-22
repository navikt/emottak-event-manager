package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.eventmanager.repository.buildTestEvent
import no.nav.emottak.eventmanager.repository.buildTestTransportMessageDetail
import no.nav.emottak.utils.kafka.model.EventDataType
import java.time.Instant
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail as TransportEbmsMessageDetail
import no.nav.emottak.utils.kafka.model.EventType as EventTypeEnum

class EbmsMessageDetailServiceTest : StringSpec({

    val eventRepository = mockk<EventRepository>()
    val ebmsMessageDetailRepository = mockk<EbmsMessageDetailRepository>()
    val eventTypeRepository = mockk<EventTypeRepository>(relaxed = true)
    val ebmsMessageDetailService = EbmsMessageDetailService(eventRepository, ebmsMessageDetailRepository, eventTypeRepository)

    "Should call database repository on processing EBMS message details" {

        val testTransportMessageDetail = buildTestTransportMessageDetail()
        val testDetailsJson = Json.encodeToString(TransportEbmsMessageDetail.serializer(), testTransportMessageDetail)

        val testDetails = EbmsMessageDetail.fromTransportModel(testTransportMessageDetail)

        coEvery { ebmsMessageDetailRepository.insert(testDetails) } returns testDetails.requestId

        ebmsMessageDetailService.process(testDetailsJson.toByteArray())

        coVerify { ebmsMessageDetailRepository.insert(testDetails) }
    }

    "Should call database repository on fetching EBMS message details by time interval" {
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

        coEvery { ebmsMessageDetailRepository.findRelatedMottakIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.calculateMottakId())
        coEvery { eventRepository.findEventsByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        val messageInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        coVerify { ebmsMessageDetailRepository.findByTimeInterval(from, to) }
        coVerify { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }

        messageInfoList.size shouldBe 1
        messageInfoList[0].mottakidliste shouldBe testDetails.calculateMottakId()
        messageInfoList[0].cpaid shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by Request ID" {
        var testDetails = buildTestEbmsMessageDetail()
        testDetails = testDetails.copy(
            mottakId = testDetails.calculateMottakId()
        )

        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )

        coEvery { ebmsMessageDetailRepository.findByRequestId(testDetails.requestId) } returns testDetails
        coEvery { eventRepository.findEventsByRequestId(testDetails.requestId) } returns listOf(testEvent)
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        val mottakIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(testDetails.requestId.toString())

        coVerify { ebmsMessageDetailRepository.findByRequestId(testDetails.requestId) }
        coVerify { eventRepository.findEventsByRequestId(testDetails.requestId) }

        mottakIdInfoList.size shouldBe 1
        mottakIdInfoList[0].mottakid shouldBe testDetails.calculateMottakId()
        mottakIdInfoList[0].cpaid shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by Mottak ID" {
        var testDetails = buildTestEbmsMessageDetail()
        testDetails = testDetails.copy(
            mottakId = testDetails.calculateMottakId()
        )

        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )

        coEvery { ebmsMessageDetailRepository.findByMottakIdPattern(testDetails.calculateMottakId()) } returns testDetails
        coEvery { eventRepository.findEventsByRequestId(testDetails.requestId) } returns listOf(testEvent)
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        val mottakIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(testDetails.calculateMottakId())

        coVerify { ebmsMessageDetailRepository.findByMottakIdPattern(testDetails.calculateMottakId()) }
        coVerify { eventRepository.findEventsByRequestId(testDetails.requestId) }

        mottakIdInfoList.size shouldBe 1
        mottakIdInfoList[0].mottakid shouldBe testDetails.calculateMottakId()
        mottakIdInfoList[0].cpaid shouldBe testDetails.cpaId
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
        coEvery { ebmsMessageDetailRepository.findRelatedMottakIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.calculateMottakId())
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
        coEvery { ebmsMessageDetailRepository.findRelatedMottakIds(listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.calculateMottakId())
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
            ebmsMessageDetailRepository.findRelatedMottakIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns
            mapOf(
                testDetails1.requestId to "${testDetails1.calculateMottakId()},${testDetails2.calculateMottakId()}",
                testDetails2.requestId to "${testDetails1.calculateMottakId()},${testDetails2.calculateMottakId()}",
                testDetails3.requestId to testDetails3.calculateMottakId()
            )
        coEvery {
            eventRepository.findEventsByRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns listOf()

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.size shouldBe 3
        result[0].mottakidliste shouldBe "${testDetails1.calculateMottakId()},${testDetails2.calculateMottakId()}"
        result[1].mottakidliste shouldBe "${testDetails1.calculateMottakId()},${testDetails2.calculateMottakId()}"
        result[2].mottakidliste shouldBe testDetails3.calculateMottakId()
    }

    "isDuplicate should return true if message is a duplicate" {
        val testMessageDetails = buildTestEbmsMessageDetail()
        val testMessageDetailsDuplicate = testMessageDetails.copy(
            requestId = Uuid.random()
        )

        coEvery {
            ebmsMessageDetailRepository.findByMessageIdConversationIdAndCpaId(
                testMessageDetails.messageId,
                testMessageDetails.conversationId,
                testMessageDetails.cpaId
            )
        } returns listOf(testMessageDetailsDuplicate)

        val result = ebmsMessageDetailService.isDuplicate(
            testMessageDetails.messageId,
            testMessageDetails.conversationId,
            testMessageDetails.cpaId
        )

        result shouldBe true
    }

    "isDuplicate should return false if message is not a duplicate" {
        val testMessageDetails = buildTestEbmsMessageDetail()

        coEvery {
            ebmsMessageDetailRepository.findByMessageIdConversationIdAndCpaId(
                testMessageDetails.messageId,
                testMessageDetails.conversationId,
                testMessageDetails.cpaId
            )
        } returns listOf()

        val result = ebmsMessageDetailService.isDuplicate(
            testMessageDetails.messageId,
            testMessageDetails.conversationId,
            testMessageDetails.cpaId
        )

        result shouldBe false
    }
})
