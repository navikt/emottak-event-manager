package no.nav.emottak.eventmanager

import com.zaxxer.hikari.HikariConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import no.nav.emottak.eventmanager.configuration.config
import no.nav.emottak.eventmanager.kafka.startEventReceiver
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.eventDbConfig
import no.nav.emottak.eventmanager.persistence.eventMigrationConfig
import no.nav.emottak.eventmanager.service.EventService
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.Application")
val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val eventsService = EventsService()
val config = config()

fun main(args: Array<String>) = runBlocking {
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = eventManagerModule(
            eventDbConfig.value,
            eventMigrationConfig.value
        )
    ).start(wait = true)

    if (config.eventConsumer.active) {
        launch(Dispatchers.IO) {
            val eventService = EventService()
            startEventReceiver(config.eventConsumer.eventTopic, eventService)
        }
    }
}

fun eventManagerModule(
    dbConfig: HikariConfig,
    migrationDbConfig: HikariConfig
): Application.() -> Unit {
    return {
        val database = Database(dbConfig)
        database.migrate(migrationDbConfig)

        install(ContentNegotiation) { json() }
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        configureRouting()
        configureNaisRouts(appMicrometerRegistry, eventsService)
    }
}
