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

// Renames between Minecraft versions, applied by Stonecutter when it generates a version's sources.
// Each rule is a regex pair (forward, then reverse) so switching the active version round-trips.
// Anything that is not a plain rename lives in //? blocks or in fabric/compat.
val sc = extensions.getByType<dev.kikugie.stonecutter.build.StonecutterBuildExtension>()
val is26 = sc.eval(sc.current.version, ">=26.1")
val is263 = sc.eval(sc.current.version, ">=26.3")

fun rename(
    from: String,
    to: String,
    reverseFrom: String,
    reverseTo: String,
    active: Boolean = is26,
) = sc.replacements.regex(active) { replace(from, to, reverseFrom, reverseTo) }

fun word(
    from: String,
    to: String,
    active: Boolean = is26,
) = rename("\\b$from\\b", to, "\\b$to\\b", from, active)

// Before 1.21.11 (1.21.10): older names of what 1.21.11 renamed.
val before12111 = sc.eval(sc.current.version, "<1.21.11")
// Not the "Identifier" in "SPDX-License-Identifier" headers.
rename("(?<!-)\\bIdentifier\\b", "ResourceLocation", "\\bResourceLocation\\b", "Identifier", before12111)
rename("\\bimport net\\.minecraft\\.util\\.Util;", "import net.minecraft.Util;", "\\bimport net\\.minecraft\\.Util;", "import net.minecraft.util.Util;", before12111)

// 26.1 renamed GuiGraphics and the render methods to "extract" its render state.
word("GuiGraphics", "GuiGraphicsExtractor")
rename(
    "public void render\\((?=GuiGraphics)",
    "public void extractRenderState(",
    "public void extractRenderState\\(",
    "public void render(",
)
rename("\\bsuper\\.render\\(", "super.extractRenderState(", "\\bsuper\\.extractRenderState\\(", "super.render(")
rename(
    "public void renderBackground\\(",
    "public void extractBackground(",
    "public void extractBackground\\(",
    "public void renderBackground(",
)
rename(
    "public void renderContent\\(",
    "public void extractContent(",
    "public void extractContent\\(",
    "public void renderContent(",
)
rename("\\.drawString\\(", ".text(", "\\.text\\(", ".drawString(")
rename("\\.drawCenteredString\\(", ".centeredText(", "\\.centeredText\\(", ".drawCenteredString(")
rename("\\.drawWordWrap\\(", ".textWithWordWrap(", "\\.textWithWordWrap\\(", ".drawWordWrap(")
rename("\\bg\\.renderItem\\(", "g.item(", "\\bg\\.item\\(", "g.renderItem(")
rename(
    "\\bg\\.renderItemDecorations\\(",
    "g.itemDecorations(",
    "\\bg\\.itemDecorations\\(",
    "g.renderItemDecorations(",
)
// Screens moved to Minecraft.gui.
rename(
    "(?<!\\.)\\b(mc|minecraft|client|Minecraft\\.getInstance\\(\\))\\.screen\\b(?!\\()",
    "$1.gui.screen()",
    "(?<!\\.)\\b(mc|minecraft|client|Minecraft\\.getInstance\\(\\))\\.gui\\.screen\\(\\)",
    "$1.screen",
)
rename(
    "(?<!\\.)\\b(mc|minecraft|client|Minecraft\\.getInstance\\(\\))\\.setScreen\\(",
    "$1.gui.setScreen(",
    "(?<!\\.)\\b(mc|minecraft|client|Minecraft\\.getInstance\\(\\))\\.gui\\.setScreen\\(",
    "$1.setScreen(",
)
// Fabric API renames.
rename("\\bScreens\\.getButtons\\(", "Screens.getWidgets(", "\\bScreens\\.getWidgets\\(", "Screens.getButtons(")
rename("\\.client\\.keybinding\\.v1\\.", ".client.keymapping.v1.", "\\.client\\.keymapping\\.v1\\.", ".client.keybinding.v1.")
word("KeyBindingHelper", "KeyMappingHelper")
word("registerKeyBinding", "registerKeyMapping")
word("ClientCommandManager", "ClientCommands")
// 26.3 moved input from GLFW to SDL: key codes are scancodes, read through InputConstants.
rename("\\bType\\.KEYSYM\\b", "Type.KEYBOARD", "\\bType\\.KEYBOARD\\b", "Type.KEYSYM", is263)
// Accessors that moved in 26.1.
rename(
    "\\b(mc|minecraft|client)\\.getToastManager\\(\\)",
    "$1.gui.toastManager()",
    "\\b(mc|minecraft|client)\\.gui\\.toastManager\\(\\)",
    "$1.getToastManager()",
)
rename(
    "\\.gui\\.setOverlayMessage\\(",
    ".gui.hud.setOverlayMessage(",
    "\\.gui\\.hud\\.setOverlayMessage\\(",
    ".gui.setOverlayMessage(",
)
rename(
    "\\bUtil\\.getPlatform\\(\\)\\.openPath\\(",
    "com.mojang.blaze3d.Blaze3D.openPath(",
    "\\bcom\\.mojang\\.blaze3d\\.Blaze3D\\.openPath\\(",
    "Util.getPlatform().openPath(",
    is263,
)
rename(
    "\\b(mc|minecraft|client)\\.getMainRenderTarget\\(\\)",
    "$1.gameRenderer.mainRenderTarget()",
    "\\b(mc|minecraft|client)\\.gameRenderer\\.mainRenderTarget\\(\\)",
    "$1.getMainRenderTarget()",
)
rename(
    "\\.gameRenderer\\.getMainCamera\\(\\)",
    ".gameRenderer.mainCamera()",
    "\\.gameRenderer\\.mainCamera\\(\\)",
    ".gameRenderer.getMainCamera()",
)
rename(
    "\\.resolve\\((\\w+)\\.getSoundManager\\(\\)\\)",
    ".getOrResolve($1.getSoundManager())",
    "\\.getOrResolve\\((\\w+)\\.getSoundManager\\(\\)\\)",
    ".resolve($1.getSoundManager())",
    is263,
)
rename(
    "(?<![\\w.])EntityType\\.([A-Z_]+)\\b",
    "net.minecraft.world.entity.EntityTypes.$1",
    "\\bnet\\.minecraft\\.world\\.entity\\.EntityTypes\\.([A-Z_]+)\\b",
    "EntityType.$1",
)
// Fabric's client gametest API: worlds are reached through their server connection.
rename(
    "\\bworld\\.getClientWorld\\(\\)",
    "world.getConnection()",
    "\\bworld\\.getConnection\\(\\)",
    "world.getClientWorld()",
)
rename(
    "\\bconnection\\.getClientWorld\\(\\)\\.",
    "connection.",
    "\\bconnection\\.(?=waitFor)",
    "connection.getClientWorld().",
)
rename(
    "\\bTestServerConnection connection = ",
    "TestDedicatedServerConnection connection = ",
    "\\bTestDedicatedServerConnection connection = ",
    "TestServerConnection connection = ",
)
rename(
    "\\.v1\\.context\\.TestServerConnection;",
    ".v1.context.TestDedicatedServerConnection;",
    "\\.v1\\.context\\.TestDedicatedServerConnection;",
    ".v1.context.TestServerConnection;",
)
