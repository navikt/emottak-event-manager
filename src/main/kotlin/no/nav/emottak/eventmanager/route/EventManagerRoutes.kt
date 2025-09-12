package no.nav.emottak.eventmanager.route

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.emottak.eventmanager.constants.QueryConstants.FROM_DATE
import no.nav.emottak.eventmanager.constants.QueryConstants.ID
import no.nav.emottak.eventmanager.constants.QueryConstants.TO_DATE
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.route.EventManagerRoutes")

fun Routing.eventManagerRoutes(eventService: EventService, ebmsMessageDetailService: EbmsMessageDetailService) {
    get("/events") {
        if (!Validation.validateDateRangeRequest(call)) return@get

        val fromDate = Validation.parseDate(call.request.queryParameters[FROM_DATE]!!)
        val toDate = Validation.parseDate(call.request.queryParameters[TO_DATE]!!)

        log.debug("Retrieving events from database")
        val events = eventService.fetchEvents(fromDate, toDate)
        log.debug("Events retrieved: ${events.size}")
        log.debug("The last event: {}", events.lastOrNull())

        call.respond(events)
    }

    get("/message-details/{$ID}/events") {
        if (!Validation.validateMessageLogInfoRequest(call)) return@get

        val id = call.pathParameters[ID]!!

        log.debug("Retrieving related events info from database")
        val messageLoggInfo = eventService.fetchMessageLogInfo(id)
        log.debug("Related events info retrieved: {}", messageLoggInfo)

        call.respond(messageLoggInfo)
    }

    get("/message-details") {
        if (!Validation.validateDateRangeRequest(call)) return@get

        val fromDate = Validation.parseDate(call.request.queryParameters[FROM_DATE]!!)
        val toDate = Validation.parseDate(call.request.queryParameters[TO_DATE]!!)

        log.debug("Retrieving message details from database")
        val messageDetails = ebmsMessageDetailService.fetchEbmsMessageDetails(fromDate, toDate)
        log.debug("Message details retrieved: ${messageDetails.size}")
        log.debug("The last message details retrieved: {}", messageDetails.lastOrNull())

        call.respond(messageDetails)
    }

    get("/message-details/{$ID}") {
        if (!Validation.validateReadableIdInfoRequest(call)) return@get

        val id = call.pathParameters[ID]!!

        log.debug("Retrieving message details for readable ID: $id")
        val readableIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(id)
        log.debug("Message details for readable ID {} retrieved: {}", id, readableIdInfoList)

        call.respond(readableIdInfoList)
    }
}
