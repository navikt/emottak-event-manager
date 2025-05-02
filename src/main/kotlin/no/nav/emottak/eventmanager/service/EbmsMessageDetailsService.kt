package no.nav.emottak.eventmanager.service

import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails

class EbmsMessageDetailsService(private val ebmsMessageDetailsRepository: EbmsMessageDetailsRepository) {
    suspend fun process(value: ByteArray) {
        try {
            log.info("EBMS message details read from Kafka: ${String(value)}")

            val details: EbmsMessageDetails = Json.decodeFromString(String(value))
            ebmsMessageDetailsRepository.insert(details)
            log.info("EBMS message details processed successfully: $details")
        } catch (e: Exception) {
            log.error("Exception while processing EBMS message details:${String(value)}", e)
        }
    }
}
