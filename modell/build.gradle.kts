plugins {
    kotlin("jvm")
    `java-library`
}

group = "no.nav.dapenger.rapportering.personregister"
version = "unspecified"

/*repositories {
    mavenCentral()
}*/

dependencies {
    implementation(libs.kotlin.logging)

    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)

    testApi("org.junit.platform:junit-platform-suite-api:6.0.0")
    testImplementation("org.junit.platform:junit-platform-suite-engine:6.0.0")
    testImplementation("io.cucumber:cucumber-java8:7.31.0")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.31.0")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
