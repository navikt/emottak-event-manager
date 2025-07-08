package no.nav.emottak.eventmanager.kafka

import io.github.nomisRev.kafka.receiver.AutoOffsetReset
import io.github.nomisRev.kafka.receiver.KafkaReceiver
import io.github.nomisRev.kafka.receiver.ReceiverSettings
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import no.nav.emottak.eventmanager.config
import no.nav.emottak.eventmanager.configuration.toProperties
import no.nav.emottak.eventmanager.log
import no.nav.emottak.eventmanager.service.EbmsMessageDetailService
import no.nav.emottak.eventmanager.service.EventService
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import kotlin.time.Duration.Companion.seconds

suspend fun startEventReceiver(
    topics: List<String>,
    eventService: EventService,
    ebmsMessageDetailService: EbmsMessageDetailService
) {
    log.info("Starting event receiver on topics $topics")
    val receiverSettings: ReceiverSettings<String?, ByteArray> =
        ReceiverSettings(
            bootstrapServers = config.kafka.bootstrapServers,
            keyDeserializer = StringDeserializer(),
            valueDeserializer = ByteArrayDeserializer(),
            groupId = config.eventConsumer.consumerGroupId,
            autoOffsetReset = AutoOffsetReset.Latest,
            pollTimeout = 10.seconds,
            properties = config.kafka.toProperties()
        )

    KafkaReceiver(receiverSettings)
        .receive(topics)
        .map { record ->
            log.debug("Processing record: {}", record)
            when (record.topic()) {
                config.eventConsumer.eventTopic -> eventService.process(record.value())
                config.eventConsumer.messageDetailsTopic -> ebmsMessageDetailService.process(record.value())
                else -> log.warn("Record received from an unknown topic ${record.topic()}: ${record.value()}")
            }
            record.offset.acknowledge()
        }.collect()
}
