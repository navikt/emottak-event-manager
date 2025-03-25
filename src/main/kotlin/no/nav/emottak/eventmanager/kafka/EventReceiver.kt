package no.nav.emottak.eventmanager.kafka

import io.github.nomisRev.kafka.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.eventmanager.config
import no.nav.emottak.eventmanager.configuration.toProperties
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.service.EventService
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay

suspend fun startEventReceiver(topic: String, eventService: EventService) {
    log.info("Starting event receiver on topic $topic")
    val receiverSettings: ReceiverSettings<String, ByteArray> =
        ReceiverSettings(
            bootstrapServers = config.kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = config.eventConsumer.consumerGroupId,
            autoOffsetReset = AutoOffsetReset.Earliest,
            pollTimeout = 10.seconds,
            properties = config.kafka.toProperties()
        )

    val receiver = KafkaReceiver(receiverSettings)
        .receive(topic)
        .map { record ->
            log.info("Processing record: $record")
            eventService.process(record.key(), record.value())
            record.offset.acknowledge()
        }

    while (true) {
        delay(10000)
        log.info("Collecting records")
        receiver.collect()
    }
}
