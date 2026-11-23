plugins {
    alias(libs.plugins.loom) apply false
    alias(libs.plugins.spotless)
}

repositories {
    mavenCentral()
}

allprojects {
    group = providers.gradleProperty("maven_group").get()
    version = providers.gradleProperty("mod_version").get()
}

subprojects {
    // The Stonecutter controller (:fabric) only holds the per-version projects (:fabric:<version>).
    if (path == ":fabric") return@subprojects
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        // Java 21 for core and Minecraft 1.21.x, 25 for 26.x (java_version in fabric/versions/*).
        toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion(project)))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(javaVersion(project))
        options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing", "-Xlint:-serial"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        testLogging {
            events("failed", "skipped")
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        }
    }

    extensions.configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        java {
            // The fabric version projects all share fabric/src.
            target(if (path.startsWith(":fabric:")) "../../src/**/*.java" else "src/**/*.java")
            googleJavaFormat(
                rootProject.libs.versions.google.java.format
                    .get(),
            )
            removeUnusedImports()
            trimTrailingWhitespace()
            endWithNewline()
            licenseHeader(
                "/*\n" +
                    " * Copyright (C) \$YEAR Reizo contributors\n" +
                    " * SPDX-License-Identifier: GPL-3.0-or-later\n" +
                    " */\n",
            )
        }
    }
}

fun javaVersion(project: Project): Int = (project.findProperty("java_version") as String?)?.toInt() ?: 21

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
    }
}
