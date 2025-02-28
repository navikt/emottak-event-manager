package no.nav.emottak.eventmanager

import io.ktor.server.application.*
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import dev.reformator.stacktracedecoroutinator.runtime.DecoroutinatorRuntime
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing

fun main(args: Array<String>) {
    //if (getEnvVar("NAIS_CLUSTER_NAME", "local") != "prod-fss") {
        DecoroutinatorRuntime.load()
    //}
    embeddedServer(
        factory = Netty,
        port = 8080,
        module = eventManagerModule()
    ).start(wait = true)
}

fun eventManagerModule(): Application.() -> Unit {
    return {
        install(ContentNegotiation) { json() }
        routing {
            get("/") {
                call.respondText("Hello, World!")
            }
        }
    }
}
