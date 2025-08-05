package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.util.logging.error
import kotlinx.serialization.json.Json
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

object Validation {

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

    suspend fun validateDuplicateCheckRequest(
        call: RoutingCall,
        duplicateCheckRequestJson: String
    ): Boolean {
        log.info("Validating duplicate check request: $duplicateCheckRequestJson")

        var errorMessage = ""
        val duplicateCheckRequest: DuplicateCheckRequest = try {
            Json.decodeFromString<DuplicateCheckRequest>(duplicateCheckRequestJson)
        } catch (e: Exception) {
            errorMessage = "DuplicateCheckRequest is not valid: $duplicateCheckRequestJson"
            log.error(errorMessage, e)
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }

        val requiredFieldMissing = when {
            duplicateCheckRequest.requestId.isBlank() -> "requestId"
            duplicateCheckRequest.messageId.isBlank() -> "messageId"
            duplicateCheckRequest.conversationId.isBlank() -> "conversationId"
            duplicateCheckRequest.cpaId.isBlank() -> "cpaId"
            else -> ""
        }

        if (requiredFieldMissing.isNotEmpty()) {
            errorMessage = "Required request parameter is missing: $requiredFieldMissing"
            log.error(IllegalArgumentException(errorMessage))
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }

        return true
    }

    suspend fun validateMottakIdInfoRequest(call: RoutingCall): Boolean {
        val parameters = call.request.queryParameters
        log.info("Validating Mottak ID request parameters: $parameters")

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
}
