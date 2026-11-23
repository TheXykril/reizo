pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // One fabric source tree built for every supported Minecraft version (ADR 0007).
    id("dev.kikugie.stonecutter") version "0.10"
}

rootProject.name = "reizo"

include("core")

stonecutter {
    create(":fabric") {
        // The OneClient versions are added one at a time (docs/ROADMAP.md, M8).
        versions("1.21.11", "26.3")
        vcsVersion = "1.21.11"
    }
}
