import com.github.davidmc24.gradle.plugin.avro.GenerateAvroProtocolTask

plugins {
    kotlin("jvm")
    `java-library`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
}

group = "no.nav.dagpenger.rapportering.personregister"
version = "unspecified"

val schema by configurations.creating {
    isTransitive = false
}

dependencies {
    api("io.confluent:kafka-avro-serializer:7.8.1")
    api("io.confluent:kafka-schema-registry:7.8.1")
    api("io.confluent:kafka-streams-avro-serde:7.8.1")
    api("org.apache.avro:avro:1.12.0")
    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)
    schema("no.nav.paw.arbeidssokerregisteret.api:bekreftelse-paavegneav-schema:25.02.07.15-1")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

tasks.named("generateAvroProtocol", GenerateAvroProtocolTask::class.java) {
    source(zipTree(schema.singleFile))
}

kotlin {
    jvmToolchain(21)
}
