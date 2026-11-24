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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thexykril.reizo.core.timeline.KeyframeEdits
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
        val timelineHeight = if (maxHeight < 400.dp) maxHeight * 0.55f else (maxHeight * 0.32f).coerceIn(170.dp, 260.dp)
        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TopBar()
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                if (wide) Inspector(now, ::edited)
            }
            Column(
                Modifier.fillMaxWidth().height(timelineHeight).themedSurface(SurfaceKind.Panel).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Transport(maxOf(0L, toVideo(now)), videoDuration(), server.clock().isRunning, server.clock().speed())
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

    @Composable
    private fun Inspector(now: Long, edited: () -> Unit) {
        val theme = OneConfigTheme.current
        Column(
            Modifier.width(260.dp).fillMaxHeight().themedSurface(SurfaceKind.Panel).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(t("reizo.timeline.camera").uppercase(), color = theme.textColorSecondary, style = OneConfigTheme.typography.eyebrow)
            val key = ReplayEditor.nearest(now, 50)
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
            Spacer(Modifier.weight(1f))
            Text(
                ReplayEditor.path().keyframes().size.toString() + " " + t("reizo.timeline.keyframes"),
                color = theme.textColorSecondary,
                style = OneConfigTheme.typography.small,
            )
            if (key != null) {
                TextButton(
                    t("reizo.timeline.remove_keyframe"),
                    {
                        ReplayEditor.recordUndo()
                        ReplayEditor.removeNearestKeyframe()
                        edited()
                    },
                    Modifier.fillMaxWidth(),
                    style = ControlStyle.Danger,
                    fillWidth = true,
                )
            } else {
                TextButton(
                    t("reizo.timeline.add_keyframe"),
                    {
                        ReplayEditor.recordUndo()
                        ReplayEditor.addKeyframe(Minecraft.getInstance())
                        edited()
                    },
                    Modifier.fillMaxWidth(),
                    style = ControlStyle.Primary,
                    fillWidth = true,
                )
            }
        }
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
    private fun Transport(now: Long, duration: Long, running: Boolean, speed: Double) {
        val theme = OneConfigTheme.current
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(if (running) "❚❚" else "▶", { ReplayPlayback.togglePause() }, style = ControlStyle.Secondary)
            Text(ReplayHud.time(now), style = OneConfigTheme.typography.bodyMedium)
            Text("/ " + ReplayHud.time(duration), color = theme.textColorSecondary, style = OneConfigTheme.typography.small)
            Spacer(Modifier.weight(1f))
            Text(ReplayHud.speed(speed) + "×", color = theme.textColorSecondary, style = OneConfigTheme.typography.small)
            TextButton("−", { ReplayPlayback.stepSpeed(-1) }, style = ControlStyle.Ghost)
            TextButton("+", { ReplayPlayback.stepSpeed(1) }, style = ControlStyle.Ghost)
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

    /** The selected camera keyframes, by replay time. */
    private val selection = mutableStateListOf<Long>()

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

        /** The keyframe (replay time) under a point in the camera lane, or null. */
        fun keyframeAt(p: Offset, w: Float, h: Float): Long? {
            val camTop = rulerPx + 4 * density.density + (h - rulerPx - 8 * density.density) / 2 + 4 * density.density
            if (p.y < camTop) return null
            val d = videoDuration()
            return ReplayEditor.path().keyframes()
                .map { it.timeMs() to toVideo(it.timeMs()) }
                .filter { it.second >= 0 }
                .minByOrNull { kotlin.math.abs(w * it.second / d - p.x) }
                ?.takeIf { kotlin.math.abs(w * it.second / d - p.x) <= hit }
                ?.first
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
                        val k = keyframeAt(p, size.width.toFloat(), size.height.toFloat())
                        selection.clear()
                        if (k != null) {
                            selection.add(k)
                            ReplayPlayback.seekTo(k)
                        } else {
                            seekAt(p.x, size.width.toFloat())
                        }
                    }
                }
                .pointerInput(Unit) {
                    var moving = false
                    var startX = 0f
                    var applied = 0L
                    var recorded = false
                    detectDragGestures(
                        onDragStart = { p ->
                            val k = keyframeAt(p, size.width.toFloat(), size.height.toFloat())
                            moving = k != null
                            startX = p.x
                            applied = 0
                            recorded = false
                            if (k != null && k !in selection) {
                                selection.clear()
                                selection.add(k)
                            }
                        },
                        onDragEnd = { if (recorded) edited() },
                        onDragCancel = { if (recorded) edited() },
                    ) { change, _ ->
                        if (!moving) {
                            seekAt(change.position.x, size.width.toFloat())
                            return@detectDragGestures
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
            for ((a, b) in clips) {
                drawRoundRect(
                    accent.copy(alpha = 0.35f),
                    Offset(x(a) + gap, clipsY),
                    Size(x(b) - x(a) - 2 * gap, laneH - 2f),
                    CornerRadius(6.dp.toPx()),
                )
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
