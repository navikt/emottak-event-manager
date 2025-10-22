package no.nav.emottak.eventmanager.model

import kotlinx.serialization.Serializable
import no.nav.emottak.utils.serialization.InstantSerializer
import java.time.Instant

@Serializable
data class DistinctRolesServicesActions(
    val roles: List<String>,
    val services: List<String>,
    val actions: List<String>,
    @Serializable(with = InstantSerializer::class)
    val refreshedAt: Instant
)
