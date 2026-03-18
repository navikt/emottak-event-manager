package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable
import no.nav.emottak.utils.serialization.InstantSerializer
import java.time.Instant

@Serializable
data class DistinctRolesServicesActionsDTO(
    val roles: List<String>,
    val services: List<String>,
    val actions: List<String>,
    @Serializable(with = InstantSerializer::class)
    val refreshedAt: Instant
)
