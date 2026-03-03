package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import no.nav.emottak.eventmanager.model.ASCENDING
import no.nav.emottak.eventmanager.model.ConversationStatusInfo
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.eventmanager.model.Page
import no.nav.emottak.eventmanager.model.Pageable
import no.nav.emottak.eventmanager.persistence.repository.ConversationStatusRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.ERROR
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.INFORMATION
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum.PROCESSING_COMPLETED
import no.nav.emottak.eventmanager.repository.buildTestConversationStatusData
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetailsForConversationStatus
import no.nav.emottak.eventmanager.repository.buildTestEventsForConversationStatus
import no.nav.emottak.utils.common.toOsloZone

class ConversationStatusServiceTest : StringSpec({
    val conversationStatusRepository = mockk<ConversationStatusRepository>()
    val conversationStatusService = ConversationStatusService(conversationStatusRepository)

    beforeEach {
        clearAllMocks()
    }

    "Should return conversation statuses" {
        val (c1md1, c1md2, c2md1, c1md3, c3md1) = buildTestEbmsMessageDetailsForConversationStatus()
        val (_, _, c2md1EventsList, c1md3EventsList, c3md1EventsList) = buildTestEventsForConversationStatus(c1md1, c1md2, c2md1, c1md3, c3md1)
        val conversationStatus1 = buildTestConversationStatusData(c1md3, c1md3EventsList.last())
        val conversationStatus2 = buildTestConversationStatusData(c2md1, c2md1EventsList.last())
        val conversationStatus3 = buildTestConversationStatusData(c3md1, c3md1EventsList.last())

        val list = listOf(conversationStatus1, conversationStatus2, conversationStatus3)
        val pageable = Pageable(1, list.size)

        coEvery { conversationStatusRepository.findByFilters(pageable = pageable) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            ASCENDING,
            list.size.toLong(),
            list
        )

        val conversationStatusPage = conversationStatusService.findByFilters(pageable = pageable)
        conversationStatusPage.size shouldBe 3
        conversationStatusPage.page shouldBe 1

        val conversationStatusList = conversationStatusPage.content
        conversationStatusList.size shouldBe 3

        assertConversationStatus(conversationStatusList[0], c1md3, ERROR)
        assertConversationStatus(conversationStatusList[1], c2md1, INFORMATION)
        assertConversationStatus(conversationStatusList[2], c3md1, PROCESSING_COMPLETED)
    }

    "Should return conversation statuses with filter on status" {
        val (c1md1, c1md2, c2md1, c1md3, c3md1) = buildTestEbmsMessageDetailsForConversationStatus()
        val (_, _, c2md1EventsList, c1md3EventsList, _) = buildTestEventsForConversationStatus(c1md1, c1md2, c2md1, c1md3, c3md1)
        val conversationStatus1 = buildTestConversationStatusData(c1md3, c1md3EventsList.last())
        val conversationStatus2 = buildTestConversationStatusData(c2md1, c2md1EventsList.last())

        val requestedStatuses = listOf(ERROR, INFORMATION)
        val list = listOf(conversationStatus1, conversationStatus2)
        val pageable = Pageable(1, list.size)

        coEvery { conversationStatusRepository.findByFilters(statuses = requestedStatuses, pageable = pageable) } returns Page(
            pageable.pageNumber,
            pageable.pageSize,
            ASCENDING,
            list.size.toLong(),
            list
        )

        val conversationStatusPage = conversationStatusService.findByFilters(statuses = requestedStatuses, pageable = pageable)
        conversationStatusPage.size shouldBe 2
        conversationStatusPage.page shouldBe 1

        val conversationStatusList = conversationStatusPage.content
        conversationStatusList.size shouldBe 2

        assertConversationStatus(conversationStatusList[0], c1md3, ERROR)
        assertConversationStatus(conversationStatusList[1], c2md1, INFORMATION)
    }
})

private fun assertConversationStatus(
    actualConversationStatusInfo: ConversationStatusInfo,
    expectedMessageDetail: EbmsMessageDetail,
    expectedStatus: EventStatusEnum
) {
    actualConversationStatusInfo.createdAt shouldBe expectedMessageDetail.savedAt.toOsloZone().toString()
    actualConversationStatusInfo.readableIdList shouldBe expectedMessageDetail.generateReadableId()
    actualConversationStatusInfo.cpaId shouldBe expectedMessageDetail.cpaId
    actualConversationStatusInfo.service shouldBe expectedMessageDetail.service
    actualConversationStatusInfo.statusAt shouldBe expectedMessageDetail.savedAt.plusMillis(1000).toOsloZone().toString()
    actualConversationStatusInfo.latestStatus shouldBe expectedStatus.dbValue
}
