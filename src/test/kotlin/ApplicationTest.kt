package no.nav

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import no.nav.emottak.eventmanager.eventManagerModule
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application(eventManagerModule())
        client.get("/").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }
}
