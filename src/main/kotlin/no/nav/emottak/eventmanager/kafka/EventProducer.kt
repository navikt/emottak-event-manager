package no.nav.emottak.eventmanager.kafka

import io.github.nomisRev.kafka.publisher.KafkaPublisher
import io.github.nomisRev.kafka.publisher.PublisherSettings
import no.nav.emottak.eventmanager.config
import no.nav.emottak.eventmanager.configuration.toProperties
import no.nav.emottak.eventmanager.log
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

class EventProducer(private val topic: String) {
    private var publisher: KafkaPublisher<String, ByteArray>

    init {
        val producerSettings = PublisherSettings<String, ByteArray>(
            config.kafka.bootstrapServers,
            StringSerializer(),
            ByteArraySerializer(),
            properties = config.kafka.toProperties()
        )
        publisher = KafkaPublisher(producerSettings)
    }

    suspend fun send(key: String, value: ByteArray) {
        val record = ProducerRecord(topic, key, value)
        publisher.publishScope {
            publishCatching(record)
        }
            .onSuccess { log.info("Event is published to: $topic") }
            .onFailure { log.error("Failed to publish event to: $topic ${it.message}") }
    }
}
