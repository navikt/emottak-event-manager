package no.nav.emottak.eventmanager.model

sealed class ProcedureArgs {
    data class DeleteServiceArgs(val service: String, val batchSize: Int = 10000) : ProcedureArgs()
    data class CleanupArgs(val hours: Int, val batchSize: Int = 10000) : ProcedureArgs()
}
