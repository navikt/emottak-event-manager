package no.nav.emottak.eventmanager.model

import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import java.time.Instant

data class ConversationStatus(
    val conversationId: String,
    val createdAt: Instant,
    val latestStatus: EventStatusEnum,
    val statusAt: Instant
)
