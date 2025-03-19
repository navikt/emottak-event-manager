package no.nav.emottak.eventmanager.persistence.table

enum class EventStatusEnum(val dbValue: String) {
    OPPRETTET("Opprettet"),
    INFORMASJON("Informasjon"),
    MANUELL_BEHANDLING("Manuell behandling"),
    ADVARSEL("Advarsel"),
    FEIL("Feil"),
    FATAL_FEIL("Fatal feil"),
    FERDIGBEHANDLET("Ferdigbehandlet");

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
