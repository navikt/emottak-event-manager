package no.nav.emottak.eventmanager.route

import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import no.nav.emottak.eventmanager.constants.QueryConstants.ACTION
import no.nav.emottak.eventmanager.constants.QueryConstants.CPA_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.FROM_DATE
import no.nav.emottak.eventmanager.constants.QueryConstants.ID
import no.nav.emottak.eventmanager.constants.QueryConstants.MESSAGE_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.PAGE_NUMBER
import no.nav.emottak.eventmanager.constants.QueryConstants.PAGE_SIZE
import no.nav.emottak.eventmanager.constants.QueryConstants.READABLE_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.ROLE
import no.nav.emottak.eventmanager.constants.QueryConstants.SERVICE
import no.nav.emottak.eventmanager.constants.QueryConstants.SORT
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
        val role = call.request.queryParameters[ROLE] ?: ""
        val service = call.request.queryParameters[SERVICE] ?: ""
        val action = call.request.queryParameters[ACTION] ?: ""

        val pageable = Validation.getPageable(
            call,
            call.request.queryParameters[PAGE_NUMBER],
            call.request.queryParameters[PAGE_SIZE],
            call.request.queryParameters[SORT],
            50
        )
        if (pageable == null) return@get

        log.debug("Retrieving events from database, page ${pageable.pageNumber} with size ${pageable.pageSize} and sort order ${pageable.sort}")
        val eventsPage = eventService.fetchEvents(fromDate, toDate, role, service, action, pageable)
        val events = eventsPage.content
        log.debug("Events retrieved: ${events.size} of total ${eventsPage.totalElements}")
        log.debug("The last event: {}", events.lastOrNull())

        call.respond(eventsPage)
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
        val readableId = call.request.queryParameters[READABLE_ID] ?: ""
        val cpaId = call.request.queryParameters[CPA_ID] ?: ""
        val messageId = call.request.queryParameters[MESSAGE_ID] ?: ""
        val role = call.request.queryParameters[ROLE] ?: ""
        val service = call.request.queryParameters[SERVICE] ?: ""
        val action = call.request.queryParameters[ACTION] ?: ""

        val pageable = Validation.getPageable(
            call,
            call.request.queryParameters[PAGE_NUMBER],
            call.request.queryParameters[PAGE_SIZE],
            call.request.queryParameters[SORT],
            50
        )
        if (pageable == null) return@get

        log.debug("Retrieving message details from database, page ${pageable.pageNumber} with size ${pageable.pageSize} and sort order ${pageable.sort}")
        val messageDetailsPage = ebmsMessageDetailService.fetchEbmsMessageDetails(fromDate, toDate, readableId, cpaId, messageId, role, service, action, pageable)
        val messageDetails = messageDetailsPage.content
        log.debug("Message details retrieved: ${messageDetails.size} of total ${messageDetailsPage.totalElements}")
        log.debug("The last message details retrieved: {}", messageDetails.lastOrNull())

        call.respond(messageDetailsPage)
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
