// Fabric client mod, built once per Minecraft version by Stonecutter (:fabric:<version>, ADR 0007).
// Every version shares fabric/src; differences are //? blocks. Thin adapter layer: hooks Minecraft
// and forwards data to :core. Names are Mojang's (mapped for 1.21.x, unobfuscated from 26.1).
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.fabricmc.loom.api.fabricapi.FabricApiExtension

plugins {
    java
}

val modId = providers.gradleProperty("mod_id").get()
val minecraftVersion = property("minecraft_version") as String
val remap = (property("loom_remap") as String).toBoolean()
val javaVersion = property("java_version") as String
val fabricApiVersion = property("fabric_api_version") as String

// Obfuscated versions need the remapping Loom; 26.x is unobfuscated.
apply(plugin = if (remap) "net.fabricmc.fabric-loom-remap" else "net.fabricmc.fabric-loom")
val loom = extensions.getByType<LoomGradleExtensionAPI>()
val modImplementation = if (remap) "modImplementation" else "implementation"

base {
    archivesName.set("$modId-fabric-$minecraftVersion")
}

repositories {
    mavenCentral()
    // OneConfig API, compile-only: the integration is optional and loaded only by OneConfig.
    maven("https://repo.polyfrost.org/releases") {
        content { includeGroupAndSubgroups("org.polyfrost") }
    }
}

loom.apply {
    runs {
        named("client") {
            client()
            configName = "Minecraft Client ($modId)"
            ideConfigGenerated(true)
            runDir("run")
            // Verbose logging for our own logger only; keeps vanilla logs readable.
            vmArgs("-D$modId.debug=true")
        }
        remove(findByName("server"))
    }
}

// Client gametests: a real Minecraft client creates a singleplayer world and records it.
// Source set: src/gametest. Run headless with: xvfb-run ./gradlew :fabric:runClientGameTest
extensions.configure<FabricApiExtension> {
    configureTests {
        createSourceSet.set(true)
        modId.set("$modId-gametest")
        enableGameTests.set(false)
        enableClientGameTests.set(true)
        eula.set(true)
    }
}

// CI runners render in software on few cores, and the gametest runs the integrated server in
// lockstep with client frames. A small window keeps frames cheap enough for the world to load.
loom.runs.matching { it.name == "clientGameTest" }.configureEach {
    programArgs("--width", "320", "--height", "240")
    // Playback feeds the client from a raw socket, so packets are not sent through a Connection
    // the gametest framework can count; its packet synchronizer would report a false error.
    vmArgs("-Dfabric.client.gametest.disableNetworkSynchronizer=true")
}

dependencies {
    "minecraft"("com.mojang:minecraft:$minecraftVersion")
    if (remap) {
        "mappings"(loom.officialMojangMappings())
    }
    modImplementation(libs.fabric.loader)
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // Optional OneConfig settings page (only its Minecraft-independent config API is needed).
    compileOnly("org.polyfrost.oneconfig:config:${libs.versions.oneconfig.get()}") { isTransitive = false }
    compileOnly("org.polyfrost.oneconfig:config-impl:${libs.versions.oneconfig.get()}") { isTransitive = false }
    // OneConfig HUD element (the recording indicator in OneConfig's HUD editor). Compile-only too:
    // these jars and the Kotlin standard library come with OneConfig at runtime.
    compileOnly("org.polyfrost.oneconfig:hud:${libs.versions.oneconfig.get()}") { isTransitive = false }
    compileOnly("org.polyfrost.oneconfig:ui:${libs.versions.oneconfig.get()}") { isTransitive = false }
    compileOnly("org.polyfrost.oneconfig:utils:${libs.versions.oneconfig.get()}") { isTransitive = false }
    // OneConfig notifications (Reizo's toasts and export progress inside OneClient).
    compileOnly("org.polyfrost.oneconfig:notifications:${libs.versions.oneconfig.get()}") { isTransitive = false }
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib:2.2.20") { isTransitive = false }

    implementation(project(":core"))
    "include"(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val props =
        mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to minecraftVersion,
            "java_version" to javaVersion,
            "loader_version" to
                libs.versions.fabric.loader
                    .get(),
        )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
