import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Target
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Generator
import groovy.json.JsonSlurper

plugins {
    kotlin("jvm") version "2.2.10"
    application
    kotlin("plugin.serialization") version "2.2.10"
    id("nu.studer.jooq") version "10.2"
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-client-cio:3.4.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
    implementation("io.ktor:ktor-server-core:3.5.0")
    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("io.ktor:ktor-server-websockets:3.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.6.0")
    implementation("org.jooq:jooq:3.19.18")
    implementation("org.postgresql:postgresql:42.7.7")

    jooqGenerator("org.postgresql:postgresql:42.7.7")
    jooqGenerator("org.jooq:jooq-meta:3.19.7")
    jooqGenerator("org.jooq:jooq-codegen:3.19.7")

    implementation(project(":domain"))
    implementation(project(":lm_studio"))
    testImplementation(kotlin("test"))
}


val sqlConfig = JsonSlurper().parse(File(".secret/sql_config.json")) as Map<*, *>

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

jooq {
    version.set("3.19.18")
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                logging = Logging.WARN

                jdbc = Jdbc().apply {
                    driver = "org.postgresql.Driver"
                    url = sqlConfig["url"].toString()
                    user = sqlConfig["user"].toString()
                    password = sqlConfig["password"].toString()
                }

                generator = Generator().apply {
                    name = "org.jooq.codegen.DefaultGenerator"

                    database = Database().apply {
                        name = "org.jooq.meta.postgres.PostgresDatabase"
                        inputSchema = "public"
                    }

                    generate = Generate().apply {
                        isDeprecated = false
                        isRecords = true
                        isImmutablePojos = false
                        isFluentSetters = true
                        isDaos = false
                        isPojos = true
                        isJavaTimeTypes = true
                    }

                    target = Target().apply {
                        packageName = "jooq"
                        directory = "build/generated/jooq"
                    }
                }
            }
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.main {
    java.srcDir("src/main")
    java.srcDir("build/generated/jooq")
}

application {
    mainClass.set("MainKt")
}

kotlin {
    jvmToolchain(23)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    dependsOn(tasks.named("generateJooq"))
}
