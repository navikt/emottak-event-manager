package no.nav

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import no.nav.emottak.eventmanager.eventManagerModule

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application(eventManagerModule())
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}
