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
import org.slf4j.LoggerFactory

val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.Application")
val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

fun main(args: Array<String>) {
//    if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
//        DecoroutinatorRuntime.load()
//    }
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = eventManagerModule()
    ).start(wait = true)
}

fun eventManagerModule(): Application.() -> Unit {
    return {
        install(ContentNegotiation) { json() }
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
        configureRouting()
        configureNaisRouts(appMicrometerRegistry)
    }
}
