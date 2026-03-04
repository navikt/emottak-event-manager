rootProject.name = "emottak-event-manager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("exposed", "0.47.0")
            version("ktor", "3.3.1")
            version("hoplite", "2.8.2")
            version("suspendapp", "0.5.0")
            version("arrow", "2.0.1")
            version("emottak-utils", "0.3.5")
            version("kotlin-kafka", "0.4.1")
            version("logback", "1.5.18")
            version("logstash", "8.0")
            version("prometheus", "1.12.4")
            version("vault-jdbc", "1.3.10")
            version("token-validation-ktor", "5.0.30")

            library("hikari", "com.zaxxer:HikariCP:5.0.1")

            library("flyway-core", "org.flywaydb:flyway-core:9.16.3")

            library("exposed-core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
            library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")
            library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")
            library("exposed-time", "org.jetbrains.exposed", "exposed-java-time").versionRef("exposed")
            library("exposed-json", "org.jetbrains.exposed", "exposed-json").versionRef("exposed")

            library("server-content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef("ktor")
            library("client-content-negotiation", "io.ktor", "ktor-client-content-negotiation").versionRef("ktor")
            library("serialization-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")
            library("server-config-yaml", "io.ktor", "ktor-server-config-yaml").versionRef("ktor")
            library("micrometer", "io.ktor", "ktor-server-metrics-micrometer").versionRef("ktor")
            library("server-core", "io.ktor", "ktor-server-core").versionRef("ktor")
            library("server-netty", "io.ktor", "ktor-server-netty").versionRef("ktor")

            library("kotlin-kafka", "io.github.nomisrev", "kotlin-kafka").versionRef("kotlin-kafka")

            library("micrometer-registry-prometheus", "io.micrometer", "micrometer-registry-prometheus").versionRef("prometheus")

            library("hoplite-core", "com.sksamuel.hoplite", "hoplite-core").versionRef("hoplite")
            library("hoplite-hocon", "com.sksamuel.hoplite", "hoplite-hocon").versionRef("hoplite")

            library("arrow-core", "io.arrow-kt", "arrow-core").versionRef("arrow")
            library("arrow-fx-coroutines", "io.arrow-kt", "arrow-fx-coroutines").versionRef("arrow")
            library("arrow-functions", "io.arrow-kt", "arrow-functions").versionRef("arrow")

            library("arrow-suspendapp", "io.arrow-kt", "suspendapp").versionRef("suspendapp")
            library("arrow-suspendapp-ktor", "io.arrow-kt", "suspendapp-ktor").versionRef("suspendapp")

            library("emottak-utils", "no.nav.emottak", "emottak-utils").versionRef("emottak-utils")

            library("logback", "ch.qos.logback", "logback-classic").versionRef("logback")
            library("logstash", "net.logstash.logback", "logstash-logback-encoder").versionRef("logstash")

            library("vault-jdbc", "no.nav", "vault-jdbc").versionRef("vault-jdbc")

            library("token-validation-ktor-v3", "no.nav.security", "token-validation-ktor-v3").versionRef("token-validation-ktor")

            bundle("suspendapp", listOf("arrow-suspendapp", "arrow-suspendapp-ktor"))
            bundle("exposed", listOf("exposed-core", "exposed-dao", "exposed-jdbc", "exposed-time", "exposed-json"))
            bundle("ktor", listOf("server-content-negotiation", "client-content-negotiation", "serialization-json", "micrometer", "server-core", "server-netty", "server-config-yaml"))
            bundle("hoplite", listOf("hoplite-core", "hoplite-hocon"))
            bundle("arrow", listOf("arrow-core", "arrow-fx-coroutines", "arrow-functions"))
        }

        create("testLibs") {
            version("kotest", "5.9.1")
            version("ktor-test", "1.1.5")
            version("testcontainers", "1.18.1")
            version("mockk", "1.13.17")

            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core").versionRef("kotest")

            library("ktor-server-test", "io.ktor", "ktor-server-test-host").versionRef("ktor-test")

            library("testcontainers-postgresql", "org.testcontainers", "postgresql").versionRef("testcontainers")

            library("mockk", "io.mockk", "mockk").versionRef("mockk")

            library("mock-oauth2-server", "no.nav.security:mock-oauth2-server:2.1.2")

            bundle("kotest", listOf("kotest-runner", "kotest-assertions"))
        }
    }
}
