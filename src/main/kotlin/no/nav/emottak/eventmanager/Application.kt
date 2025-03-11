package no.nav.emottak.eventmanager

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.configuration.config
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.eventDbConfig
import no.nav.emottak.eventmanager.persistence.eventMigrationConfig
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.Application")
val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
val eventsService = EventsService()
val config = config()

fun main(args: Array<String>) {
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = eventManagerModule()
    ).start(wait = true)
}

fun eventManagerModule(): Application.() -> Unit {
    return {
        if (config.environment.naisClusterName.value != "local") {
            val database = Database(eventDbConfig.value)
            database.migrate(eventMigrationConfig.value)
        }

        install(ContentNegotiation) { json() }
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        configureRouting()
        configureNaisRouts(appMicrometerRegistry, eventsService)
    }
}
