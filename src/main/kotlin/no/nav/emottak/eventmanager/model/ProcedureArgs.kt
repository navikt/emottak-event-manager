package no.nav.emottak.eventmanager.model

sealed class ProcedureArgs {
    data class DeleteServiceArgs(val service: String) : ProcedureArgs()
    data class CleanupArgs(val hours: Int) : ProcedureArgs()
}
