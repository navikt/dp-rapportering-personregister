import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin
}

group = "no.nav"
version = "unspecified"

dependencies {
    implementation(project(":kafka"))
    implementation(project(":modell"))
    implementation(project(":openapi"))

    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.postgres)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation("no.nav.dagpenger:oauth2-klient:2025.02.13-18.02.052b7c34baab")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics:${libs.versions.ktor.get()}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.14.0")
    implementation("io.opentelemetry:opentelemetry-api:1.48.0")
    implementation("io.getunleash:unleash-client-java:10.2.0")

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.12.1")
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation("org.testcontainers:kafka:1.20.6")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

/*tasks.named("build") {
    dependsOn(":kafka:build")
}*/

application {
    mainClass.set("no.nav.dagpenger.rapportering.personregister.mediator.ApplicationKt")
}

tasks.withType<ShadowJar> {
    mergeServiceFiles()
}
