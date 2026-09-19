import org.jooq.meta.jaxb.Logging
import org.jooq.meta.jaxb.Target
import org.jooq.meta.jaxb.Generate
import org.jooq.meta.jaxb.Database
import org.jooq.meta.jaxb.Jdbc
import org.jooq.meta.jaxb.Generator
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jooq)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
    application
    alias(libs.plugins.kotlin.serialization)
    id("java")
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.kotlin.stdlib)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.websocket)
    implementation(libs.spring.boot.starter.jooq)
    implementation(libs.postgresql)
    implementation(libs.jooq)
    jooqGenerator(libs.postgresql)

    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.serialization.core)

    implementation(project(":domain"))
    implementation(project(":lm_studio"))
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.kotlin.test)
}

val envFile = rootProject.file(".env")
val envProps = Properties()
if (envFile.exists()) {
    envFile.inputStream().use { envProps.load(it) }
}
fun getEnv(key: String, fallback: String): String =
    System.getenv(key) ?: envProps.getProperty(key, fallback)

val dbUrl = getEnv("DB_URL", getEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost:5433/mafia_game"))
val dbUser = getEnv("DB_USER", getEnv("SPRING_DATASOURCE_USERNAME", "postgres"))
val dbPassword = getEnv("DB_PASSWORD", getEnv("SPRING_DATASOURCE_PASSWORD", "1234"))

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

jooq {
    version.set(libs.versions.jooq)
    edition.set(nu.studer.gradle.jooq.JooqEdition.OSS)

    configurations {
        create("main") {
            generateSchemaSourceOnCompilation.set(true)

            jooqConfiguration.apply {
                logging = Logging.WARN

                jdbc = Jdbc().apply {
                    driver = "org.postgresql.Driver"
                    url = dbUrl
                    user = dbUser
                    password = dbPassword
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
    mainClass.set("game.server.ServerApplicationKt")
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

tasks.withType<org.springframework.boot.gradle.tasks.run.BootRun> {
    if (envFile.exists()) {
        envProps.stringPropertyNames().forEach { key ->
            environment(key, envProps.getProperty(key))
        }
    }
}
