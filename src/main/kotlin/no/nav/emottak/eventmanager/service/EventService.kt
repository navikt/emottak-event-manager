package no.nav.emottak.eventmanager.service

import no.nav.emottak.eventmanager.log

class EventService {
    suspend fun process(key: String, value: ByteArray) {
        try {
            log.info("Kafka test: Message received key:$key, value:${String(value)}")
        } catch (e: Exception) {
            log.error("Kafka test: Exception while receiving messages from queue", e)
        }
    }
}
