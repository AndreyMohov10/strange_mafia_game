import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    application
    alias(libs.plugins.kotlin.serialization)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.telegrambots.client)
    implementation(libs.telegrambots.longpolling)
    implementation(project(":domain"))
    testImplementation(libs.kotlin.test)
    testImplementation(libs.spring.boot.starter.test)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("game.client.ClientApplicationKt")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(23)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        javaParameters.set(true)
    }
}

val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) {
    envFile.inputStream().use { envProps.load(it) }
}

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    if (envFile.exists()) {
        envProps.stringPropertyNames().forEach { key ->
            environment(key, envProps.getProperty(key))
        }
    }
}
