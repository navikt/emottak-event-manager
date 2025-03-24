package no.nav.emottak.eventmanager.kafka

import io.github.nomisRev.kafka.Acks
import io.github.nomisRev.kafka.ProducerSettings
import io.github.nomisRev.kafka.kafkaProducer
import kotlinx.coroutines.flow.Flow
import no.nav.emottak.eventmanager.config
import no.nav.emottak.eventmanager.configuration.toProperties
import no.nav.emottak.eventmanager.log
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer

class EventProducer(private val topic: String) {
    private var producersFlow: Flow<KafkaProducer<String, ByteArray>>

    init {
        val producerSettings = ProducerSettings(
            bootstrapServers = config.kafka.bootstrapServers,
            keyDeserializer = StringSerializer(),
            valueDeserializer = ByteArraySerializer(),
            acks = Acks.All,
            other = config.kafka.toProperties()
        )
        producersFlow = kafkaProducer(producerSettings)
    }

    suspend fun send(key: String, value: ByteArray) {
        try {
            producersFlow.collect { producer ->
                val record = ProducerRecord(topic, key, value)
                producer.send(record).get()
            }
            log.info("Message sent successfully to topic $topic")
        } catch (e: Exception) {
            log.error("Failed to send message: ${e.message}", e)
        }
    }
}
