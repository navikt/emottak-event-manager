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

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().all {
    compilerOptions {
        freeCompilerArgs = listOf("-opt-in=kotlin.uuid.ExperimentalUuidApi,com.sksamuel.hoplite.ExperimentalHoplite,io.ktor.utils.io.InternalAPI")
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
    implementation(libs.logback)
    implementation(libs.logstash)
    implementation(libs.emottak.utils)
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.arrow)
    implementation(libs.bundles.exposed)
    implementation(libs.bundles.hoplite)
    implementation(libs.bundles.suspendapp)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.hikari)
    implementation(libs.flyway.core)
    implementation(libs.vault.jdbc)
    implementation(libs.kotlin.kafka)

    testImplementation(kotlin("test"))
    testImplementation(testLibs.mockk)
    testImplementation(testLibs.bundles.kotest)
    testImplementation(testLibs.ktor.server.test)
    testImplementation(testLibs.testcontainers.postgresql)
}
