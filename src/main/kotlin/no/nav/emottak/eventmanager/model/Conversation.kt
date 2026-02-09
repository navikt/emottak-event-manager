package no.nav.emottak.eventmanager.model

import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import java.time.Instant

data class Conversation(
    val messageDetails: List<EbmsMessageDetail>,
    val createdAt: Instant,
    val latestEventAt: Instant,
    val latestEventStatus: EventStatusEnum
)
