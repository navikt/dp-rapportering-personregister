plugins {
    kotlin("jvm")
    `java-library`
}

group = "no.nav.dapenger.rapportering.personregister"
version = "unspecified"

dependencies {
    implementation(libs.kotlin.logging)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)

    testImplementation("org.junit.platform:junit-platform-suite-engine:6.1.1")
    testImplementation("io.cucumber:cucumber-java8:7.34.4")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.34.4")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
