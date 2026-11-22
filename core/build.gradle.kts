// Minecraft-independent engine: replay format, recording lifecycle, storage, recovery.
// Must never depend on Minecraft, Fabric, or OneConfig classes, so it stays unit-testable.
plugins {
    `java-library`
}

repositories {
    mavenCentral()
}

dependencies {
    // Gson ships with Minecraft, so it is free at runtime inside the game.
    api(libs.gson)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.add("-Werror")
}
