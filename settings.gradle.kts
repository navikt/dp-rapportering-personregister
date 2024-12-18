rootProject.name = "dp-rapportering-personregister"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.9.0"
}
dependencyResolutionManagement {
    repositories {
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.dagpenger:dp-version-catalog:20241211.113.b9ad87")
        }
    }
}

include("mediator")
include("modell")
include("openapi")
