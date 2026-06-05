package no.nav.emottak.eventmanager.model

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail
import no.nav.emottak.utils.common.parseOrGenerateUuid

class EbmsMessageDetailSpec : DescribeSpec({

    describe("Tests of direction") {
/*
        it("Should return Direction OUT when fromPartyId is 'HER:79768'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "HER:79768")
            messageDetail.getDirection() shouldBe "OUT"
        }

        it("Should return Direction OUT when fromPartyId is 'ENH:889640782'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "ENH:889640782")
            messageDetail.getDirection() shouldBe "OUT"
        }
*/
        it("Should return Direction OUT when fromPartyId is 'orgnummer:990983291'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "orgnummer:990983291")
            messageDetail.getDirection() shouldBe "OUT"
        }
/*
        it("Should return Direction OUT when fromPartyId is 'commonname:ARBEIDS OG VELFERDSETATEN'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "commonname:ARBEIDS OG VELFERDSETATEN")
            messageDetail.getDirection() shouldBe "OUT"
        }
*/
        it("Should return Direction IN when fromPartyId is not representing Nav (none of the values in config().navPartyIds)") {
            val messageDetail = buildTestEbmsMessageDetail()
            messageDetail.getDirection() shouldBe "IN"
        }

        it("Should still return Direction IN when fromPartyId is similar but not identical to a value in config().navPartyIds") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "orgnummer:990983290")
            messageDetail.getDirection() shouldBe "IN"
        }
    }

    describe("Tests of readable id") {

        it("Should return a readable id starting on OUT. when fromPartyId is Nav") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "orgnummer:990983291")
            messageDetail.generateReadableId() shouldStartWith "OUT."
        }

        it("Should return a readable id starting on IN. when fromPartyId is NOT Nav") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "HER:12345")
            messageDetail.generateReadableId() shouldStartWith "IN."
        }

        it("Should return a readable id containing 'NAVM' when fromPartyId is Nav") {
            val messageDetail = buildTestEbmsMessageDetail().copy(fromPartyId = "orgnummer:990983291")
            messageDetail.generateReadableId() shouldContain ".NAVM."
        }

        it("Should return a readable id containing 'UNKN' when fromPartyId is not Nav and senderName is null") {
            val messageDetail = buildTestEbmsMessageDetail()
            messageDetail.generateReadableId() shouldContain ".UNKN."
        }

        it("Should return a readable id containing 'oslo' (lowercase) when fromPartyId is not Nav and senderName begins with 'OSLO'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(senderName = "OSLO KOMMUNE")
            messageDetail.generateReadableId() shouldContain ".oslo."
        }

        it("Should return a readable id containing 'NAVM' when senderName is 'Something but fromPartyId is Nav") {
            val messageDetail = buildTestEbmsMessageDetail().copy(
                senderName = "Something",
                fromPartyId = "orgnummer:990983291"
            )
            messageDetail.generateReadableId() shouldContain ".NAVM."
        }

        it("Should return a readable id containing 'olan' (lowercase stripped of spaces) when fromPartyId is not Nav and senderName is 'Ola Normann'") {
            val messageDetail = buildTestEbmsMessageDetail().copy(senderName = "Ola Normann")
            messageDetail.generateReadableId() shouldContain ".olan."
        }

        it("Should return a readable id ending on the requestId's last 6 characters") {
            val reqId = "931c5b8f-7781-4d18-b924-345fb86ecd52"
            val messageDetail = buildTestEbmsMessageDetail(requestId = reqId.parseOrGenerateUuid())
            messageDetail.generateReadableId() shouldEndWith "6ecd52"
        }
    }
})
