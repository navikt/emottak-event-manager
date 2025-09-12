package no.nav.emottak.eventmanager.route

import io.ktor.server.auth.authenticate
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.auth.AZURE_AD_AUTH
import no.nav.emottak.eventmanager.route.validation.Validation
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import no.nav.emottak.utils.common.model.DuplicateCheckResponse
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.route.DuplicateCheckRoute")

fun Route.duplicateCheckRoute(ebmsMessageDetailService: EbmsMessageDetailService) {
    authenticate(AZURE_AD_AUTH) {
        post("/message-details/duplicate-check") {
            val duplicateCheckRequestJson = call.receiveText()
            if (!Validation.validateDuplicateCheckRequest(call, duplicateCheckRequestJson)) return@post

            val duplicateCheckRequest: DuplicateCheckRequest =
                Json.decodeFromString<DuplicateCheckRequest>(duplicateCheckRequestJson)
            log.debug("Received duplicate check request: {}", duplicateCheckRequest)

            val isDuplicate = ebmsMessageDetailService.isDuplicate(
                messageId = duplicateCheckRequest.messageId,
                conversationId = duplicateCheckRequest.conversationId,
                cpaId = duplicateCheckRequest.cpaId
            )
            if (isDuplicate) {
                log.info("Message is duplicated: {}", duplicateCheckRequest.requestId)
            }

            val duplicateCheckResponse = DuplicateCheckResponse(
                requestId = duplicateCheckRequest.requestId,
                isDuplicate = isDuplicate
            )
            log.debug("Duplicate check response: {}", duplicateCheckResponse)

            call.respond(duplicateCheckResponse)
        }
    }
}
