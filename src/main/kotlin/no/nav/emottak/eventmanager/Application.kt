package no.nav.emottak.eventmanager

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.resourceScope
import com.zaxxer.hikari.HikariConfig
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
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
val eventService = EventService()

fun main(args: Array<String>) = SuspendApp {
    result {
        resourceScope {
            server(
                factory = Netty,
                port = 8080,
                module = eventManagerModule(
                    eventDbConfig.value,
                    eventMigrationConfig.value
                )
            )

            log.debug("Configuration: $config")
            if (config.eventConsumer.active) {
                log.info("Starting event receiver")
                launch(Dispatchers.IO) {
                    startEventReceiver(config.eventConsumer.eventTopic, eventService)
                }
            }

            awaitCancellation()
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
