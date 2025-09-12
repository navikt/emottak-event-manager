package no.nav.emottak.eventmanager.route

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.route.EventManagerRoutes")

fun Routing.eventManagerRoutes(eventService: EventService, ebmsMessageDetailService: EbmsMessageDetailService) {
    get("/fetchevents") {
        if (!Validation.validateDateRangeRequest(call)) return@get

        val fromDate = Validation.parseDate(call.request.queryParameters["fromDate"]!!)
        val toDate = Validation.parseDate(call.request.queryParameters["toDate"]!!)

        log.debug("Retrieving events from database")
        val events = eventService.fetchEvents(fromDate, toDate)
        log.debug("Events retrieved: ${events.size}")
        log.debug("The last event: {}", events.lastOrNull())

        call.respond(events)
    }

    get("/fetchMessageLoggInfo") {
        if (!Validation.validateMessageLoggInfoRequest(call)) return@get

        val id = call.request.queryParameters["id"]!!

        log.debug("Retrieving related events info from database")
        val messageLoggInfo = eventService.fetchMessageLoggInfo(id)
        log.debug("Related events info retrieved: {}", messageLoggInfo)

        call.respond(messageLoggInfo)
    }

    get("/fetchMessageDetails") {
        if (!Validation.validateDateRangeRequest(call)) return@get

        val fromDate = Validation.parseDate(call.request.queryParameters["fromDate"]!!)
        val toDate = Validation.parseDate(call.request.queryParameters["toDate"]!!)

        log.debug("Retrieving message details from database")
        val messageDetails = ebmsMessageDetailService.fetchEbmsMessageDetails(fromDate, toDate)
        log.debug("Message details retrieved: ${messageDetails.size}")
        log.debug("The last message details retrieved: {}", messageDetails.lastOrNull())

        call.respond(messageDetails)
    }

    get("/fetchMottakIdInfo") {
        if (!Validation.validateMottakIdInfoRequest(call)) return@get

        val id = call.request.queryParameters["id"]!!

        log.debug("Retrieving message details for mutable ID: $id")
        val mottakIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(id)
        log.debug("Message details for mutable ID {} retrieved: {}", id, mottakIdInfoList)

        call.respond(mottakIdInfoList)
    }
}
