package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.util.logging.error
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

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
    ebmsMessageDetailService: EbmsMessageDetailService
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
            if (!validateDateRangeRequest(call)) return@get

            val fromDate = parseDate(call.request.queryParameters.get("fromDate")!!)
            val toDate = parseDate(call.request.queryParameters.get("toDate")!!)

            log.debug("Retrieving events from database")
            val events = eventService.fetchEvents(fromDate, toDate)
            log.debug("Events retrieved: ${events.size}")
            log.debug("The last event: ${events.lastOrNull()}")

            call.respond(events)
        }

        get("/fetchMessageDetails") {
            if (!validateDateRangeRequest(call)) return@get

            val fromDate = parseDate(call.request.queryParameters.get("fromDate")!!)
            val toDate = parseDate(call.request.queryParameters.get("toDate")!!)

            log.debug("Retrieving message details from database")
            val messageDetails = ebmsMessageDetailService.fetchEbmsMessageDetails(fromDate, toDate)
            log.debug("Message details retrieved: ${messageDetails.size}")
            log.debug("The last message details retrieved: ${messageDetails.lastOrNull()}")

            call.respond(messageDetails)
        }

        get("/fetchMessageLoggInfo") {
            if (!validateRequestIdRequest(call)) return@get

            val requestId = Uuid.parse(call.request.queryParameters.get("requestId")!!)

            log.debug("Retrieving related events info from database")
            val messageLoggInfo = eventService.fetchMessageLoggInfo(requestId)
            log.debug("Related events info retrieved: $messageLoggInfo")

            call.respond(messageLoggInfo)
        }
    }
}

suspend fun validateDateRangeRequest(call: RoutingCall): Boolean {
    val parameters = call.request.queryParameters
    log.info("Validating date range request parameters: $parameters")

    val fromDateParam = parameters["fromDate"]
    val toDateParam = parameters["toDate"]

    var errorMessage = ""
    if (fromDateParam.isNullOrEmpty()) {
        errorMessage = "Request parameter is missing: fromDate"
        log.error(IllegalArgumentException(errorMessage))
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }
    if (toDateParam.isNullOrEmpty()) {
        errorMessage = "Request parameter is missing: toDate"
        log.error(IllegalArgumentException(errorMessage))
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }

    try {
        parseDate(fromDateParam)
    } catch (e: Exception) {
        errorMessage = "Invalid date format for fromDate: $fromDateParam"
        log.error(errorMessage, e)
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }
    try {
        parseDate(toDateParam)
    } catch (e: Exception) {
        errorMessage = "Invalid date format for toDate: $toDateParam"
        log.error(errorMessage, e)
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }
    return true
}

suspend fun validateRequestIdRequest(call: RoutingCall): Boolean {
    val parameters = call.request.queryParameters
    log.info("Validating date request ID parameters: $parameters")

    val requestIdParam = parameters["requestId"]

    var errorMessage = ""
    if (requestIdParam.isNullOrEmpty()) {
        errorMessage = "Request parameter is missing: requestId"
        log.error(IllegalArgumentException(errorMessage))
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }

    try {
        Uuid.parse(requestIdParam)
    } catch (e: Exception) {
        errorMessage = "Parameter 'requestId' is not a valid UUID: $requestIdParam"
        log.error(errorMessage, e)
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        return false
    }

    return true
}

fun parseDate(dateString: String, dateFormatString: String = "yyyy-MM-dd'T'HH:mm"): Instant {
    val formatter = DateTimeFormatter.ofPattern(dateFormatString)
    return LocalDateTime.parse(dateString, formatter)
        .atZone(ZoneId.of("Europe/Oslo"))
        .toInstant()
}
