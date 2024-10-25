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
            from("no.nav.dagpenger:dp-version-catalog:20241024.96.25c0c3")
        }
    }
}

include("mediator")
include("modell")
include("openapi")
