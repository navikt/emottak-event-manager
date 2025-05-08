package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.service.EbmsMessageDetailsService
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

fun Application.configureNaisRouts(
    collectorRegistry: PrometheusMeterRegistry,
    eventService: EventService,
    ebmsMessageDetailsService: EbmsMessageDetailsService
) {
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
            log.info("fromDate: $fromDateParam, toDate: $toDateParam")

            if (fromDateParam.isNullOrEmpty()) {
                log.info("Request parameter is missing: fromDate")
                call.respond(HttpStatusCode.BadRequest)
            }
            if (toDateParam.isNullOrEmpty()) {
                log.info("Request parameter is missing: toDate")
                call.respond(HttpStatusCode.BadRequest)
            }

            val fromDate = parseDate(fromDateParam!!)
            val toDate = parseDate(toDateParam!!)

            log.debug("Retrieving events from database")
            val events = eventService.fetchEvents(fromDate, toDate)
            log.debug("Events retrieved: ${events.size}")
            log.debug("The last event: ${events.lastOrNull()}")

            call.respond(events)
        }

        get("/fetchMessageDetails") {
            val fromDateParam = call.request.queryParameters.get("fromDate")
            val toDateParam = call.request.queryParameters.get("toDate")
            log.info("fromDate: $fromDateParam, toDate: $toDateParam")

            if (fromDateParam.isNullOrEmpty()) {
                log.info("Request parameter is missing: fromDate")
                call.respond(HttpStatusCode.BadRequest)
            }
            if (toDateParam.isNullOrEmpty()) {
                log.info("Request parameter is missing: toDate")
                call.respond(HttpStatusCode.BadRequest)
            }

            val fromDate = parseDate(fromDateParam!!)
            val toDate = parseDate(toDateParam!!)

            log.debug("Retrieving message details from database")
            val events = ebmsMessageDetailsService.fetchEbmsMessageDetails(fromDate, toDate)
            log.debug("Events retrieved: ${events.size}")
            log.debug("The last event: ${events.lastOrNull()}")

            call.respond(events)
        }
    }
}

fun parseDate(dateString: String, dateFormatString: String = "yyyy-MM-dd'T'HH:mm"): Instant {
    val formatter = DateTimeFormatter.ofPattern(dateFormatString)
    return LocalDateTime.parse(dateString, formatter)
        .atZone(ZoneId.of("Europe/Oslo"))
        .toInstant()
}
