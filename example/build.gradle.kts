plugins {
    kotlin("jvm") version "2.0.20"
}

group = "io.xconn"
version = "unspecified"

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":xconn"))
    implementation("io.xconn:wampproto:0.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
