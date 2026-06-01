package no.nav.emottak.eventmanager.model

import net.logstash.logback.marker.LogstashMarker
import net.logstash.logback.marker.Markers
import no.nav.emottak.eventmanager.constants.Constants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.eventmanager.constants.Constants.MESSAGEERROR_ACTION
import no.nav.emottak.eventmanager.constants.Constants.NOT_APPLICABLE_ROLE
import no.nav.emottak.eventmanager.constants.Constants.SENDER_NAME_NAV_MOTTAK
import no.nav.emottak.utils.common.constants.LogFields.ACTION
import no.nav.emottak.utils.common.constants.LogFields.CONVERSATION_ID
import no.nav.emottak.utils.common.constants.LogFields.CPA_ID
import no.nav.emottak.utils.common.constants.LogFields.FROM_PARTY
import no.nav.emottak.utils.common.constants.LogFields.FROM_ROLE
import no.nav.emottak.utils.common.constants.LogFields.MESSAGE_ID
import no.nav.emottak.utils.common.constants.LogFields.SERVICE
import no.nav.emottak.utils.common.constants.LogFields.TO_PARTY
import no.nav.emottak.utils.common.constants.LogFields.TO_ROLE
import no.nav.emottak.utils.common.constants.LogFields.X_REQUEST_ID
import no.nav.emottak.utils.common.toOsloZone
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.uuid.Uuid
import no.nav.emottak.utils.kafka.model.EbmsMessageDetail as TransportEbmsMessageDetail

data class EbmsMessageDetail(
    // Basic fields
    val requestId: Uuid,
    val cpaId: String,
    val conversationId: String,
    val messageId: String,
    val refToMessageId: String? = null,
    val fromPartyId: String,
    val fromRole: String? = null,
    val toPartyId: String,
    val toRole: String? = null,
    val service: String,
    val action: String,
    val sentAt: Instant? = null,
    val savedAt: Instant,

    // Extra fields
    val senderName: String? = null,
    val refParam: String? = null,
    val readableId: String? = null
) {

    val marker: LogstashMarker = Markers.appendEntries(
        mapOf(
            X_REQUEST_ID to this.requestId,
            MESSAGE_ID to this.messageId,
            CONVERSATION_ID to this.conversationId,
            CPA_ID to this.cpaId,
            SERVICE to this.service,
            ACTION to this.action,
            TO_ROLE to (this.toRole ?: ""),
            FROM_ROLE to (this.fromRole ?: ""),
            TO_PARTY to this.toPartyId,
            FROM_PARTY to this.fromPartyId
        )
    )

    companion object {
        fun fromTransportModel(transportEbmsMessageDetail: TransportEbmsMessageDetail): EbmsMessageDetail {
            return EbmsMessageDetail(
                requestId = transportEbmsMessageDetail.requestId,
                cpaId = transportEbmsMessageDetail.cpaId,
                conversationId = transportEbmsMessageDetail.conversationId,
                messageId = transportEbmsMessageDetail.messageId,
                refToMessageId = transportEbmsMessageDetail.refToMessageId,
                fromPartyId = transportEbmsMessageDetail.fromPartyId,
                fromRole = transportEbmsMessageDetail.fromRole,
                toPartyId = transportEbmsMessageDetail.toPartyId,
                toRole = transportEbmsMessageDetail.toRole,
                service = transportEbmsMessageDetail.service,
                action = transportEbmsMessageDetail.action,
                sentAt = transportEbmsMessageDetail.sentAt,
                savedAt = transportEbmsMessageDetail.savedAt
            )
        }
    }

    fun generateReadableId(): String {
        val direction = getDirection()

        val formatter = DateTimeFormatter.ofPattern("yyMMddHHmm")
        val savedAtString: String = this.savedAt.toOsloZone()
            .format(formatter)

        val senderName = if (getReadableSenderName() == SENDER_NAME_NAV_MOTTAK) {
            "NAVM"
        } else {
            this.senderName?.replace("\\s".toRegex(), "")?.take(4)?.lowercase() ?: "UNKN"
        }

        val id = this.requestId.toString().takeLast(6)

        return "$direction.$savedAtString.$senderName.$id"
    }

    fun getDirection() = if (isIncomingMessage()) "IN" else "OUT"

    fun getReadableSenderName() = if (isIncomingMessage()) SENDER_NAME_NAV_MOTTAK else this.senderName

    private fun isIncomingMessage() = if (this.refToMessageId == null) {
        true
    } else {
        this.toRole == NOT_APPLICABLE_ROLE && this.action in status_actions
    }
}

private val status_actions = listOf(ACKNOWLEDGMENT_ACTION, MESSAGEERROR_ACTION)
