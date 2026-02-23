package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class ConversationStatusInfo(
    val createdAt: String,
    val readableIdList: String,
    val service: String,
    val cpaId: String,
    val statusAt: String,
    val latestStatus: String
)
