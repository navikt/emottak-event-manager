package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageInfo(
    val datomottat: String,
    val requestid: String? = null,
    val mottakidliste: String,
    val role: String? = null,
    val service: String? = null,
    val action: String? = null,
    val referanse: String? = null,
    val avsender: String? = null,
    val cpaid: String? = null,
    val antall: Int,
    val status: String? = null
)
