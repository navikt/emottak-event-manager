package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.service.EventService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}

fun Application.configureNaisRouts(collectorRegistry: PrometheusMeterRegistry, eventService: EventService) {
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
            val fromDateParam = call.request.queryParameters.get("fromDate")
            val toDateParam = call.request.queryParameters.get("toDate")

            if (fromDateParam.isNullOrEmpty()) {
                log.info("Mangler parameter: fromDate")
                call.respond(HttpStatusCode.BadRequest)
            }
            if (toDateParam.isNullOrEmpty()) {
                log.info("Mangler parameter: toDate")
                call.respond(HttpStatusCode.BadRequest)
            }

            val fromDate = parseDate(fromDateParam!!)
            val toDate = parseDate(toDateParam!!)

            log.debug("Henter hendelser fra events endepunktet...")
            val events = eventService.fetchEvents(fromDate, toDate)
            log.debug("Antall hendelser fra endepunktet : ${events.size}")
            log.debug("Henter siste hendelse : ${events.lastOrNull()}")

            call.respond(events)
        }
    }
}

fun parseDate(dateString: String, dateFormatString: String = "yyyy-MM-dd HH:mm"): Instant {
    val formatter = DateTimeFormatter.ofPattern(dateFormatString)
    return LocalDateTime.parse(dateString, formatter)
        .atZone(ZoneId.of("UTC"))
        .toInstant()
}
