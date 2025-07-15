package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class MessageLoggInfo(
    val hendelsesdato: String,
    val hendelsesbeskrivelse: String,
    val hendelsesid: String
)
