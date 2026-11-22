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
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
        withSourcesJar()
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release.set(21)
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
            target("src/**/*.java")
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

spotless {
    kotlinGradle {
        target("*.gradle.kts", "*/*.gradle.kts")
        ktlint()
    }
}
