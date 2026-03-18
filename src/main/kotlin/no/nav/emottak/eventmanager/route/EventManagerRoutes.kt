package no.nav.emottak.eventmanager.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.util.logging.error
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
import no.nav.emottak.eventmanager.constants.QueryConstants.STATUSES
import no.nav.emottak.eventmanager.constants.QueryConstants.TO_DATE
import no.nav.emottak.eventmanager.persistence.table.EventStatusEnum
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.eventmanager.service.ConversationStatusService
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.format.DateTimeParseException

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.route.EventManagerRoutes")

fun Route.eventManagerRoutes(
    eventService: EventService,
    ebmsMessageDetailService: EbmsMessageDetailService,
    conversationStatusService: ConversationStatusService
) {
    get("/filter-values") {
        val filterValues = ebmsMessageDetailService.getDistinctRolesServicesActions()
        log.debug("Got filter-values (last refreshed at: {})", filterValues.refreshedAt)
        call.respond(filterValues)
    }

    get("/events") {
        if (!Validation.validateDateRangeRequest(call)) return@get

        val fromDate = Validation.parseDate(call.request.queryParameters[FROM_DATE]!!)
        val toDate = Validation.parseDate(call.request.queryParameters[TO_DATE]!!)

        val (role, service, action) = getRoleServiceActionParameters(call)
        val pageable = getPagableParameters(call) ?: return@get

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

        val (role, service, action) = getRoleServiceActionParameters(call)
        val pageable = getPagableParameters(call) ?: return@get

        log.debug("Retrieving message details from database, page ${pageable.pageNumber} with size ${pageable.pageSize} and sort order ${pageable.sort}")
        val messageDetailsPage = ebmsMessageDetailService.fetchEbmsMessageDetails(
            fromDate,
            toDate,
            readableId,
            cpaId,
            messageId,
            role,
            service,
            action,
            pageable
        )
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

    get("/conversation-status") {
        var fromDate: Instant? = null
        if (!call.request.queryParameters[FROM_DATE].isNullOrBlank()) {
            fromDate = getInputDate(call, FROM_DATE) ?: return@get
        }
        var toDate: Instant? = null
        if (!call.request.queryParameters[TO_DATE].isNullOrBlank()) {
            toDate = getInputDate(call, TO_DATE) ?: return@get
        }
        val cpaIdPattern = call.request.queryParameters[CPA_ID] ?: ""
        val service = call.request.queryParameters[SERVICE] ?: ""
        val statuses = parseStatuses(call) ?: return@get
        debugConversationStatusInput(fromDate, toDate, cpaIdPattern, service, statuses)
        val conversationPage = conversationStatusService.findByFilters(fromDate, toDate, cpaIdPattern, service, statuses)
        log.debug("{} conversation statuses retrieved (out of a total of: {})", conversationPage.content.size, conversationPage.totalElements)
        call.respond(conversationPage)
    }
}

private fun getRoleServiceActionParameters(call: RoutingCall): Triple<String, String, String> {
    val role = call.request.queryParameters[ROLE] ?: ""
    val service = call.request.queryParameters[SERVICE] ?: ""
    val action = call.request.queryParameters[ACTION] ?: ""
    return Triple(role, service, action)
}

private suspend fun getPagableParameters(call: RoutingCall, defaultSize: Int = 50) =
    Validation.getPageable(
        call,
        call.request.queryParameters[PAGE_NUMBER],
        call.request.queryParameters[PAGE_SIZE],
        call.request.queryParameters[SORT],
        defaultSize
    )

private suspend fun getInputDate(call: RoutingCall, param: String) =
    try {
        Validation.parseDate(call.request.queryParameters[param]!!)
    } catch (_: DateTimeParseException) {
        val errorMessage = "Invalid date: $param"
        log.error(IllegalArgumentException(errorMessage))
        call.respond(HttpStatusCode.BadRequest, errorMessage)
        null
    }

private suspend fun parseStatuses(call: RoutingCall): List<EventStatusEnum>? {
    val statuses = call.request.queryParameters[STATUSES]
    if (statuses.isNullOrBlank()) return emptyList()
    log.debug("Parsing statuses: {}", statuses)
    try {
        return statuses.split(",").map { EventStatusEnum.fromDbValue(it) }
    } catch (e: IllegalArgumentException) {
        log.error(e)
        call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid status code")
        return null
    }
}

private fun debugConversationStatusInput(fromDate: Instant?, toDate: Instant?, cpaIdPattern: String, service: String, statuses: List<EventStatusEnum>) {
    var msg = "Retrieving conversation statuses with filters: "
    if (fromDate == null && toDate == null && cpaIdPattern == "" && service == "" && statuses.isEmpty()) {
        log.debug("Retrieving conversation statuses without filters")
        return
    }
    if (fromDate != null) msg += "fromDate: '$fromDate', "
    if (toDate != null) msg += "toDate: '$toDate', "
    if (cpaIdPattern != "") msg += "cpaIdPattern: '$cpaIdPattern', "
    if (service != "") msg += "service: '$service', "
    if (statuses.isNotEmpty()) msg += "statuses: '$statuses', "
    log.debug(msg.dropLast(2))
}
