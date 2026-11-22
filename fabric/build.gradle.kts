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
