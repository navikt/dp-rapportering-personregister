import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.gradleup.shadow") version "8.3.9"
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
    implementation("no.nav.dagpenger:pdl-klient:2025.07.23-08.30.31e64aee9725")
    implementation("no.nav.dagpenger:oauth2-klient:2025.07.23-08.30.31e64aee9725")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics:${libs.versions.ktor.get()}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.18.1")
    implementation("io.opentelemetry:opentelemetry-api:1.52.0")
    implementation("io.getunleash:unleash-client-java:11.0.2")

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.13.4")
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation("org.testcontainers:kafka:1.21.3")
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
