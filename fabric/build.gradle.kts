// Fabric client mod for Minecraft 1.21.11 (Mojang mappings).
// Thin adapter layer: hooks Minecraft and forwards data to :core.
plugins {
    alias(libs.plugins.loom)
}

val modId = providers.gradleProperty("mod_id").get()

base {
    archivesName.set("$modId-fabric-${libs.versions.minecraft.get()}")
}

repositories {
    mavenCentral()
    // OneConfig API, compile-only: the integration is optional and loaded only by OneConfig.
    maven("https://repo.polyfrost.org/releases") {
        content { includeGroupAndSubgroups("org.polyfrost") }
    }
}

loom {
    // Mixins live in the client source set only; the mod is client-side.
    mixin {
        defaultRefmapName.set("$modId.refmap.json")
    }
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
fabricApi {
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
    minecraft("com.mojang:minecraft:${libs.versions.minecraft.get()}")
    mappings(loom.officialMojangMappings())
    modImplementation(libs.fabric.loader)
    modImplementation(libs.fabric.api)

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
    include(project(":core"))

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.processResources {
    val props =
        mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to libs.versions.minecraft.get(),
            "loader_version" to
                libs.versions.fabric.loader
                    .get(),
        )
    inputs.properties(props)
    filesMatching("fabric.mod.json") { expand(props) }
}
