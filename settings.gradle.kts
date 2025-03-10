rootProject.name = "emottak-event-manager"

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            version("exposed", "0.47.0")
            version("ktor", "2.3.6")

            library("hikari", "com.zaxxer:HikariCP:5.0.1")

            library("flyway-core", "org.flywaydb:flyway-core:9.16.3")

            library("exposed-core", "org.jetbrains.exposed", "exposed-core").versionRef("exposed")
            library("exposed-dao", "org.jetbrains.exposed", "exposed-dao").versionRef("exposed")
            library("exposed-jdbc", "org.jetbrains.exposed", "exposed-jdbc").versionRef("exposed")

            library("content-negotiation", "io.ktor", "ktor-server-content-negotiation").versionRef("ktor")
            library("serialization-json", "io.ktor", "ktor-serialization-kotlinx-json").versionRef("ktor")
            library("micrometer", "io.ktor", "ktor-server-metrics-micrometer").versionRef("ktor")

            bundle("exposed", listOf("exposed-core", "exposed-dao", "exposed-jdbc"))
            bundle("ktor", listOf("content-negotiation", "serialization-json", "micrometer"))
        }
    }
}
