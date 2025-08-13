package no.nav.emottak.eventmanager

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import no.nav.emottak.utils.common.model.DuplicateCheckResponse

fun Application.configureRouting(
    eventService: EventService,
    ebmsMessageDetailService: EbmsMessageDetailService
) {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
        get("/fetchevents") {
            if (!Validation.validateDateRangeRequest(call)) return@get

            val fromDate = Validation.parseDate(call.request.queryParameters.get("fromDate")!!)
            val toDate = Validation.parseDate(call.request.queryParameters.get("toDate")!!)

            log.debug("Retrieving events from database")
            val events = eventService.fetchEvents(fromDate, toDate)
            log.debug("Events retrieved: ${events.size}")
            log.debug("The last event: ${events.lastOrNull()}")

            call.respond(events)
        }

        get("/fetchMessageDetails") {
            if (!Validation.validateDateRangeRequest(call)) return@get

            val fromDate = Validation.parseDate(call.request.queryParameters.get("fromDate")!!)
            val toDate = Validation.parseDate(call.request.queryParameters.get("toDate")!!)

            log.debug("Retrieving message details from database")
            val messageDetails = ebmsMessageDetailService.fetchEbmsMessageDetails(fromDate, toDate)
            log.debug("Message details retrieved: ${messageDetails.size}")
            log.debug("The last message details retrieved: ${messageDetails.lastOrNull()}")

            call.respond(messageDetails)
        }

        get("/fetchMessageLoggInfo") {
            if (!Validation.validateMessageLoggInfoRequest(call)) return@get

            val id = call.request.queryParameters.get("requestId")!!

            log.debug("Retrieving related events info from database")
            val messageLoggInfo = eventService.fetchMessageLoggInfo(id)
            log.debug("Related events info retrieved: $messageLoggInfo")

            call.respond(messageLoggInfo)
        }

        get("/fetchMottakIdInfo") {
            if (!Validation.validateMottakIdInfoRequest(call)) return@get

            val id = call.request.queryParameters.get("requestId")!!

            log.debug("Retrieving message details for mutable ID: $id")
            val mottakIdInfoList = ebmsMessageDetailService.fetchEbmsMessageDetails(id)
            log.debug("Message details for mutable ID $id retrieved: $mottakIdInfoList")

            call.respond(mottakIdInfoList)
        }

        authenticate(AZURE_AD_AUTH) {
            post("/duplicateCheck") {
                val duplicateCheckRequestJson = call.receiveText()
                if (!Validation.validateDuplicateCheckRequest(call, duplicateCheckRequestJson)) return@post

                val duplicateCheckRequest: DuplicateCheckRequest = Json.decodeFromString<DuplicateCheckRequest>(duplicateCheckRequestJson)
                log.debug("Received duplicate check request: $duplicateCheckRequest")

                val isDuplicate = ebmsMessageDetailService.isDuplicate(
                    messageId = duplicateCheckRequest.messageId,
                    conversationId = duplicateCheckRequest.conversationId,
                    cpaId = duplicateCheckRequest.cpaId
                )
                if (isDuplicate) {
                    log.info("Message is duplicated: ${duplicateCheckRequest.requestId}")
                }

                val duplicateCheckResponse = DuplicateCheckResponse(
                    requestId = duplicateCheckRequest.requestId,
                    isDuplicate = isDuplicate
                )
                log.debug("Duplicate check response: $duplicateCheckResponse")

                call.respond(duplicateCheckResponse)
            }
        }
    }
}

fun Application.configureNaisRouts(
    collectorRegistry: PrometheusMeterRegistry
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
    }
}
