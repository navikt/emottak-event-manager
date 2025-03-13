package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.toLocalDateTime
import io.ktor.utils.io.InternalAPI
import io.micrometer.prometheus.PrometheusMeterRegistry
import java.text.SimpleDateFormat

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}

@OptIn(InternalAPI::class)
fun Application.configureNaisRouts(collectorRegistry: PrometheusMeterRegistry, eventsService: EventsService) {
    routing {
        get("/internal/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/internal/health/readiness") {
            call.respondText("I'm ready! :)")
        }
        get("/internal/prometheus") {
            call.respond(collectorRegistry.scrape())
        }
        get("/fetchevents") {
            val fromDate = call.request.queryParameters.get("fromDate")
            val toDate = call.request.queryParameters.get("toDate")
            if (fromDate.isNullOrEmpty()) {
                log.info("Mangler parameter: from date")
                call.respond(HttpStatusCode.BadRequest)
            }
            val fom = SimpleDateFormat("yyyy-MM-dd HH:mm").parse(fromDate).toLocalDateTime()
            if (toDate.isNullOrEmpty()) {
                log.info("Mangler parameter: to date")
                call.respond(HttpStatusCode.BadRequest)
            }
            val tom = SimpleDateFormat("yyyy-MM-dd HH:mm").parse(toDate).toLocalDateTime()
            log.info("Henter hendelser fra events endepunktet til ebms ...")
            val events = eventsService.fetchevents(fom, tom)
            log.info("Antall hendelser fra endepunktet : ${events.size}")
            call.respond(events)
        }
    }
}
