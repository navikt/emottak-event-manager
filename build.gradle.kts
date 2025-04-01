val kotlin_version: String by project
val logback_version: String by project
val logstash_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.1.1"
    kotlin("plugin.serialization") version "2.1.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
}

group = "no.nav.emottak"
version = "0.0.1"

application {
    mainClass = "no.nav.emottak.eventmanager.ApplicationKt"
}

tasks {
    shadowJar {
        archiveFileName.set("app.jar")
    }
    ktlintFormat {
        this.enabled = true
    }
    ktlintCheck {
        dependsOn("ktlintFormat")
    }
    build {
        dependsOn("ktlintCheck")
    }
    test {
        useJUnitPlatform()
    }
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    maven {
        name = "Emottak Utils"
        url = uri("https://maven.pkg.github.com/navikt/emottak-utils")
        credentials {
            username = "token"
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("no.nav.emottak:emottak-utils:0.1.0")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation(libs.bundles.ktor)
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation("com.bettercloud:vault-java-driver:5.1.0")
    implementation("no.nav:vault-jdbc:1.3.10")
    implementation(libs.bundles.hoplite)
    implementation("io.github.nomisrev:kotlin-kafka:0.4.1")
    implementation("com.sksamuel.hoplite:hoplite-core:2.8.2")
    implementation(libs.arrow.suspendapp)
    implementation(libs.arrow.suspendapp.ktor)
    implementation(libs.arrow.core)
    implementation(libs.arrow.fx.coroutines)

    testImplementation(kotlin("test"))
    testImplementation(testLibs.bundles.kotest)
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.testcontainers:postgresql:1.18.0")
    testImplementation("io.mockk:mockk:1.13.17")
}
