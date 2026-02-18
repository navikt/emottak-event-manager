package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
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
        conversationStatus.latestStatus shouldBe EventStatusEnum.INFORMATION
    }

    "Should update conversation status" {
        val conversationId = Uuid.random().toString()
        val success = conversationStatusRepository.insert(conversationId)
        success shouldBe true

        val conversationStatus = conversationStatusRepository.get(conversationId)
        conversationStatus shouldNotBe null
        conversationStatus!!.conversationId shouldBe conversationId
        conversationStatus.latestStatus shouldBe EventStatusEnum.INFORMATION
        conversationStatus.createdAt shouldBe conversationStatus.statusAt

        val updated = conversationStatusRepository.update(conversationId, EventStatusEnum.PROCESSING_COMPLETED)
        updated shouldBe true

        val updatedConversationStatus = conversationStatusRepository.get(conversationId)
        updatedConversationStatus shouldNotBe null
        updatedConversationStatus!!.conversationId shouldBe conversationId
        updatedConversationStatus.latestStatus shouldBe EventStatusEnum.PROCESSING_COMPLETED
        updatedConversationStatus.createdAt shouldBeLessThan updatedConversationStatus.statusAt
    }

    "Update should return false if conversationId not found" {
        val conversationId = Uuid.random().toString()
        val updated = conversationStatusRepository.update(conversationId, EventStatusEnum.ERROR)
        updated shouldBe false
    }
})
