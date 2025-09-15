package no.nav.emottak.eventmanager.route.validation

import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingCall
import io.ktor.util.logging.error
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.constants.Constants
import no.nav.emottak.eventmanager.constants.QueryConstants.CONVERSATION_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.CPA_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.FROM_DATE
import no.nav.emottak.eventmanager.constants.QueryConstants.ID
import no.nav.emottak.eventmanager.constants.QueryConstants.MESSAGE_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.REQUEST_ID
import no.nav.emottak.eventmanager.constants.QueryConstants.TO_DATE
import no.nav.emottak.utils.common.model.DuplicateCheckRequest
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid

private val log = LoggerFactory.getLogger("no.nav.emottak.eventmanager.route.validation.Validation")

object Validation {

    suspend fun validateDateRangeRequest(call: RoutingCall): Boolean {
        val parameters = call.request.queryParameters
        log.info("Validating date range request parameters: $parameters")
        if (!validateIsValidDate(call, parameters, FROM_DATE)) return false
        if (!validateIsValidDate(call, parameters, TO_DATE)) return false
        val fromDate = parseDate(parameters[FROM_DATE]!!)
        val toDate = parseDate(parameters[TO_DATE]!!)
        if (fromDate.isAfter(toDate)) {
            val errorMessage = "Fromdate: $fromDate is after Todate: $toDate"
            log.error(IllegalArgumentException(errorMessage))
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }
        return true
    }

    suspend fun validateMessageLogInfoRequest(call: RoutingCall): Boolean {
        val parameters = call.pathParameters
        log.info("Validating Message log info request parameters: $parameters")
        return validateIsNotNullOrEmpty(call, parameters, ID)
    }

    suspend fun validateDuplicateCheckRequest(
        call: RoutingCall,
        duplicateCheckRequestJson: String
    ): Boolean {
        log.info("Validating duplicate check request: $duplicateCheckRequestJson")

        val duplicateCheckRequest: DuplicateCheckRequest = try {
            Json.decodeFromString<DuplicateCheckRequest>(duplicateCheckRequestJson)
        } catch (e: Exception) {
            val errorMessage = "DuplicateCheckRequest is not valid: $duplicateCheckRequestJson"
            log.error(errorMessage, e)
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }

        val requiredFieldMissing = when {
            duplicateCheckRequest.requestId.isBlank() -> REQUEST_ID
            duplicateCheckRequest.messageId.isBlank() -> MESSAGE_ID
            duplicateCheckRequest.conversationId.isBlank() -> CONVERSATION_ID
            duplicateCheckRequest.cpaId.isBlank() -> CPA_ID
            else -> ""
        }

        if (requiredFieldMissing.isNotEmpty()) {
            val errorMessage = "Required request parameter is missing: $requiredFieldMissing"
            log.error(IllegalArgumentException(errorMessage))
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }

        return true
    }

    suspend fun validateReadableIdInfoRequest(call: RoutingCall): Boolean {
        val parameters = call.pathParameters
        log.info("Validating Readable ID request parameters: $parameters")
        return validateIsNotNullOrEmpty(call, parameters, ID)
    }

    private suspend fun validateIsNotNullOrEmpty(call: RoutingCall, parameters: Parameters, field: String): Boolean {
        val requestParam = parameters[field]
        if (requestParam.isNullOrEmpty()) {
            val errorMessage = "Request parameter is missing: $field"
            log.error(IllegalArgumentException(errorMessage))
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }
        return true
    }

    private suspend fun validateIsValidDate(call: RoutingCall, parameters: Parameters, field: String): Boolean {
        if (!validateIsNotNullOrEmpty(call, parameters, field)) return false
        val dateParam = parameters[field]!!
        try {
            parseDate(dateParam)
        } catch (e: Exception) {
            val errorMessage = "Invalid date format for $field: $dateParam"
            log.error(errorMessage, e)
            call.respond(HttpStatusCode.BadRequest, errorMessage)
            return false
        }
        return true
    }

    fun parseDate(dateString: String, dateFormatString: String = "yyyy-MM-dd'T'HH:mm"): Instant {
        val formatter = DateTimeFormatter.ofPattern(dateFormatString)
        return LocalDateTime.parse(dateString, formatter)
            .atZone(ZoneId.of(Constants.ZONE_ID_OSLO))
            .toInstant()
    }

    fun isValidUuid(value: String): Boolean {
        return try {
            Uuid.parse(value)
            true
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
