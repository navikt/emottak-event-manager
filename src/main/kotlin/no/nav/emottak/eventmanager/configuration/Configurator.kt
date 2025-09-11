package no.nav.emottak.eventmanager.configuration

import arrow.core.memoize
import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

val config: () -> Config = {
    ConfigLoader.builder()
        .addEnvironmentSource()
        .addResourceSource("/application-personal.conf", optional = true)
        .addResourceSource("/kafka_common.conf")
        .addResourceSource("/application.conf")
        .withExplicitSealedTypes()
        .build()
        .loadConfigOrThrow<Config>()
}.memoize()
