package no.nav.emottak.eventmanager.model

data class DistinctRolesServicesActions(
    val roles: List<String>,
    val services: List<String>,
    val actions: List<String>,
    val refreshedAt: java.time.LocalDateTime
)
