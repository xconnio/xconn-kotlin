plugins {
    kotlin("jvm") version "2.0.20"
}

group = "io.xconn"
version = "0.1.0-alpha.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.xconn:wampproto:0.1.0")
    implementation("io.ktor:ktor-client-websockets:2.3.12")
    implementation("io.ktor:ktor-client-cio:2.3.12")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
