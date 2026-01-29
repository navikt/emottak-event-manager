package no.nav.emottak.eventmanager.utils

import no.nav.emottak.eventmanager.constants.Constants
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

fun Instant.toOsloZone(): ZonedDateTime = atZone(ZoneId.of(Constants.ZONE_ID_OSLO))
