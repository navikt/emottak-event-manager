package no.nav.emottak.eventmanager.model.dto

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.emottak.eventmanager.model.EbmsMessageDetail
import no.nav.emottak.utils.common.toOsloZone

fun ReadableIdDto?.shouldBeEqualsTo(testDetails: EbmsMessageDetail) {
    this shouldNotBe null
    this!!.requestId shouldBe testDetails.requestId.toString()
    this.receivedDate shouldBe testDetails.savedAt.toOsloZone().toString()
    this.messageId shouldBe testDetails.messageId
    this.service shouldBe testDetails.service
    this.role shouldBe testDetails.fromRole
    this.action shouldBe testDetails.action
    this.referenceParameter shouldBe testDetails.refParam
    this.cpaId shouldBe testDetails.cpaId
    this.conversationId shouldBe testDetails.conversationId
    this.status shouldBe null // Not populated
}
