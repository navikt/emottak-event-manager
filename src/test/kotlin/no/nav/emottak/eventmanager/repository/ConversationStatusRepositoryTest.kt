package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.emottak.eventmanager.model.ASCENDING
import no.nav.emottak.eventmanager.model.DESCENDING
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import kotlin.uuid.Uuid

class ConversationStatusRepositoryTest : RepositoryTestBase({

    "Should insert conversation" {
        val conversationId = Uuid.random().toString()
        val success = conversationStatusRepository.insert(conversationId)
        success shouldBe true
    }

    "Should NOT insert identical conversation" {
        val conversationId = Uuid.random().toString()
        val success1 = conversationStatusRepository.insert(conversationId)
        success1 shouldBe true
        // Try to insert the same conversationId:
        val success2 = conversationStatusRepository.insert(conversationId)
        success2 shouldBe false
    }

    "Should retreive conversation status" {
        val conversationId = Uuid.random().toString()
        val success = conversationStatusRepository.insert(conversationId)
        success shouldBe true

        val conversationStatus = conversationStatusRepository.get(conversationId)
        conversationStatus shouldNotBe null
        conversationStatus!!.conversationId shouldBe conversationId
        conversationStatus.latestStatus shouldBe INFORMATION
    }

    "Should retreive null when conversation status not found" {
        val conversationId = Uuid.random().toString()
        val conversationStatus = conversationStatusRepository.get(conversationId)
        conversationStatus shouldBe null
    }

    "Should update conversation status" {
        val conversationId = Uuid.random().toString()
        val success = conversationStatusRepository.insert(conversationId)
        success shouldBe true

        val conversationStatus = conversationStatusRepository.get(conversationId)
        conversationStatus shouldNotBe null
        conversationStatus!!.conversationId shouldBe conversationId
        conversationStatus.latestStatus shouldBe INFORMATION
        conversationStatus.createdAt shouldBe conversationStatus.statusAt

        val updated = conversationStatusRepository.update(conversationId, PROCESSING_COMPLETED)
        updated shouldBe true

        val updatedConversationStatus = conversationStatusRepository.get(conversationId)
        updatedConversationStatus shouldNotBe null
        updatedConversationStatus!!.conversationId shouldBe conversationId
        updatedConversationStatus.latestStatus shouldBe PROCESSING_COMPLETED
        updatedConversationStatus.createdAt shouldBeLessThan updatedConversationStatus.statusAt
    }

    "Update should return false if conversationId not found" {
        val conversationId = Uuid.random().toString()
        val updated = conversationStatusRepository.update(conversationId, ERROR)
        updated shouldBe false
    }

    "Should find conversations and order it by createdAt descending" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (c1md1, _, c2md1, _, c3md1) = messageDetails

        val pagable = conversationStatusRepository.findByFilters()

        pagable.totalElements shouldBe 3
        pagable.size shouldBe 3

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c3md1.conversationId
        conversations[1].conversationId shouldBe c2md1.conversationId
        conversations[2].conversationId shouldBe c1md1.conversationId
        conversations[0].createdAt shouldBeGreaterThan conversations[1].createdAt
        conversations[1].createdAt shouldBeGreaterThan conversations[2].createdAt
    }

    "Should find conversations and order it by createdAt ascending" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(
            ebmsMessageDetailRepository,
            eventRepository,
            conversationStatusRepository
        )
        val (c1md1, _, c2md1, _, c3md1) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(
            pageable = Pageable(
                pageNumber = 1,
                pageSize = 10,
                sort = ASCENDING
            )
        )

        pagable.totalElements shouldBe 3
        pagable.size shouldBe 10

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c1md1.conversationId
        conversations[1].conversationId shouldBe c2md1.conversationId
        conversations[2].conversationId shouldBe c3md1.conversationId
        conversations[0].createdAt shouldBeLessThan conversations[1].createdAt
        conversations[1].createdAt shouldBeLessThan conversations[2].createdAt
    }

    "Should find conversations and return expected fields" {
        val (messageDetails, events) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (c1md1, c1md2, c2md1, c1md3, c3md1) = messageDetails
        val (_, _, _, c1md3EventsList, c3md1EventsList) = events

        val readableIdC1md1 = ebmsMessageDetailRepository.findByRequestId(c1md1.requestId)!!.readableId
        val readableIdC1md2 = ebmsMessageDetailRepository.findByRequestId(c1md2.requestId)!!.readableId
        val readableIdC2md1 = ebmsMessageDetailRepository.findByRequestId(c2md1.requestId)!!.readableId
        val readableIdC1md3 = ebmsMessageDetailRepository.findByRequestId(c1md3.requestId)!!.readableId
        val readableIdC3md1 = ebmsMessageDetailRepository.findByRequestId(c3md1.requestId)!!.readableId

        val pagable = conversationStatusRepository.findByFilters()

        pagable.totalElements shouldBe 3
        pagable.size shouldBe 3

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c3md1.conversationId
        conversations[1].conversationId shouldBe c2md1.conversationId
        conversations[2].conversationId shouldBe c1md1.conversationId
        conversations[0].createdAt shouldBe c3md1.savedAt
        conversations[1].createdAt shouldBe c2md1.savedAt
        conversations[2].createdAt shouldBe c1md1.savedAt
        conversations[0].latestStatus shouldBe PROCESSING_COMPLETED
        conversations[1].latestStatus shouldBe INFORMATION
        conversations[2].latestStatus shouldBe ERROR
        conversations[0].statusAt shouldBe c3md1EventsList.last().createdAt
        conversations[1].statusAt shouldBe c2md1.savedAt // Alle events er 'Informasjon', så ingen update på status utført
        conversations[2].statusAt shouldBe c1md3EventsList.last().createdAt
        conversations[0].cpaId shouldBe c3md1.cpaId
        conversations[1].cpaId shouldBe c2md1.cpaId
        conversations[2].cpaId shouldBe c1md1.cpaId
        conversations[0].service shouldBe c3md1.service
        conversations[1].service shouldBe c2md1.service
        conversations[2].service shouldBe c1md1.service
        conversations[0].readableIdList shouldBe readableIdC3md1
        conversations[1].readableIdList shouldBe readableIdC2md1
        conversations[2].readableIdList shouldBe listOf(readableIdC1md1, readableIdC1md2, readableIdC1md3).joinToString(",")
    }

    "Should find conversations and filter on CPA-id" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, _, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(cpaIdPattern = c2md1.cpaId)

        pagable.totalElements shouldBe 1
        pagable.size shouldBe 1

        val conversations = pagable.content
        conversations[0].conversationId shouldBe c2md1.conversationId
        conversations[0].cpaId shouldBe c2md1.cpaId
        conversations[0].createdAt shouldBe c2md1.savedAt
        conversations[0].statusAt shouldBe c2md1.savedAt // Alle events er 'Informasjon', ingen update utført
    }

    "Should find conversations and filter on CPA-id pattern" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, _, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(cpaIdPattern = "another")

        pagable.totalElements shouldBe 1
        pagable.size shouldBe 1

        val conversations = pagable.content
        conversations[0].conversationId shouldBe c2md1.conversationId
        conversations[0].cpaId shouldBe c2md1.cpaId
    }

    "Should find conversations and filter on service" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (c1md1, _, c2md1, _, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(service = c1md1.service)

        pagable.totalElements shouldBe 2
        pagable.size shouldBe 2

        val conversations = pagable.content
        conversations[0].conversationId shouldBe c2md1.conversationId
        conversations[1].conversationId shouldBe c1md1.conversationId
        conversations[0].service shouldBe c2md1.service
        conversations[1].service shouldBe c1md1.service
    }

    "Should find conversations and filter on from-timestamp" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, _, c3md1) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(from = c2md1.savedAt)

        pagable.totalElements shouldBe 2
        pagable.size shouldBe 2

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c3md1.conversationId
        conversations[1].conversationId shouldBe c2md1.conversationId
    }

    "Should find conversations and filter on to-timestamp" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (c1md1, _, c2md1, _, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(to = c2md1.savedAt)

        pagable.totalElements shouldBe 2
        pagable.size shouldBe 2

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c2md1.conversationId
        conversations[1].conversationId shouldBe c1md1.conversationId
    }

    "Should find conversations and filter on from- and to-timestamp" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, _, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(from = c2md1.savedAt, to = c2md1.savedAt)

        pagable.totalElements shouldBe 1
        pagable.size shouldBe 1

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c2md1.conversationId
    }

    "Should find conversations and filter on status (unfinished)" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, c1md3, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(statuses = listOf(ERROR, INFORMATION))

        pagable.totalElements shouldBe 2
        pagable.size shouldBe 2

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c2md1.conversationId
        conversations[1].conversationId shouldBe c1md3.conversationId
    }

    "Should find conversations and filter on status (failed)" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, _, c1md3, _) = messageDetails

        val pagable = conversationStatusRepository.findByFilters(statuses = listOf(ERROR))

        pagable.totalElements shouldBe 1
        pagable.size shouldBe 1

        val conversations = pagable.content
        println(conversations)
        conversations[0].conversationId shouldBe c1md3.conversationId
    }

    "Should find conversations and limit by pageable" {
        val (messageDetails, _) = buildAndInsertTestEbmsMessageDetailConversation(ebmsMessageDetailRepository, eventRepository, conversationStatusRepository)
        val (_, _, c2md1, c1md3, c3md1) = messageDetails

        val page1 = conversationStatusRepository.findByFilters(pageable = Pageable(pageNumber = 1, pageSize = 2, sort = DESCENDING))
        page1.totalElements shouldBe 3
        page1.size shouldBe 2
        page1.content.size shouldBe 2
        page1.content[0].conversationId shouldBe c3md1.conversationId
        page1.content[1].conversationId shouldBe c2md1.conversationId

        val page2 = conversationStatusRepository.findByFilters(pageable = Pageable(pageNumber = 2, pageSize = 2, sort = DESCENDING))
        page2.totalElements shouldBe 3
        page2.size shouldBe 2
        page2.content.size shouldBe 1
        page2.content[0].conversationId shouldBe c1md3.conversationId
    }
})
