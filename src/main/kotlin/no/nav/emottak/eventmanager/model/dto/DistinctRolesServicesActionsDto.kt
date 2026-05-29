package no.nav.emottak.eventmanager.model.dto

import kotlinx.serialization.Serializable

@Serializable
data class DistinctRolesServicesActionsDto(
    val roles: List<String>,
    val services: List<String>,
    val actions: List<String>
)
