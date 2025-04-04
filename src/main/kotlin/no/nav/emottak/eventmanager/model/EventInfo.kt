package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable

@Serializable
data class EventInfo(
    val hendelsedato: String,
    val hendelsedeskr: String,
    val tillegsinfo: String,
    val mottakid: String,
    val role: String? = null,
    val service: String? = null,
    val action: String? = null,
    val referanse: String? = null,
    val avsender: String? = null
)
