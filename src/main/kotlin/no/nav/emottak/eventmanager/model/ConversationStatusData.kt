package no.nav.emottak.eventmanager.model

import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import java.time.Instant

data class ConversationStatusData(
    val conversationId: String,
    val createdAt: Instant,
    val readableIdList: String,
    val service: String,
    val cpaId: String,
    val statusAt: Instant,
    val latestStatus: EventStatusEnum
)
