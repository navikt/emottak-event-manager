@file:OptIn(ExperimentalUuidApi::class)

package no.nav.emottak.eventmanager

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.util.toLocalDateTime
import io.ktor.utils.io.InternalAPI
import io.micrometer.prometheus.PrometheusMeterRegistry
import no.nav.emottak.eventmanager.kafka.EventProducer
import no.nav.emottak.utils.events.model.Event
import no.nav.emottak.utils.events.model.EventType
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.toKotlinUuid

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("Hello World!")
        }
    }
}

@OptIn(InternalAPI::class, ExperimentalUuidApi::class)
fun Application.configureNaisRouts(collectorRegistry: PrometheusMeterRegistry, eventsService: EventsService) {
    routing {
        get("/internal/health/liveness") {
            call.respondText("I'm alive! :)")
        }
        get("/internal/health/readiness") {
            call.respondText("I'm ready! :)")
        }
        get("/internal/prometheus") {
            call.respond(collectorRegistry.scrape())
        }
        get("/fetchevents") {
            val fromDate = call.request.queryParameters.get("fromDate")
            val toDate = call.request.queryParameters.get("toDate")
            if (fromDate.isNullOrEmpty()) {
                log.info("Mangler parameter: from date")
                call.respond(HttpStatusCode.BadRequest)
            }
            val fom = SimpleDateFormat("yyyy-MM-dd HH:mm").parse(fromDate).toLocalDateTime()
            if (toDate.isNullOrEmpty()) {
                log.info("Mangler parameter: to date")
                call.respond(HttpStatusCode.BadRequest)
            }
            val tom = SimpleDateFormat("yyyy-MM-dd HH:mm").parse(toDate).toLocalDateTime()
            log.info("Henter hendelser fra events endepunktet til ebms ...")
            val events = eventsService.fetchevents(fom, tom)
            log.info("Antall hendelser fra endepunktet : ${events.size}")
            log.info("Henter siste hendelse : ${events.last()}")
            call.respond(events)
        }

        get("/kafkatest_write") {
            log.debug("Kafka test: start")

            val producer = EventProducer("team-emottak.common.topic.for.development")

            val testEvent = Event(
                eventType = EventType.MESSAGE_SAVED_IN_JURIDISK_LOGG,
                requestId = UUID.randomUUID().toKotlinUuid(),
                contentId = "test-content-id",
                messageId = "test-message-id",
                eventData = "{\"key\":\"value\"}"
            )
            var message = ""
            try {
                producer.send(
                    testEvent.requestId.toString(),
                    testEvent.toByteArray()
                )
                message = "Kafka test: Sent 5 messages to Kafka"
            } catch (e: Exception) {
                log.error("Kafka test: Exception while reading messages from queue", e)
                message = "Kafka test: Failed to send messages to Kafka: ${e.message}"
            }
            log.debug("Kafka test: done: $message")

            call.respondText(message)
        }
    }
}
