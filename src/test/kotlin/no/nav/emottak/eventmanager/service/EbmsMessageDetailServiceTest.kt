package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.model.DistinctRolesServicesActions
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.repository.DistinctRolesServicesActionsRepository
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
    val distinctRolesServicesActionsRepository = mockk<DistinctRolesServicesActionsRepository>()
    val ebmsMessageDetailService = EbmsMessageDetailService(
        eventRepository,
        ebmsMessageDetailRepository,
        eventTypeRepository,
        distinctRolesServicesActionsRepository
    )

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
        val testEvent = buildTestEvent(testDetails.requestId)
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, cpaIdPattern = testDetails.cpaId) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        val messageInfoPage = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to, cpaId = testDetails.cpaId)
        val messageInfoList = messageInfoPage.content

        coVerify { ebmsMessageDetailRepository.findByTimeInterval(from, to, cpaIdPattern = testDetails.cpaId) }
        coVerify { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }

        messageInfoList.size shouldBe 1
        messageInfoList[0].readableIdList shouldBe testDetails.generateReadableId()
        messageInfoList[0].cpaId shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by time interval and filtered by readableId" {
        val testDetails = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent(testDetails.requestId)
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )
        val from = Instant.now()
        val to = from.plusSeconds(60)
        val readableId = testDetails.generateReadableId()

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, readableIdPattern = readableId) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        val messageInfoPage = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to, readableId = readableId)
        val messageInfoList = messageInfoPage.content

        coVerify { ebmsMessageDetailRepository.findByTimeInterval(from, to, readableIdPattern = readableId) }
        coVerify { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }

        messageInfoList.size shouldBe 1
        messageInfoList[0].readableIdList shouldBe readableId
        messageInfoList[0].cpaId shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by time interval and filtered by messageId" {
        val testDetails = buildTestEbmsMessageDetail()
        val testEvent = buildTestEvent(testDetails.requestId)
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, messageIdPattern = testDetails.messageId) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns listOf(testEvent)

        val messageInfoPage = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to, messageId = testDetails.messageId)
        val messageInfoList = messageInfoPage.content

        coVerify { ebmsMessageDetailRepository.findByTimeInterval(from, to, messageIdPattern = testDetails.messageId) }
        coVerify { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) }

        messageInfoList.size shouldBe 1
        messageInfoList[0].readableIdList shouldBe testDetails.generateReadableId()
        messageInfoList[0].cpaId shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by Request ID" {
        var testDetails = buildTestEbmsMessageDetail()
        testDetails = testDetails.copy(
            readableId = testDetails.generateReadableId()
        )

        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )

        coEvery { ebmsMessageDetailRepository.findByRequestId(testDetails.requestId) } returns testDetails
        coEvery { eventRepository.findByRequestId(testDetails.requestId) } returns listOf(testEvent)
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        val readableIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(testDetails.requestId.toString())

        coVerify { ebmsMessageDetailRepository.findByRequestId(testDetails.requestId) }
        coVerify { eventRepository.findByRequestId(testDetails.requestId) }

        readableIdInfoList.size shouldBe 1
        readableIdInfoList[0].readableId shouldBe testDetails.generateReadableId()
        readableIdInfoList[0].cpaId shouldBe testDetails.cpaId
    }

    "Should call database repository on fetching EBMS message details by Readable ID" {
        var testDetails = buildTestEbmsMessageDetail()
        testDetails = testDetails.copy(
            readableId = testDetails.generateReadableId()
        )

        val testEvent = buildTestEvent()
        val testEventType = EventType(
            eventTypeId = 19,
            description = "Melding lagret i juridisk logg",
            status = EventStatusEnum.INFORMATION
        )

        coEvery { ebmsMessageDetailRepository.findByReadableIdPattern(testDetails.generateReadableId(), any()) } returns testDetails
        coEvery { eventRepository.findByRequestId(testDetails.requestId) } returns listOf(testEvent)
        coEvery { eventTypeRepository.findEventTypesByIds(listOf(testEvent.eventType.value)) } returns listOf(testEventType)

        val readableIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(testDetails.generateReadableId())

        coVerify { ebmsMessageDetailRepository.findByReadableIdPattern(testDetails.generateReadableId(), 1000) }
        coVerify { eventRepository.findByRequestId(testDetails.requestId) }

        readableIdInfoList.size shouldBe 1
        readableIdInfoList[0].readableId shouldBe testDetails.generateReadableId()
        readableIdInfoList[0].cpaId shouldBe testDetails.cpaId
    }

    "Should find sender name from related events" {
        val testDetails = buildTestEbmsMessageDetail().copy(senderName = null)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val relatedEvents = listOf(
            buildTestEvent(),
            buildTestEvent().copy(
                eventType = EventTypeEnum.MESSAGE_VALIDATED_AGAINST_CPA,
                requestId = testDetails.requestId,
                eventData = Json.encodeToString(mapOf(EventDataType.SENDER_NAME.value to "Test EPJ AS"))
            )
        )

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, any()) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.content.first().senderName shouldBe "Test EPJ AS"
    }

    "Bug-workaround: Should find sender name from related events when key is in uppercase" {
        val testDetails = buildTestEbmsMessageDetail().copy(senderName = null)
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val relatedEvents = listOf(
            buildTestEvent(),
            buildTestEvent().copy(
                eventType = EventTypeEnum.MESSAGE_VALIDATED_AGAINST_CPA,
                requestId = testDetails.requestId,
                eventData = Json.encodeToString(mapOf(EventDataType.SENDER_NAME to "Test STORE BOKSTAVER"))
            )
        )

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, any()) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.content.first().senderName shouldBe "Test STORE BOKSTAVER"
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
                eventData = Json.encodeToString(mapOf(EventDataType.REFERENCE_PARAMETER.value to reference))
            )
        )

        val list = listOf(testDetails)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, any()) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery { ebmsMessageDetailRepository.findRelatedReadableIds(listOf(testDetails.conversationId), listOf(testDetails.requestId)) } returns
            mapOf(testDetails.requestId to testDetails.generateReadableId())
        coEvery { eventRepository.findByRequestIds(listOf(testDetails.requestId)) } returns relatedEvents

        val result = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)

        result.content.first().referenceParameter shouldBe reference
    }

    "Should find related messages" {
        val commonReferenceId = "commonRef123"
        val from = Instant.now()
        val to = from.plusSeconds(60)

        val testDetails1 = buildTestEbmsMessageDetail().copy(conversationId = commonReferenceId)
        val testDetails2 = buildTestEbmsMessageDetail().copy(conversationId = commonReferenceId)
        val testDetails3 = buildTestEbmsMessageDetail().copy(conversationId = "differentRef456")

        val list = listOf(testDetails1, testDetails2, testDetails3)
        val pageable = Pageable(1, list.size)
        coEvery { ebmsMessageDetailRepository.findByTimeInterval(from, to, any()) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            "ASC",
            list.size.toLong(),
            list
        )
        coEvery {
            ebmsMessageDetailRepository.findRelatedReadableIds(
                listOf(testDetails1.conversationId, testDetails2.conversationId, testDetails3.conversationId),
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns
            mapOf(
                testDetails1.requestId to "${testDetails1.generateReadableId()},${testDetails2.generateReadableId()}",
                testDetails2.requestId to "${testDetails1.generateReadableId()},${testDetails2.generateReadableId()}",
                testDetails3.requestId to testDetails3.generateReadableId()
            )
        coEvery {
            eventRepository.findByRequestIds(
                listOf(testDetails1.requestId, testDetails2.requestId, testDetails3.requestId)
            )
        } returns listOf()

        val resultPage = ebmsMessageDetailService.fetchEbmsMessageDetails(from, to)
        val result = resultPage.content

        result.size shouldBe 3
        result[0].readableIdList shouldBe "${testDetails1.generateReadableId()},${testDetails2.generateReadableId()}"
        result[1].readableIdList shouldBe "${testDetails1.generateReadableId()},${testDetails2.generateReadableId()}"
        result[2].readableIdList shouldBe testDetails3.generateReadableId()
    }

    "Should retrieve filter-values" {
        val filters = DistinctRolesServicesActions(
            roles = listOf("roleA", "roleB"),
            services = listOf("servicesA", "servicesB"),
            actions = listOf("actionA", "actionB"),
            refreshedAt = Instant.now()
        )
        coEvery { distinctRolesServicesActionsRepository.getDistinctRolesServicesActions() } returns filters
        val reply = ebmsMessageDetailService.getDistinctRolesServicesActions()
        coVerify(exactly = 1) { distinctRolesServicesActionsRepository.getDistinctRolesServicesActions() }
        reply shouldBe filters
    }

    "Should call refreshDistinctRolesServicesActions() if getDistinctRolesServicesActions() returns null" {
        val filters = DistinctRolesServicesActions(
            roles = listOf("roleA", "roleB"),
            services = listOf("servicesA", "servicesB"),
            actions = listOf("actionA", "actionB"),
            refreshedAt = Instant.now()
        )
        coEvery { distinctRolesServicesActionsRepository.getDistinctRolesServicesActions() } returns null
        coEvery { distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions() } returns filters
        ebmsMessageDetailService.getDistinctRolesServicesActions()
        coVerify(exactly = 1) { distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions() }
    }

    "isDuplicate should return true when message is a duplicate" {
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

    "isDuplicate should return false when message is not a duplicate" {
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
