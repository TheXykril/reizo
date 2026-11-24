/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import org.polyfrost.oneconfig.api.ui.v1.components.SegmentedChips
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thexykril.reizo.core.effects.Grade
import io.github.thexykril.reizo.core.timeline.KeyframeEdits
import io.github.thexykril.reizo.fabric.compat.Compat
import io.github.thexykril.reizo.fabric.playback.EditorViewport
import io.github.thexykril.reizo.fabric.playback.ReplayEditor
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

/**
 * The replay editor on OneConfig's Compose UI (ADR 0017), first slice: a top bar, a camera
 * inspector, the transport and a timeline with the ruler, the video clips, camera keyframes,
 * markers and the playhead. The world stays visible in the middle. Opened only with
 * `-Dreizo.composeTimeline=true` until it matches [TimelineScreen].
 */
class ComposeTimelineScreen(private val parent: Screen?) :
    OneConfigComposeScreen(Component.translatable("reizo.timeline.title"), parent),
    EditorView {

    override val blurBackground: Boolean get() = false
    override val panoramaBackground: Boolean get() = false
    override val dimBackground: Boolean get() = false
    override val pausesGame: Boolean get() = false

    private fun t(key: String, vararg args: Any): String = Component.translatable(key, *args).string

    @Composable
    override fun Content() {
        val server = ReplayPlayback.current()
        if (server == null) {
            dismiss()
            return
        }
        // The world shows in a box, so the HUD (sized for the whole window) stays hidden meanwhile.
        if (hudHidden == null) {
            val mc = Minecraft.getInstance()
            hudHidden = Compat.hudHidden(mc)
            Compat.setHudHidden(mc, true)
        }
        // Replay time moves on its own; read it every frame.
        var now by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis { now = server.replayTimeMs() }
            }
        }
        fun edited() {
            ReplayEditor.changed()
            version++
        }
        version.let { }

        BoxWithConstraints(Modifier.fillMaxSize()) {
        // The timeline takes about a third of the height, more on small windows.
        val wide = maxWidth >= 560.dp
        val rootW = constraints.maxWidth.toFloat()
        val rootH = constraints.maxHeight.toFloat()
        val backdrop = OneConfigTheme.current.pageBackground.toArgb()
        if (!wide) EditorViewport.clear()
        // Slim lanes like a video editor; the transport moves into the panel on small windows.
        val timelineHeight = if (wide) RULER + LANE * 2 + 36.dp else (maxHeight * 0.55f)
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopBar()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier.weight(1f).fillMaxHeight().onGloballyPositioned {
                        if (wide) frameWorld(it.boundsInRoot(), rootW, rootH, backdrop) else EditorViewport.clear()
                    },
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // Over the world, like a player's controls.
                    if (wide) {
                        Box(Modifier.themedSurface(SurfaceKind.Panel).padding(horizontal = 10.dp, vertical = 4.dp)) {
                            Transport(maxOf(0L, toVideo(now)), videoDuration(), server.clock().isRunning, server.clock().speed(), pill = true)
                        }
                    }
                }
                if (wide) Inspector(now, ::edited)
            }
            Column(
                Modifier.fillMaxWidth().height(timelineHeight).themedSurface(SurfaceKind.Panel).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!wide) Transport(maxOf(0L, toVideo(now)), videoDuration(), server.clock().isRunning, server.clock().speed())
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TrackLabels(Modifier.width(56.dp).fillMaxHeight())
                    Timeline(now, ::edited, Modifier.weight(1f).fillMaxHeight())
                }
            }
        }
        }
    }

    @Composable
    private fun TopBar() {
        val theme = OneConfigTheme.current
        Row(
            Modifier.fillMaxWidth().themedSurface(SurfaceKind.Panel).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Reizo", style = OneConfigTheme.typography.titleSmall)
            Text(
                ReplayPlayback.metadata()?.name ?: "",
                Modifier.weight(1f),
                color = theme.textColorSecondary,
                style = OneConfigTheme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(t("reizo.timeline.undo_short"), { ReplayEditor.undo(); version++ }, style = ControlStyle.Ghost, enabled = ReplayEditor.canUndo())
            TextButton(t("reizo.timeline.redo_short"), { ReplayEditor.redo(); version++ }, style = ControlStyle.Ghost, enabled = ReplayEditor.canRedo())
            TextButton(
                t("reizo.export.title"),
                { Minecraft.getInstance().setScreen(ExportSettingsScreen.create(this@ComposeTimelineScreen)) },
                style = ControlStyle.Primary,
            )
            TextButton(t("gui.done"), { dismiss() }, style = ControlStyle.Secondary)
        }
    }

    private enum class Tab { PROPERTIES, EXPORT }

    private var tab by mutableStateOf(Tab.PROPERTIES)

    /** Groups the user folded away, by name. */
    private val folded = mutableStateMapOf<String, Boolean>()

    /** The right panel: tabs, then property groups that each fold and keyframe on their own. */
    @Composable
    private fun Inspector(now: Long, edited: () -> Unit) {
        Column(
            Modifier.width(300.dp).fillMaxHeight().themedSurface(SurfaceKind.Panel).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SegmentedChips(
                Tab.values().toList(),
                tab,
                { tab = it },
                Modifier.fillMaxWidth(),
                { if (it == Tab.PROPERTIES) t("reizo.timeline.properties") else t("reizo.timeline.export_tab") },
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (tab) {
                    Tab.PROPERTIES -> {
                        CameraGroup(now, edited)
                        LookGroup()
                    }
                    Tab.EXPORT -> ExportTab()
                }
            }
        }
    }

    /** A group header: fold chevron, title, and a keyframe diamond (filled when one is here). */
    @Composable
    private fun GroupHeader(name: String, title: String, keyed: Boolean, onKey: () -> Unit) {
        val open = folded[name] != true
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            TextButton(if (open) "▾" else "▸", { folded[name] = open }, style = ControlStyle.Ghost)
            Text(title, Modifier.weight(1f), style = OneConfigTheme.typography.bodyMedium)
            KeyDiamond(keyed, onKey)
        }
    }

    /** A keyframe button drawn as a diamond: outlined when no keyframe is at the playhead. */
    @Composable
    private fun KeyDiamond(keyed: Boolean, onClick: () -> Unit) {
        val accent = OneConfigTheme.accent
        val dim = OneConfigTheme.current.textColorSecondary
        Canvas(Modifier.size(22.dp).pointerInput(Unit) { detectTapGestures { onClick() } }) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = 6.dp.toPx()
            if (keyed) {
                diamond(c, r, accent)
            } else {
                val p = Path().apply {
                    moveTo(c.x, c.y - r)
                    lineTo(c.x + r, c.y)
                    lineTo(c.x, c.y + r)
                    lineTo(c.x - r, c.y)
                    close()
                }
                drawPath(p, dim, style = Stroke(1.5f.dp.toPx()))
            }
        }
    }

    @Composable
    private fun CameraGroup(now: Long, edited: () -> Unit) {
        val key = ReplayEditor.nearest(now, 50)
        GroupHeader("camera", t("reizo.timeline.camera"), key != null) {
            if (key != null) ReplayEditor.removeNearestKeyframe() else ReplayEditor.addKeyframe(Minecraft.getInstance())
            edited()
        }
        if (folded["camera"] == true) return
        val cam = key ?: ReplayEditor.cameraNow()
        if (cam != null) {
            CameraValues(cam.x(), cam.y(), cam.z(), cam.yaw(), cam.pitch(), cam.roll(), cam.fov())
        } else {
            // No keyframe here: show where the camera is now.
            val mc = Minecraft.getInstance()
            val e = mc.cameraEntity
            if (e != null) {
                val roll = ReplayEditor.rollNow().let { if (it.isNaN()) 0f else it }
                val fov = ReplayEditor.fovNow().let { if (it.isNaN()) mc.options.fov().get().toFloat() else it }
                CameraValues(e.x, e.y, e.z, e.yRot, e.xRot, roll, fov)
            }
        }
        Text(
            ReplayEditor.path().keyframes().size.toString() + " " + t("reizo.timeline.keyframes"),
            color = OneConfigTheme.current.textColorSecondary,
            style = OneConfigTheme.typography.caption,
        )
    }

    /** The look (effects) at the playhead; moving a slider keys it here. */
    @Composable
    private fun LookGroup() {
        GroupHeader("look", t("reizo.timeline.effects"), EffectEdits.atKeyframe()) {
            if (!EffectEdits.removeHere()) EffectEdits.edit { it }
            version++
        }
        if (folded["look"] == true) return
        val g = ReplayEditor.gradeNow()
        LookSlider("reizo.look.exposure", g.exposure(), -3f, 3f) { v -> EffectEdits.edit { it.withExposure(v) } }
        LookSlider("reizo.look.contrast", g.contrast(), 0f, 2f) { v -> EffectEdits.edit { it.withContrast(v) } }
        LookSlider("reizo.look.saturation", g.saturation(), 0f, 2f) { v -> EffectEdits.edit { it.withSaturation(v) } }
        LookSlider("reizo.look.temperature", g.temperature(), -1f, 1f) { v -> EffectEdits.edit { it.withTemperature(v) } }
        LookSlider("reizo.look.vignette", g.vignette(), 0f, 1f) { v -> EffectEdits.edit { it.withVignette(v) } }
        LookSlider("reizo.look.bloom", g.bloom(), 0f, 1f) { v -> EffectEdits.edit { it.withBloom(v) } }
        LookSlider("reizo.look.dof", g.dof(), 0f, 1f) { v -> EffectEdits.edit { it.withDof(v) } }
        LookSlider("reizo.look.focus_short", g.focus(), 0f, 64f, auto = true) { v -> EffectEdits.edit { it.withFocus(v) } }
    }

    @Composable
    private fun LookSlider(key: String, value: Float, min: Float, max: Float, auto: Boolean = false, set: (Float) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t(key), Modifier.width(84.dp), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.typography.small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Slider(value, { set(it); version++ }, Modifier.weight(1f), min..max)
            Text(
                if (auto && value == Grade.AUTO_FOCUS) t("reizo.look.auto") else "%.2f".format(value),
                Modifier.width(36.dp),
                style = OneConfigTheme.typography.small,
            )
        }
    }

    @Composable
    private fun ExportTab() {
        Text(
            t("reizo.timeline.export_hint"),
            color = OneConfigTheme.current.textColorSecondary,
            style = OneConfigTheme.typography.small,
        )
        TextButton(
            t("reizo.export.title"),
            { Minecraft.getInstance().setScreen(ExportSettingsScreen.create(this@ComposeTimelineScreen)) },
            Modifier.fillMaxWidth(),
            style = ControlStyle.Primary,
            fillWidth = true,
        )
    }

    @Composable
    private fun CameraValues(x: Double, y: Double, z: Double, yaw: Float, pitch: Float, roll: Float, fov: Float) {
        Value("X", "%.2f".format(x))
        Value("Y", "%.2f".format(y))
        Value("Z", "%.2f".format(z))
        Value(t("reizo.timeline.yaw"), "%.1f°".format(yaw))
        Value(t("reizo.timeline.pitch"), "%.1f°".format(pitch))
        Value(t("reizo.timeline.roll"), "%.1f°".format(roll))
        Value("FOV", "%.0f".format(fov))
    }

    @Composable
    private fun Value(label: String, value: String) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, Modifier.weight(1f), color = OneConfigTheme.current.textColorSecondary, style = OneConfigTheme.typography.small)
            Text(value, style = OneConfigTheme.typography.small)
        }
    }

    @Composable
    private fun Transport(now: Long, duration: Long, running: Boolean, speed: Double, pill: Boolean = false) {
        val theme = OneConfigTheme.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(if (running) "❚❚" else "▶", { ReplayPlayback.togglePause() }, style = ControlStyle.Secondary)
            Text(ReplayHud.time(now), style = OneConfigTheme.typography.bodyMedium)
            Text("/ " + ReplayHud.time(duration), color = theme.textColorSecondary, style = OneConfigTheme.typography.small)
            Spacer(if (pill) Modifier.width(12.dp) else Modifier.weight(1f))
            TextButton(t("reizo.timeline.split_short"), { splitAt(now) }, style = ControlStyle.Ghost)
            TextButton(
                t("reizo.timeline.delete_clip"),
                { deleteClip() },
                style = ControlStyle.Ghost,
                enabled = selectedClip >= 0 && ReplayEditor.sequence().size() > 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(ReplayHud.speed(speed) + "×", color = theme.textColorSecondary, style = OneConfigTheme.typography.small)
            TextButton("−", { ReplayPlayback.stepSpeed(-1) }, style = ControlStyle.Ghost)
            TextButton("+", { ReplayPlayback.stepSpeed(1) }, style = ControlStyle.Ghost)
        }
    }

    /** Splits the clip under the playhead (video time). */
    private fun splitAt(videoMs: Long) {
        if (ReplayEditor.editClips({ it.split(videoMs) })) version++
    }

    private fun deleteClip() {
        val i = selectedClip
        if (ReplayEditor.editClips({ it.delete(i) })) {
            selectedClip = -1
            version++
        }
    }

    /** Names of the lanes, lined up with [Timeline]'s ruler and two lanes. */
    @Composable
    private fun TrackLabels(modifier: Modifier) {
        val dim = OneConfigTheme.current.textColorSecondary
        Column(modifier.padding(top = RULER + 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (name in listOf(t("reizo.timeline.video"), t("reizo.timeline.camera"))) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                    Text(name, color = dim, style = OneConfigTheme.typography.small)
                }
            }
        }
    }

    /**
     * Shows the world in [area] (root pixels) at the window's shape, centred, like a video
     * editor's viewer; the rest of the screen gets the [backdrop].
     */
    private fun frameWorld(area: Rect, rootW: Float, rootH: Float, backdrop: Int) {
        if (rootW <= 0f || rootH <= 0f || area.width <= 0f || area.height <= 0f) {
            EditorViewport.clear()
            return
        }
        val aspect = rootW / rootH
        var w = area.width
        var h = w / aspect
        if (h > area.height) {
            h = area.height
            w = h * aspect
        }
        val x = area.left + (area.width - w) / 2
        val y = area.top + (area.height - h) / 2
        EditorViewport.set(x / rootW, y / rootH, w / rootW, h / rootH, backdrop)
    }

    override fun removed() {
        super.removed()
        EditorViewport.clear()
        hudHidden?.let { Compat.setHudHidden(Minecraft.getInstance(), it) }
        hudHidden = null
    }

    /** Whether the HUD was hidden before the editor hid it; null while the editor has not. */
    private var hudHidden: Boolean? = null

    /** The selected camera keyframes, by replay time. */
    private val selection = mutableStateListOf<Long>()

    /** The selected clip (index in play order), or -1. */
    private var selectedClip by mutableIntStateOf(-1)

    private enum class Drag { SEEK, KEYFRAMES, CLIP, TRIM_IN, TRIM_OUT }

    /** Bumped on every edit so that the editor reads the project again. */
    private var version by mutableIntStateOf(0)

    private fun replayDuration(): Long = maxOf(1L, ReplayPlayback.current()?.script()?.durationMs() ?: 1L)

    /** Length of the edited video: the clips' total, or the whole replay without clips. */
    private fun videoDuration(): Long {
        val seq = ReplayEditor.sequence()
        return if (seq.isEmpty) replayDuration() else maxOf(1L, seq.durationMs())
    }

    /** Where the timeline canvas is, in window pixels; for tests. */
    private var timelineBounds = Rect.Zero

    /** The timeline canvas's bounds and density, for test messages. */
    fun timelineArea(): String = "$timelineBounds at density $lastDensity"

    /** The selected keyframe times; for tests. */
    fun selectedTimes(): Set<Long> = selection.toSet()

    /**
     * The centre of a camera keyframe in window pixels, or null when it is not on the timeline; for
     * tests. Follows the layout in [Timeline].
     */
    fun keyframePoint(timeMs: Long): DoubleArray? {
        val b = timelineBounds
        val v = toVideo(timeMs)
        if (b.width <= 0f || v < 0) return null
        val density = lastDensity
        val ruler = RULER.value * density
        val lane = (b.height - ruler - 8 * density) / 2
        val y = b.top + ruler + 4 * density + lane + 4 * density + lane / 2
        return doubleArrayOf((b.left + b.width * v / videoDuration()).toDouble(), y.toDouble())
    }

    private var lastDensity = 1f

    /**
     * A point on a clip in window pixels, for tests: at [fraction] of its width (0 = its start
     * edge, 1 = its end edge), or null without clips.
     */
    fun clipPoint(index: Int, fraction: Double): DoubleArray? {
        val b = timelineBounds
        val seq = ReplayEditor.sequence()
        if (b.width <= 0f || seq.isEmpty || index !in 0 until seq.size()) return null
        val ruler = RULER.value * lastDensity
        val lane = (b.height - ruler - 8 * lastDensity) / 2
        val d = videoDuration().toDouble()
        val a = b.left + b.width * seq.startOf(index) / d
        val e = b.left + b.width * (seq.startOf(index) + seq.clip(index).lengthMs()) / d
        return doubleArrayOf(a + (e - a) * fraction, (b.top + ruler + 4 * lastDensity + lane / 2).toDouble())
    }

    /** The selected clip; for tests. */
    fun selectedClip(): Int = selectedClip

    private fun toVideo(ms: Long): Long = ReplayEditor.sequence().let { if (it.isEmpty) ms else it.toVideo(ms) }

    /**
     * Ruler, clips, camera keyframes and markers over video time (the clips in play order, or the
     * whole replay without clips). Click or drag to seek; click a keyframe to select it, drag it to
     * move the selection.
     */
    @Composable
    private fun Timeline(now: Long, edited: () -> Unit, modifier: Modifier) {
        val theme = OneConfigTheme.current
        val accent = OneConfigTheme.accent
        val track = theme.componentBackground
        val line = theme.borderColor
        val text = theme.textColor
        val dim = theme.textColorSecondary
        val seq = ReplayEditor.sequence()
        val duration = videoDuration()
        val markers = ReplayPlayback.metadata()?.markers?.map { toVideo(it.timeMs.toLong()) }?.filter { it >= 0 } ?: listOf()
        val keyframes = ReplayEditor.path().keyframes().map { it.timeMs() }
        val clips =
            if (seq.isEmpty) listOf(0L to duration)
            else seq.clips().indices.map { seq.startOf(it) to seq.startOf(it) + seq.clip(it).lengthMs() }
        val head = toVideo(now)
        val measurer = rememberTextMeasurer()
        val labelStyle = OneConfigTheme.typography.caption.copy(color = dim)
        val density = LocalDensity.current
        val hit = with(density) { 7.dp.toPx() }
        val rulerPx = with(density) { RULER.toPx() }

        val dp = density.density
        fun laneH(h: Float) = (h - rulerPx - 8 * dp) / 2
        fun clipsTop() = rulerPx + 4 * dp
        fun camTop(h: Float) = clipsTop() + laneH(h) + 4 * dp

        /** The keyframe (replay time) under a point in the camera lane, or null. */
        fun keyframeAt(p: Offset, w: Float, h: Float): Long? {
            if (p.y < camTop(h)) return null
            val d = videoDuration()
            return ReplayEditor.path().keyframes()
                .map { it.timeMs() to toVideo(it.timeMs()) }
                .filter { it.second >= 0 }
                .minByOrNull { kotlin.math.abs(w * it.second / d - p.x) }
                ?.takeIf { kotlin.math.abs(w * it.second / d - p.x) <= hit }
                ?.first
        }
        /** The clip under a point in the video lane and whether an edge was hit, or null. */
        fun clipAt(p: Offset, w: Float, h: Float): Pair<Int, Drag>? {
            val sq = ReplayEditor.sequence()
            if (sq.isEmpty || p.y < clipsTop() || p.y > clipsTop() + laneH(h)) return null
            val d = videoDuration()
            for (i in 0 until sq.size()) {
                val a = w * sq.startOf(i) / d
                val b = w * (sq.startOf(i) + sq.clip(i).lengthMs()) / d
                if (p.x < a - hit / 2 || p.x > b + hit / 2) continue
                val edge = minOf(hit, (b - a) / 4)
                return i to when {
                    p.x - a <= edge -> Drag.TRIM_IN
                    b - p.x <= edge -> Drag.TRIM_OUT
                    else -> Drag.CLIP
                }
            }
            return null
        }
        fun seekAt(x: Float, w: Float) {
            val d = videoDuration()
            ReplayEditor.seekVideo((x / w * d).toLong().coerceIn(0, d))
        }

        lastDensity = density.density
        Canvas(
            modifier
                .onGloballyPositioned { timelineBounds = it.boundsInWindow() }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val k = keyframeAt(p, w, h)
                        selection.clear()
                        if (k != null) {
                            selection.add(k)
                            ReplayPlayback.seekTo(k)
                            return@detectTapGestures
                        }
                        val clip = clipAt(p, w, h)
                        if (clip != null) selectedClip = clip.first else if (p.y > clipsTop()) selectedClip = -1
                        seekAt(p.x, w)
                    }
                }
                .pointerInput(Unit) {
                    var mode = Drag.SEEK
                    var startX = 0f
                    var lastX = 0f
                    var applied = 0L
                    var recorded = false
                    var clip = -1
                    var clipIn = 0L
                    var clipOut = 0L
                    fun finish() {
                        val w = size.width.toFloat()
                        if (mode == Drag.CLIP && kotlin.math.abs(lastX - startX) >= 4 * dp) {
                            val sq = ReplayEditor.sequence()
                            val t = (lastX / w * videoDuration()).toLong().coerceIn(0, maxOf(0L, sq.durationMs() - 1))
                            val to = sq.indexAt(t).coerceIn(0, sq.size() - 1)
                            val from = clip
                            if (to != from && ReplayEditor.editClips({ it.move(from, to) })) selectedClip = to
                            version++
                        } else if (recorded) {
                            edited()
                        }
                        mode = Drag.SEEK
                    }
                    detectDragGestures(
                        onDragStart = { p ->
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            startX = p.x
                            lastX = p.x
                            applied = 0
                            recorded = false
                            val k = keyframeAt(p, w, h)
                            val c = if (k == null) clipAt(p, w, h) else null
                            mode = when {
                                k != null -> Drag.KEYFRAMES
                                c != null -> c.second
                                else -> Drag.SEEK
                            }
                            if (k != null && k !in selection) {
                                selection.clear()
                                selection.add(k)
                            }
                            if (c != null) {
                                clip = c.first
                                selectedClip = c.first
                                clipIn = ReplayEditor.sequence().clip(clip).inMs()
                                clipOut = ReplayEditor.sequence().clip(clip).outMs()
                                if (c.second != Drag.CLIP) {
                                    ReplayEditor.recordUndo()
                                    recorded = true
                                }
                            }
                        },
                        onDragEnd = { finish() },
                        onDragCancel = { finish() },
                    ) { change, _ ->
                        lastX = change.position.x
                        val w = size.width.toFloat()
                        when (mode) {
                            Drag.SEEK -> {
                                seekAt(lastX, w)
                                return@detectDragGestures
                            }
                            Drag.CLIP -> return@detectDragGestures
                            Drag.TRIM_IN, Drag.TRIM_OUT -> {
                                val delta = ((lastX - startX) / w * videoDuration()).toLong()
                                val replay = replayDuration()
                                val i = clip
                                if (mode == Drag.TRIM_IN) {
                                    val inMs = maxOf(0L, clipIn + delta)
                                    ReplayEditor.editClips({ it.trimStart(i, inMs) }, false)
                                } else {
                                    val outMs = minOf(replay, clipOut + delta)
                                    ReplayEditor.editClips({ it.trimEnd(i, outMs, replay) }, false)
                                }
                                version++
                                return@detectDragGestures
                            }
                            Drag.KEYFRAMES -> {}
                        }
                        val delta = ((change.position.x - startX) / size.width * videoDuration()).toLong()
                        val step = delta - applied
                        if (step == 0L) return@detectDragGestures
                        if (!recorded) {
                            ReplayEditor.recordUndo()
                            recorded = true
                        }
                        val before = selection.minOrNull() ?: 0L
                        val moved = KeyframeEdits.move(ReplayEditor.path(), selection.toSet(), step, replayDuration())
                        if (moved.isNotEmpty()) {
                            applied += moved.min() - before
                            selection.clear()
                            selection.addAll(moved)
                            version++
                        }
                    }
                },
        ) {
            val w = size.width
            fun x(ms: Long) = w * ms / duration
            val rulerH = RULER.toPx()
            val laneH = (size.height - rulerH - 8.dp.toPx()) / 2
            val clipsY = rulerH + 4.dp.toPx()
            val camY = clipsY + laneH + 4.dp.toPx()
            // Ruler: ticks, with a time label every few seconds.
            val labelEvery = if (duration > 120_000) 30_000L else if (duration > 30_000) 10_000L else 5_000L
            var s = 0L
            while (s <= duration) {
                val labelled = s % labelEvery == 0L
                drawLine(line, Offset(x(s), if (labelled) rulerH * 0.35f else rulerH * 0.65f), Offset(x(s), rulerH), 1f)
                if (labelled && s < duration) {
                    drawText(measurer, ReplayHud.time(s), Offset(x(s) + 3.dp.toPx(), -2.dp.toPx()), labelStyle)
                }
                s += if (duration > 120_000) 5000 else 1000
            }
            // Video lane: one rounded block per clip, in play order.
            val gap = if (clips.size > 1) 1.dp.toPx() else 0f
            for ((i, c) in clips.withIndex()) {
                val (a, b) = c
                val selected = i == selectedClip && !seq.isEmpty
                drawRoundRect(
                    accent.copy(alpha = if (selected) 0.6f else 0.35f),
                    Offset(x(a) + gap, clipsY),
                    Size(x(b) - x(a) - 2 * gap, laneH - 2f),
                    CornerRadius(6.dp.toPx()),
                )
                if (selected) {
                    drawRoundRect(
                        text,
                        Offset(x(a) + gap, clipsY),
                        Size(x(b) - x(a) - 2 * gap, laneH - 2f),
                        CornerRadius(6.dp.toPx()),
                        style = Stroke(1.5f.dp.toPx()),
                    )
                }
            }
            // Camera lane.
            drawRoundRect(track, Offset(0f, camY), Size(w, laneH - 2f), CornerRadius(6.dp.toPx()))
            for (k in keyframes) {
                val v = toVideo(k)
                if (v < 0) continue
                val c = Offset(x(v), camY + laneH / 2)
                if (k in selection) {
                    diamond(c, 7.dp.toPx(), accent)
                    diamond(c, 4.dp.toPx(), text)
                } else {
                    diamond(c, 5.dp.toPx(), text)
                }
            }
            for (m in markers) drawLine(dim, Offset(x(m), clipsY), Offset(x(m), camY + laneH), 2f)
            // Playhead (hidden while the replay shows a moment cut out of the video).
            if (head >= 0) {
                val px = x(head)
                drawLine(accent, Offset(px, 0f), Offset(px, size.height), 2.dp.toPx())
                drawCircle(accent, 4.dp.toPx(), Offset(px, 4.dp.toPx()))
            }
        }
    }

    private companion object {
        val RULER = 16.dp
        val LANE = 40.dp
    }

    private fun DrawScope.diamond(c: Offset, r: Float, color: Color) {
        val p = Path().apply {
            moveTo(c.x, c.y - r)
            lineTo(c.x + r, c.y)
            lineTo(c.x, c.y + r)
            lineTo(c.x - r, c.y)
            close()
        }
        drawPath(p, color)
    }
}
