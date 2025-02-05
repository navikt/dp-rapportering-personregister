plugins {
    kotlin("jvm")
    `java-library`
    id("com.github.davidmc24.gradle.plugin.avro") version "1.5.0"
}

group = "no.nav.dagpenger.rapportering.personregister"
version = "unspecified"

dependencies {
    api("io.confluent:kafka-avro-serializer:7.8.0")
    api("io.confluent:kafka-schema-registry:7.8.0")
    api("io.confluent:kafka-streams-avro-serde:7.8.0")
    api("org.apache.avro:avro:1.10.2")
    implementation(libs.rapids.and.rivers)
    implementation(libs.konfig)
    implementation(libs.kotlin.logging)

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

kotlin {
    jvmToolchain(21)
}
