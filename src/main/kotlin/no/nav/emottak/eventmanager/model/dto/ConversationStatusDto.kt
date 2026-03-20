package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConversationStatusDto(
    val createdAt: String,
    val readableIdList: String,
    val service: String,
    val cpaId: String,
    val statusAt: String,
    val latestStatus: String
)
