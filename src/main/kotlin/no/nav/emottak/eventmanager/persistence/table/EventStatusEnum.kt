package no.nav.emottak.eventmanager.persistence.table

enum class EventStatusEnum(val dbValue: String, val description: String) {
    CREATED("Opprettet", "*Statusen er ikke i bruk nå"),
    INFORMATION("Informasjon", "Meldingen er under behandling"),
    MANUAL_PROCESSING("Manuell behandling", "*Statusen er ikke i bruk nå"),
    WARNING("Advarsel", "*Statusen er ikke i bruk nå"),
    ERROR("Feil", "Meldingen feilet under behandling"),
    FATAL_ERROR("Fatal feil", "*Statusen er ikke i bruk nå"),
    PROCESSING_COMPLETED("Ferdigbehandlet", "Meldingen er ferdigbehandlet") ;

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
