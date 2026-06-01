package no.nav.emottak.eventmanager.model

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import no.nav.emottak.eventmanager.constants.Constants.ACKNOWLEDGMENT_ACTION
import no.nav.emottak.eventmanager.constants.Constants.MESSAGEERROR_ACTION
import no.nav.emottak.eventmanager.constants.Constants.NOT_APPLICABLE_ROLE
import no.nav.emottak.eventmanager.constants.Constants.SENDER_NAME_NAV_MOTTAK
import no.nav.emottak.eventmanager.repository.buildTestEbmsMessageDetail

class EbmsMessageDetailDirectionSpec : StringSpec({

    "Should return Direction IN when ref_to_message_id is null" {
        val messageDetail = buildTestEbmsMessageDetail()
        messageDetail.getDirection() shouldBe "IN"
    }

    "Should return Direction IN when Acknowledgment and toRole is 'Not applicable'" {
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            toRole = NOT_APPLICABLE_ROLE,
            action = ACKNOWLEDGMENT_ACTION,
            senderName = "NAV"
        )
        messageDetail.getDirection() shouldBe "IN"
    }

    "Should return Direction OUT when Acknowledgment and toRole is not 'Not applicable'" {
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            action = ACKNOWLEDGMENT_ACTION,
            senderName = "OSLO KOMMUNE"
        )
        messageDetail.getDirection() shouldBe "OUT"
    }

    "Should return Direction IN when MessageError and toRole is 'Not applicable'" {
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            toRole = NOT_APPLICABLE_ROLE,
            action = MESSAGEERROR_ACTION,
            senderName = "NAV"
        )
        messageDetail.getDirection() shouldBe "IN"
    }

    "Should return Direction OUT when MessageError and toRole is not 'Not applicable'" {
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            action = MESSAGEERROR_ACTION,
            senderName = "OSLO KOMMUNE"
        )
        messageDetail.getDirection() shouldBe "OUT"
    }

    "Should return 'NAV Mottak' when direction IN" {
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            toRole = NOT_APPLICABLE_ROLE,
            action = MESSAGEERROR_ACTION,
            senderName = "NAV"
        )
        messageDetail.getReadableSenderName() shouldBe SENDER_NAME_NAV_MOTTAK
    }

    "Should return senderName when direction OUT" {
        val senderName = "OSLO KOMMUNE"
        val messageDetail = buildTestEbmsMessageDetail().copy(
            refToMessageId = "messageId-reference",
            action = MESSAGEERROR_ACTION,
            senderName = senderName
        )
        messageDetail.getReadableSenderName() shouldBe senderName
    }
})
