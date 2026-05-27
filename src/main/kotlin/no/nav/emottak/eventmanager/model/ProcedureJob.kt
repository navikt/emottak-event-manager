package no.nav.emottak.eventmanager.model

data class ProcedureJob(
    val procedureName: String,
    val args: ProcedureArgs,
    val jobNr: Int
) {
    fun jobName() = when (args) {
        is ProcedureArgs.DeleteServiceArgs -> "$procedureName(${args.service}) $jobNr"
        is ProcedureArgs.CleanupArgs -> "$procedureName() $jobNr"
    }
}
