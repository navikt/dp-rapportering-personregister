import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.gradleup.shadow") version "9.3.1"
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
    implementation("no.nav.dagpenger:pdl-klient:2025.12.19-08.15.2e150cd55270")
    implementation("no.nav.dagpenger:oauth2-klient:2025.12.19-08.15.2e150cd55270")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics:${libs.versions.ktor.get()}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.23.0")
    implementation("io.opentelemetry:opentelemetry-api:1.58.0")
    implementation("io.getunleash:unleash-client-java:12.1.0")
    implementation("com.github.navikt.tbd-libs:naisful-app:2025.11.04-10.54-c831038e")

    implementation("com.fasterxml.uuid:java-uuid-generator:5.1.0")

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.0.2")
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation("org.testcontainers:kafka:1.21.4")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
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
    duplicatesStrategy = DuplicatesStrategy.INCLUDE

    mergeServiceFiles()
}
