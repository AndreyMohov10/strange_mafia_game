plugins {
    kotlin("jvm") version "2.2.10"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(23)
}

tasks.test {
    useJUnitPlatform()
}

tasks.register("runServer") {
    dependsOn(":server:run")
}

tasks.register("runClient") {
    dependsOn(":telegram_client:run")
}