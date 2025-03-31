package no.nav.emottak.eventmanager.persistence.table

enum class EventStatusEnum(val dbValue: String) {
    CREATED("Opprettet"),
    INFORMATION("Informasjon"),
    MANUAL_PROCESSING("Manuell behandling"),
    WARNING("Advarsel"),
    ERROR("Feil"),
    FATAL_ERROR("Fatal feil"),
    PROCESSING_COMPLETED("Ferdigbehandlet");

    companion object {
        fun fromDbValue(value: String): EventStatusEnum {
            return entries.find { it.dbValue == value }
                ?: throw IllegalArgumentException("Unknown event status: $value")
        }
    }

    override fun toString(): String {
        return dbValue
    }
}
