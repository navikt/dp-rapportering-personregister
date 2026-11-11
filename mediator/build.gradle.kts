import com.github.davidmc24.gradle.plugin.avro.GenerateAvroProtocolTask
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    application
    id("com.gradleup.shadow") version "9.6.1"
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
    kotlin
}

group = "no.nav"
version = "unspecified"

val schema by configurations.creating {
    isTransitive = false
}

val paavegneavSchema by configurations.creating {
    isTransitive = false
}
val mainavroSchema by configurations.creating {
    isTransitive = false
}

dependencies {
    implementation(project(":modell"))
    implementation(project(":openapi"))

    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    implementation(libs.bundles.postgres)
    implementation(libs.bundles.jackson)
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation("no.nav.dagpenger:pdl-klient:2026.11.11-13.39.1535e94d2015")
    implementation("no.nav.dagpenger:oauth2-klient:2026.10.30-06.16.f1d0628bf128")
    implementation("io.ktor:ktor-server-netty:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-config-yaml:${libs.versions.ktor.get()}")
    implementation("io.ktor:ktor-server-metrics:${libs.versions.ktor.get()}")
    implementation("io.opentelemetry.instrumentation:opentelemetry-instrumentation-annotations:2.33.1")
    implementation("io.opentelemetry:opentelemetry-api:1.68.0")
    implementation("io.getunleash:unleash-client-java:12.3.2")
    implementation("com.github.navikt.tbd-libs:naisful-app:20261029.1843")

    api("io.confluent:kafka-avro-serializer:8.4.1")
    api("io.confluent:kafka-schema-registry:8.4.1")
    api("io.confluent:kafka-streams-avro-serde:8.4.1")
    api("org.apache.avro:avro:1.12.1")
    paavegneavSchema("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:1.26.05.04.35-1")
    mainavroSchema("no.nav.paw.arbeidssokerregisteret.api:main-avro-schema:1.13764081353.1-2")

    implementation("com.fasterxml.uuid:java-uuid-generator:5.2.0")

    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelsesmelding-schema:1.26.05.04.35-1")

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.postgres.test)
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)
    testImplementation(libs.mock.oauth2.server)
    testImplementation(libs.ktor.client.mock)
    testImplementation("org.junit.jupiter:junit-jupiter-params:6.1.3")
    testImplementation("io.ktor:ktor-server-test-host-jvm:${libs.versions.ktor.get()}")
    testImplementation(libs.rapids.and.rivers.test)
    testImplementation("org.testcontainers:kafka:1.21.4")
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("--add-opens", "java.base/java.time=ALL-UNNAMED")
}

tasks.named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
    source(zipTree(paavegneavSchema.singleFile))
    source(zipTree(mainavroSchema.singleFile))
}

kotlin {
    jvmToolchain(25)
}

application {
    mainClass.set("no.nav.dagpenger.rapportering.personregister.mediator.ApplicationKt")
}

tasks {
    withType<ShadowJar> {
        duplicatesStrategy = DuplicatesStrategy.INCLUDE
        mergeServiceFiles()
        isZip64 = true
    }
    named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
        source(zipTree(schema.singleFile))
    }
}
