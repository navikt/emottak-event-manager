rootProject.name = "emottak-event-manager"

dependencyResolutionManagement {
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
}
