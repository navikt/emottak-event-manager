package no.nav.emottak.eventmanager.repository

import com.zaxxer.hikari.HikariConfig
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.emottak.eventmanager.model.EventType
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.EVENT_DB_NAME
import no.nav.emottak.eventmanager.persistence.repository.EventTypesRepository
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import org.testcontainers.containers.PostgreSQLContainer

class EventTypesRepositoryTest : StringSpec({

    lateinit var dbContainer: PostgreSQLContainer<Nothing>
    lateinit var db: Database
    lateinit var eventTypesRepository: EventTypesRepository

    beforeSpec {
        dbContainer = buildDatabaseContainer()
        dbContainer.start()
        db = Database(dbContainer.testConfiguration())
        db.migrate(db.dataSource)
        eventTypesRepository = EventTypesRepository(db)
    }

    afterSpec {
        dbContainer.stop()
    }

    "Should retrieve an event type by event type ID" {

        val retrievedEventType = eventTypesRepository.findEventTypeById(1)

        retrievedEventType shouldNotBe null
        // These values are from the migration script
        retrievedEventType?.eventTypeId shouldBe 1
        retrievedEventType?.description shouldBe "Melding mottatt via SMTP"
        retrievedEventType?.status shouldBe EventStatusEnum.INFORMATION
    }

    "Should retrieve a list of event types by a list of event type IDs" {

        val retrievedEventTypes = eventTypesRepository.findEventTypesByIds(listOf(1, 2, 3))

        retrievedEventTypes.size shouldBe 3
        // These values are from the migration script
        retrievedEventTypes shouldContainExactlyInAnyOrder listOf(
            EventType(1, "Melding mottatt via SMTP", EventStatusEnum.INFORMATION),
            EventType(2, "Feil ved mottak av melding via SMTP", EventStatusEnum.ERROR),
            EventType(3, "Melding sendt via SMTP", EventStatusEnum.PROCESSING_COMPLETED)
        )
    }
}) {
    companion object {
        fun PostgreSQLContainer<Nothing>.testConfiguration(): HikariConfig {
            return HikariConfig().apply {
                jdbcUrl = this@testConfiguration.jdbcUrl
                username = this@testConfiguration.username
                password = this@testConfiguration.password
                maximumPoolSize = 5
                minimumIdle = 1
                idleTimeout = 500001
                connectionTimeout = 10000
                maxLifetime = 600001
                initializationFailTimeout = 5000
            }
        }

        private fun buildDatabaseContainer(): PostgreSQLContainer<Nothing> =
            PostgreSQLContainer<Nothing>("postgres:15").apply {
                withUsername("$EVENT_DB_NAME-admin")
                withReuse(true)
                withLabel("app-name", "emottak-event-manager")
                start()
            }
    }
}
