val kotlin_version: String by project
val logback_version: String by project
val logstash_version: String by project

plugins {
    kotlin("jvm") version "2.1.10"
    id("io.ktor.plugin") version "3.1.1"
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
        url = uri("https://maven.pkg.github.com/navikt/ebxml-processor")
        credentials {
            username = "token"
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("no.nav.emottak:emottak-utils:0.0.7")
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_version")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation(libs.bundles.ktor)
    api("dev.reformator.stacktracedecoroutinator:stacktrace-decoroutinator-jvm:2.3.8")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.bundles.exposed)
    implementation("com.bettercloud:vault-java-driver:5.1.0")
    implementation("no.nav:vault-jdbc:1.3.10")
    implementation(libs.bundles.hoplite)

    testImplementation(kotlin("test"))
    testImplementation(testLibs.bundles.kotest)
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.testcontainers:postgresql:1.18.0")
}
