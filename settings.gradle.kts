rootProject.name = "emottak-event-manager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("exposed", "0.47.0")
            version("ktor", "2.3.6")
            version("hoplite", "2.8.2")
            version("suspendapp", "0.5.0")

            library("hikari", "com.zaxxer:HikariCP:5.0.1")

            library("flyway-core", "org.flywaydb:flyway-core:9.16.3")

            library("exposed-core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
            library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")
            library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")
            library("exposed-time", "org.jetbrains.exposed", "exposed-java-time").versionRef("exposed")
            library("exposed-json", "org.jetbrains.exposed", "exposed-json").versionRef("exposed")

            library("content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef("ktor")
            library("serialization-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")
            library("micrometer", "io.ktor", "ktor-server-metrics-micrometer").versionRef("ktor")

            library("hoplite-core", "com.sksamuel.hoplite", "hoplite-core").versionRef("hoplite")
            library("hoplite-hocon", "com.sksamuel.hoplite", "hoplite-hocon").versionRef("hoplite")

            library("arrow-suspendapp", "io.arrow-kt", "suspendapp").versionRef("suspendapp")
            library("arrow-suspendapp-ktor", "io.arrow-kt", "suspendapp-ktor").versionRef("suspendapp")

            bundle("exposed", listOf("exposed-core", "exposed-dao", "exposed-jdbc", "exposed-time", "exposed-json"))
            bundle("ktor", listOf("content-negotiation", "serialization-json", "micrometer"))
            bundle("hoplite", listOf("hoplite-core", "hoplite-hocon"))
        }

        create("testLibs") {
            version("kotest", "5.9.1")

            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core").versionRef("kotest")

            bundle("kotest", listOf("kotest-runner", "kotest-assertions"))
        }
    }
}
