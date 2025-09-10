package no.nav.emottak.eventmanager

import arrow.continuations.SuspendApp
import arrow.continuations.ktor.server
import arrow.core.raise.result
import arrow.fx.coroutines.ResourceScope
import arrow.fx.coroutines.resourceScope
import io.ktor.server.application.Application
import io.ktor.server.netty.Netty
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import no.nav.emottak.eventmanager.configuration.config
import no.nav.emottak.eventmanager.kafka.startEventReceiver
import no.nav.emottak.eventmanager.persistence.Database
import no.nav.emottak.eventmanager.persistence.eventDbConfig
import no.nav.emottak.eventmanager.persistence.eventMigrationConfig
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailRepository
import no.nav.emottak.eventmanager.persistence.repository.EventRepository
import no.nav.emottak.eventmanager.persistence.repository.EventTypeRepository
import no.nav.emottak.eventmanager.plugin.configureAuthentication
import no.nav.emottak.eventmanager.plugin.configureContentNegotiation
import no.nav.emottak.eventmanager.plugin.configureMetrics
import no.nav.emottak.eventmanager.plugin.configureRoutes
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import org.slf4j.LoggerFactory
import kotlin.coroutines.coroutineContext

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.App")

fun main(args: Array<String>) = SuspendApp {
    result {
        resourceScope {
            runServer()
            awaitCancellation()
        }
    }
}

suspend fun ResourceScope.runServer() {
    val config = config()
    val prometheusMeterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

    val database = Database(eventDbConfig.value)
    database.migrate(eventMigrationConfig.value)

    val eventRepository = EventRepository(database)
    val ebmsMessageDetailRepository = EbmsMessageDetailRepository(database)
    val eventTypeRepository = EventTypeRepository(database)

    val eventService = EventService(eventRepository, ebmsMessageDetailRepository)
    val ebmsMessageDetailService =
        EbmsMessageDetailService(eventRepository, ebmsMessageDetailRepository, eventTypeRepository)

    val serverConfig = config.server
    server(
        factory = Netty,
        port = serverConfig.port.value,
        preWait = serverConfig.preWait,
        module = eventManagerModule(eventService, ebmsMessageDetailService, prometheusMeterRegistry)
    )

    log.debug("Configuration: {}", config)
    if (config.eventConsumer.active) {
        log.info("Starting event receiver")
        CoroutineScope(coroutineContext + Dispatchers.IO).launch {
            startEventReceiver(
                listOf(
                    config.eventConsumer.eventTopic,
                    config.eventConsumer.messageDetailsTopic
                ),
                eventService,
                ebmsMessageDetailService
            )
        }
    }
}

fun eventManagerModule(
    eventService: EventService,
    ebmsMessageDetailService: EbmsMessageDetailService,
    prometheusMeterRegistry: PrometheusMeterRegistry
): Application.() -> Unit {
    return {
        configureMetrics(prometheusMeterRegistry)
        configureContentNegotiation()
        configureAuthentication()
        configureRoutes(eventService, ebmsMessageDetailService, prometheusMeterRegistry)
    }
}
