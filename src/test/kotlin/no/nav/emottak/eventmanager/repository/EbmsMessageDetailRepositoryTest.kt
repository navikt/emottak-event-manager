package no.nav.emottak.eventmanager.repository

import io.kotest.data.forAll
import io.kotest.data.row
import io.kotest.matchers.maps.shouldContainKey
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Pageable
import java.time.Instant
import java.time.temporal.ChronoUnit

class EbmsMessageDetailRepositoryTest : RepositoryTestBase({

    "Should update message details by requestId" {
        val messageDetails = buildTestEbmsMessageDetail()
        ebmsMessageDetailRepository.insert(messageDetails)

        val updatedMessageDetail = messageDetails.copy(
            cpaId = "updated-cpa-id",
            conversationId = "updated-conversation-id",
            messageId = "updated-message-id",
            fromPartyId = "updated-from-party-id",
            toPartyId = "updated-to-party-id",
            service = "updated-service",
            action = "updated-action",
            refParam = "updated-ref-param",
            sentAt = Instant.parse("2025-05-26T14:54:45.386Z"),
            savedAt = Instant.parse("2025-05-26T15:54:50.386Z")
        )
        ebmsMessageDetailRepository.update(updatedMessageDetail)

        val retrievedDetails = ebmsMessageDetailRepository.findByRequestId(messageDetails.requestId)

        retrievedDetails?.requestId shouldBe messageDetails.requestId

        retrievedDetails?.cpaId shouldBe updatedMessageDetail.cpaId
        retrievedDetails?.conversationId shouldBe updatedMessageDetail.conversationId
        retrievedDetails?.messageId shouldBe updatedMessageDetail.messageId
        retrievedDetails?.requestId shouldBe updatedMessageDetail.requestId
        retrievedDetails?.fromPartyId shouldBe updatedMessageDetail.fromPartyId
        retrievedDetails?.toPartyId shouldBe updatedMessageDetail.toPartyId
        retrievedDetails?.service shouldBe updatedMessageDetail.service
        retrievedDetails?.action shouldBe updatedMessageDetail.action
        retrievedDetails?.savedAt shouldBe updatedMessageDetail.savedAt.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.sentAt shouldBe updatedMessageDetail.sentAt?.truncatedTo(ChronoUnit.MICROS)
        retrievedDetails?.refParam shouldBe updatedMessageDetail.refParam
    }

    "Should retrieve message details by Readable ID" {
        val messageDetails = buildTestEbmsMessageDetail()

        ebmsMessageDetailRepository.insert(messageDetails)
        val retrievedDetails = ebmsMessageDetailRepository.findByReadableId(messageDetails.generateReadableId())

        retrievedDetails?.requestId shouldBe messageDetails.requestId
    }

    "Should retrieve message details by Readable ID pattern" {
        val messageDetails = buildTestEbmsMessageDetail()

        ebmsMessageDetailRepository.insert(messageDetails)

        forAll(
            row(messageDetails.generateReadableId().substring(0, 6)),
            row(messageDetails.generateReadableId().substring(0, 6).lowercase()),
            row(messageDetails.generateReadableId().substring(0, 6).uppercase()),
            row(messageDetails.generateReadableId().takeLast(6)),
            row(messageDetails.generateReadableId().substring(6, 12))
        ) { readableIdPattern ->
            val retrievedDetails = ebmsMessageDetailRepository.findByReadableIdPattern(readableIdPattern)

            retrievedDetails?.requestId shouldBe messageDetails.requestId
        }
    }

    "Should retrieve records by time interval" {
        val (md1, md2, md3, md4) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z")
        ).content
        retrievedDetails.size shouldBe 4
        retrievedDetails[0].requestId shouldBe md1.requestId
        retrievedDetails[1].requestId shouldBe md2.requestId
        retrievedDetails[2].requestId shouldBe md3.requestId
        retrievedDetails[3].requestId shouldBe md4.requestId
    }

    "Should retrieve records by time interval and filtered by readableId" {
        val (messageDetailsInInterval1, _, _, _) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableIdPattern = messageDetailsInInterval1.generateReadableId()
        ).content
        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetailsInInterval1.requestId
    }

    "Should retrieve records by time interval and filtered by part of readableId string-value" {
        val (_, _, md3, md4) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableIdPattern = "OUT."
        ).content
        retrievedDetails.size shouldBe 2
        retrievedDetails[0].requestId shouldBe md3.requestId
        retrievedDetails[1].requestId shouldBe md4.requestId
    }

    "Should retrieve records by time interval and filtered by cpaId" {
        val (_, messageDetailsInInterval2, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            cpaIdPattern = "another-cpa-id"
        ).content
        retrievedDetails.size shouldBe 2
        retrievedDetails[0].requestId shouldBe messageDetailsInInterval2.requestId
        retrievedDetails[1].requestId shouldBe messageDetailsOutOfInterval2.requestId
    }

    "Should retrieve records by time interval and filtered by part of cpaId string-value" {
        val (_, md2, _, md4) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            cpaIdPattern = "another"
        ).content
        retrievedDetails.size shouldBe 2
        retrievedDetails[0].requestId shouldBe md2.requestId
        retrievedDetails[1].requestId shouldBe md4.requestId
    }

    "Should retrieve records by time interval and filtered by messageId" {
        val (_, md2, _, _) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            messageIdPattern = "another-message-id1"
        ).content
        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe md2.requestId
    }

    "Should retrieve records by time interval and filtered by part of messageId string-value" {
        val (_, md2, _, md4) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            messageIdPattern = "another-message"
        ).content
        retrievedDetails.size shouldBe 2
        retrievedDetails[0].requestId shouldBe md2.requestId
        retrievedDetails[1].requestId shouldBe md4.requestId
    }

    "Should retrieve records by time interval and filtered by readableId, cpaId and messageId" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(repository)
        val retrievedDetails = repository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableIdPattern = messageDetailsOutOfInterval2.generateReadableId(),
            cpaIdPattern = "another-cpa-id",
            messageIdPattern = "another-message-id"
        ).content
        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetailsOutOfInterval2.requestId
    }

    "Should retrieve records by time interval, page by page" {
        val details: MutableList<EbmsMessageDetail> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val detail = buildTestEbmsMessageDetail().copy(messageId = id, savedAt = Instant.parse(ts))
            ebmsMessageDetailRepository.insert(detail)
            details.add(detail)
        }

        val page1 = Pageable(1, 4)
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page1)
        retrievedDetails.page shouldBe 1
        retrievedDetails.content.size shouldBe 4
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[0].messageId
        retrievedDetails.content[1].messageId shouldBe details[1].messageId
        retrievedDetails.content[2].messageId shouldBe details[2].messageId
        retrievedDetails.content[3].messageId shouldBe details[3].messageId

        val page2 = page1.next()
        retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page2)
        retrievedDetails.page shouldBe 2
        retrievedDetails.content.size shouldBe 4
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[4].messageId
        retrievedDetails.content[1].messageId shouldBe details[5].messageId
        retrievedDetails.content[2].messageId shouldBe details[6].messageId
        retrievedDetails.content[3].messageId shouldBe details[7].messageId

        val page3 = page2.next()
        retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page3)
        retrievedDetails.page shouldBe 3
        retrievedDetails.content.size shouldBe 1
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[8].messageId
    }

    "Should retrieve records by time interval, page by page, DESCENDING" {
        val details: MutableList<EbmsMessageDetail> = ArrayList()
        for (i in 1..9) {
            val id = "no$i"
            val ts = "2025-04-01T14:0$i:00.000Z"
            val detail = buildTestEbmsMessageDetail().copy(messageId = id, savedAt = Instant.parse(ts))
            ebmsMessageDetailRepository.insert(detail)
            details.add(detail)
        }

        val page1 = Pageable(1, 4, "DESC")
        val from = Instant.parse("2025-04-01T14:00:00Z")
        val to = Instant.parse("2025-04-01T15:00:00Z")
        var retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page1)
        retrievedDetails.page shouldBe 1
        retrievedDetails.content.size shouldBe 4
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[8].messageId
        retrievedDetails.content[1].messageId shouldBe details[7].messageId
        retrievedDetails.content[2].messageId shouldBe details[6].messageId
        retrievedDetails.content[3].messageId shouldBe details[5].messageId

        val page2 = page1.next()
        retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page2)
        retrievedDetails.page shouldBe 2
        retrievedDetails.content.size shouldBe 4
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[4].messageId
        retrievedDetails.content[1].messageId shouldBe details[3].messageId
        retrievedDetails.content[2].messageId shouldBe details[2].messageId
        retrievedDetails.content[3].messageId shouldBe details[1].messageId

        val page3 = page2.next()
        retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(from, to, pageable = page3)
        retrievedDetails.page shouldBe 3
        retrievedDetails.content.size shouldBe 1
        retrievedDetails.totalPages shouldBe 3
        retrievedDetails.totalElements shouldBe 9
        retrievedDetails.content[0].messageId shouldBe details[0].messageId
    }

    "Should retrieve empty list if no message details within given time interval" {
        buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T13:00:00Z"),
            Instant.parse("2025-04-30T14:00:00Z")
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given readableId in the given time interval" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T12:57:00Z"),
            readableIdPattern = messageDetailsOutOfInterval2.generateReadableId()
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given cpaId in the given time interval" {
        val (_, _, _, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:55:00Z"),
            Instant.parse("2025-04-30T12:57:00Z"),
            cpaIdPattern = messageDetailsOutOfInterval2.cpaId
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve empty list if no message details with the given readableId and cpaId in the given time interval" {
        val (_, _, messageDetailsOutOfInterval1, messageDetailsOutOfInterval2) = buildAndInsertTestEbmsMessageDetailFindData(ebmsMessageDetailRepository)
        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            Instant.parse("2025-04-30T12:00:00Z"),
            Instant.parse("2025-04-30T13:00:00Z"),
            readableIdPattern = messageDetailsOutOfInterval2.generateReadableId(),
            cpaIdPattern = messageDetailsOutOfInterval1.cpaId
        )
        retrievedDetails.size shouldBe 0
    }

    "Should retrieve records by time interval and filtered by Role" {
        val roleFilter = "Utleverer"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(fromRole = roleFilter)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)

        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            role = roleFilter
        ).content

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve records by time interval and filtered by Service" {
        val serviceFilter = "HarBorgerEgenandelFritak"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(service = serviceFilter)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)

        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            service = serviceFilter
        ).content

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve records by time interval and filtered by Action" {
        val actionFilter = "EgenandelForesporsel"
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(action = actionFilter)

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)

        val retrievedDetails = ebmsMessageDetailRepository.findByTimeInterval(
            from = Instant.parse("2025-05-08T12:00:00Z"),
            to = Instant.parse("2025-05-08T13:00:00Z"),
            action = actionFilter
        ).content

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails2.requestId
    }

    "Should retrieve related request IDs by request IDs" {
        val messageDetails1 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-2"
        )

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        ebmsMessageDetailRepository.insert(messageDetails3)

        val requestIds = listOf(messageDetails1.requestId, messageDetails2.requestId, messageDetails3.requestId)
        val relatedRequestIds = ebmsMessageDetailRepository.findRelatedRequestIds(requestIds)

        relatedRequestIds.size shouldBe 3
        relatedRequestIds shouldContainKey messageDetails1.requestId
        relatedRequestIds[messageDetails1.requestId] shouldBe "${messageDetails1.requestId},${messageDetails2.requestId}"
        relatedRequestIds shouldContainKey messageDetails2.requestId
        relatedRequestIds[messageDetails2.requestId] shouldBe "${messageDetails1.requestId},${messageDetails2.requestId}"
        relatedRequestIds shouldContainKey messageDetails3.requestId
        relatedRequestIds[messageDetails3.requestId] shouldBe messageDetails3.requestId.toString()
    }

    "Should retrieve related readable IDs by request IDs" {
        val messageDetails1 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-1"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "conversationId-2"
        )

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        ebmsMessageDetailRepository.insert(messageDetails3)

        val requestIds = listOf(messageDetails1.requestId, messageDetails2.requestId, messageDetails3.requestId)
        val relatedReadableIds = ebmsMessageDetailRepository.findRelatedReadableIds(requestIds)

        relatedReadableIds.size shouldBe 3
        relatedReadableIds[messageDetails1.requestId] shouldBe "${messageDetails1.generateReadableId()},${messageDetails2.generateReadableId()}"
        relatedReadableIds[messageDetails2.requestId] shouldBe "${messageDetails1.generateReadableId()},${messageDetails2.generateReadableId()}"
        relatedReadableIds[messageDetails3.requestId] shouldBe messageDetails3.generateReadableId()
    }

    "Should retrieve records by message ID, conversation ID, and cpa ID" {
        val messageDetails1 = buildTestEbmsMessageDetail()
        val messageDetails2 = buildTestEbmsMessageDetail().copy(
            messageId = "different-message-id"
        )
        val messageDetails3 = buildTestEbmsMessageDetail().copy(
            conversationId = "different-conversation-id"
        )
        val messageDetails4 = buildTestEbmsMessageDetail().copy(
            cpaId = "different-cpa-id"
        )

        ebmsMessageDetailRepository.insert(messageDetails1)
        ebmsMessageDetailRepository.insert(messageDetails2)
        ebmsMessageDetailRepository.insert(messageDetails3)
        ebmsMessageDetailRepository.insert(messageDetails4)

        val retrievedDetails = ebmsMessageDetailRepository.findByMessageIdConversationIdAndCpaId(
            messageId = messageDetails1.messageId,
            conversationId = messageDetails1.conversationId,
            cpaId = messageDetails1.cpaId
        )

        retrievedDetails.size shouldBe 1
        retrievedDetails[0].requestId shouldBe messageDetails1.requestId
    }
})
