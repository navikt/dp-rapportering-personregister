rootProject.name = "dp-rapportering-personregister"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
dependencyResolutionManagement {
    repositories {
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.dagpenger:dp-version-catalog:20240920.93.abc4fd")
        }
    }
}

include("mediator")
include("modell")
include("openapi")
