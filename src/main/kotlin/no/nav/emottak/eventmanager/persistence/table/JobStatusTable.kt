package no.nav.emottak.eventmanager.persistence.table

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.time.Instant

object JobStatusTable : Table("job_status") {
    val jobName: Column<String> = varchar("job_name", 256)
    val startedAt: Column<Instant> = timestamp("started_at")
        .defaultExpression(CurrentTimestamp)
    val updatedAt: Column<Instant> = timestamp("updated_at")
    val completedAt: Column<Instant> = timestamp("completed_at")
    val resultText: Column<String> = varchar("result_text", 256)

    override val primaryKey = PrimaryKey(jobName)
}
