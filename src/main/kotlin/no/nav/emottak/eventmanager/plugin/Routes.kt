package no.nav.emottak.eventmanager.plugin

import io.ktor.server.application.Application
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.route.duplicateCheckRoute
import no.nav.emottak.eventmanager.route.eventManagerRoutes
import no.nav.emottak.eventmanager.route.naisRoutes
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService

fun Application.configureRoutes(
    eventService: EventService,
    ebmsMessageDetailService: EbmsMessageDetailService,
    prometheusMeterRegistry: PrometheusMeterRegistry
) {
    routing {
        eventManagerRoutes(eventService, ebmsMessageDetailService)
        duplicateCheckRoute(ebmsMessageDetailService)
        naisRoutes(prometheusMeterRegistry)
        get("/") {
            call.respondText("Event Manager running properly")
        }
    }
}
