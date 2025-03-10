package no.nav.emottak.eventmanager

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
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
