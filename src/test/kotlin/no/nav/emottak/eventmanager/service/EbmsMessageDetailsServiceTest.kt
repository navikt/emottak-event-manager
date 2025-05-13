package no.nav.emottak.eventmanager.service

import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.serialization.json.Json
import no.nav.emottak.eventmanager.persistence.repository.EbmsMessageDetailsRepository
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetails
import no.nav.emottak.utils.kafka.model.EbmsMessageDetails

class EbmsMessageDetailsServiceTest : StringSpec({

    val ebmsMessageDetailsRepository = mockk<EbmsMessageDetailsRepository>()
    val ebmsMessageDetailsService = EbmsMessageDetailsService(ebmsMessageDetailsRepository)

    "Should call database repository on processing EBMS message details" {

        val testDetails = buildTestEbmsMessageDetails()
        val testDetailsJson = Json.encodeToString(EbmsMessageDetails.serializer(), testDetails)

        coEvery { ebmsMessageDetailsRepository.insert(testDetails) } returns testDetails.requestId

        ebmsMessageDetailsService.process(testDetailsJson.toByteArray())

        coVerify { ebmsMessageDetailsRepository.insert(testDetails) }
    }
})
