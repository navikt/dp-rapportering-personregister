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
    testImplementation(kotlin("test"))
    testImplementation(libs.bundles.kotest.assertions)
    testImplementation(libs.mockk)

    testApi("org.junit.platform:junit-platform-suite-api:1.11.4")
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.11.4")
    testImplementation("io.cucumber:cucumber-java8:7.21.1")
    testImplementation("io.cucumber:cucumber-junit-platform-engine:7.21.1")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}
