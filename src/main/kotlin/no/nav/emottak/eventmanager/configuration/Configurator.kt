package no.nav.emottak.eventmanager.configuration

import com.sksamuel.hoplite.ConfigLoader
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource

fun config() = ConfigLoader.builder()
    .addEnvironmentSource()
    .addResourceSource("/application-personal.conf", optional = true)
    .addResourceSource("/application.conf")
    .withExplicitSealedTypes()
    .build()
    .loadConfigOrThrow<Config>()
