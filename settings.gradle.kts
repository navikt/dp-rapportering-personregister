rootProject.name = "dp-rapportering-personregister"

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://github-package-registry-mirror.gc.nav.no/cached/maven-release")
        maven("https://packages.confluent.io/maven/")
    }
    versionCatalogs {
        create("libs") {
            from("no.nav.dagpenger:dp-version-catalog:20250430.162.96bfbf")
        }
    }
}

include("mediator")
include("modell")
include("openapi")
include("kafka")
