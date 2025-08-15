package no.nav.emottak.eventmanager.model

import java.time.Instant
import java.time.ZoneId
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
    val sender: String? = null,
    val refParam: String? = null,
    val mottakId: String? = null
) {
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

    fun calculateMottakId(): String {
        val direction = if (this.refToMessageId == null) "IN" else "OUT"

        val formatter = DateTimeFormatter.ofPattern("yyMMddHHmm")
        val savedAtString: String = this.savedAt
            .atZone(ZoneId.of("Europe/Oslo"))
            .format(formatter)

        val sender = if (this.refToMessageId != null) {
            "NAVM"
        } else {
            this.sender?.replace("\\s".toRegex(), "")?.take(4)?.lowercase() ?: "????"
        }

        val id = this.requestId.toString().takeLast(6)

        return "$direction.$savedAtString.$sender.$id"
    }
}
