package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageInfo(
    val receivedDate: String,
    val readableIdList: String,
    val role: String? = null,
    val service: String? = null,
    val action: String? = null,
    val referenceParameter: String? = null,
    val senderName: String? = null,
    val cpaId: String? = null,
    val count: Int,
    val status: String? = null
)
