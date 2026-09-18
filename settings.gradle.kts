plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "strange_mafia_game"
include("domain")
include("telegram_client")
include("lm_studio")
include("server")