/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thexykril.reizo.core.export.ExportFormat
import io.github.thexykril.reizo.core.export.FfmpegCommand
import io.github.thexykril.reizo.fabric.RecorderClient
import io.github.thexykril.reizo.fabric.export.ExportControls
import io.github.thexykril.reizo.fabric.playback.ReplayEditor
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.LabeledField
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.Slider
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextField
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

/**
 * Export settings built from OneConfig's own controls (trial for ADR 0017): the same settings as
 * [ExportSettingsScreen], drawn by OneConfig's Compose UI so it matches OneClient and follows its
 * theme. Opened instead of the kit screen when OneConfig is installed.
 */
class ComposeExportScreen(private val parent: Screen?) :
    OneConfigComposeScreen(Component.translatable("reizo.export.title"), parent),
    ExportSettingsView {

    // The replay stays visible behind the card; no blur, dimming or menu panorama.
    override val blurBackground: Boolean get() = false
    override val panoramaBackground: Boolean get() = false
    override val dimBackground: Boolean get() = true

    private val formats: List<ExportFormat>
    private val problem: String?

    init {
        val ffmpeg = FfmpegCommand.locate(RecorderClient.get().config().ffmpegPath, System.getenv("PATH"))
        formats = ffmpeg.map { ExportSettingsScreen.formatsOf(it) }.orElse(listOf())
        problem = when {
            ffmpeg.isEmpty -> "reizo.export.no_ffmpeg"
            formats.isEmpty() -> "reizo.export.no_formats"
            else -> null
        }
    }

    override fun offeredFormats(): List<ExportFormat> = formats

    private fun t(key: String, vararg args: Any): String = Component.translatable(key, *args).string

    @Composable
    override fun Content() {
        val project = ReplayEditor.project() ?: return
        val r = project.render
        // One counter: any change recomposes, reading the saved project values again.
        var version by remember { mutableIntStateOf(0) }
        fun changed() {
            ReplayEditor.changed()
            version++
        }
        version.let { }
        val theme = OneConfigTheme.current
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(440.dp).themedSurface(SurfaceKind.Panel).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(t("reizo.export.title"), style = OneConfigTheme.typography.titleSmall)

                Text(t("reizo.export.section.video").uppercase(), color = theme.textColorSecondary, style = OneConfigTheme.typography.eyebrow)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField(t("reizo.export.resolution"), Modifier.weight(1f)) {
                        Select(
                            selected = ExportSettingsScreen.closest(r.width, r.height),
                            options = ExportSettingsScreen.RESOLUTIONS,
                            onSelect = {
                                r.width = it.width()
                                r.height = it.height()
                                changed()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { it.label().string },
                        )
                    }
                    LabeledField(t("reizo.export.fps"), Modifier.weight(1f)) {
                        Select(
                            selected = if (r.fps in ExportSettingsScreen.FRAME_RATES) r.fps else 60,
                            options = ExportSettingsScreen.FRAME_RATES,
                            onSelect = {
                                r.fps = it
                                changed()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { "$it fps" },
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LabeledField(t("reizo.export.quality"), Modifier.weight(1f)) {
                        Select(
                            selected = ExportSettingsScreen.closestQuality(r.crf),
                            options = ExportSettingsScreen.QUALITIES,
                            onSelect = {
                                r.crf = it
                                changed()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            label = { ExportSettingsScreen.qualityLabel(it).string },
                        )
                    }
                    if (formats.isNotEmpty()) {
                        if (ExportFormat.forEncoder(r.codec) !in formats) {
                            r.codec = formats[0].encoder()
                        }
                        LabeledField(t("reizo.export.format"), Modifier.weight(1f)) {
                            Select(
                                selected = ExportFormat.forEncoder(r.codec),
                                options = formats,
                                onSelect = {
                                    r.codec = it.encoder()
                                    changed()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { it.label() },
                            )
                        }
                    }
                }
                LabeledField(t("reizo.export.motion_blur")) {
                    Select(
                        selected = if (r.motionBlur in ExportSettingsScreen.MOTION_BLUR) r.motionBlur else 1,
                        options = ExportSettingsScreen.MOTION_BLUR,
                        onSelect = {
                            r.motionBlur = it
                            changed()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { if (it <= 1) t("options.off") else t("reizo.export.blur_samples", it) },
                    )
                }

                Text(t("reizo.export.section.audio").uppercase(), color = theme.textColorSecondary, style = OneConfigTheme.typography.eyebrow)
                Slider(
                    value = if (r.audio) r.soundVolume.toFloat() else 0f,
                    onValueChange = {
                        val v = it.toInt()
                        r.audio = v > 0
                        if (v > 0) r.soundVolume = v
                        version++
                    },
                    onValueChangeFinished = { changed() },
                    range = 0f..100f,
                    step = 5f,
                    label = t("reizo.export.audio"),
                    valueText = { if (it <= 0f) t("options.off") else "${it.toInt()}%" },
                    modifier = Modifier.fillMaxWidth(),
                )
                Slider(
                    value = r.musicVolume.toFloat(),
                    onValueChange = {
                        r.musicVolume = it.toInt()
                        version++
                    },
                    onValueChangeFinished = { changed() },
                    range = 0f..100f,
                    step = 5f,
                    label = t("reizo.export.music"),
                    valueText = { if (it <= 0f) t("options.off") else "${it.toInt()}%" },
                    modifier = Modifier.fillMaxWidth(),
                )

                var file by remember { mutableStateOf(r.output ?: "") }
                LabeledField(t("reizo.export.file")) {
                    TextField(
                        value = file,
                        onValueChange = {
                            file = it
                            r.output = if (it.isBlank()) null else it.trim()
                            changed()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = t("reizo.export.file_hint"),
                    )
                }

                Text(
                    if (problem != null) t(problem) else rangeText(),
                    color = if (problem != null) theme.error else theme.textColorSecondary,
                    style = OneConfigTheme.typography.small,
                )

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(t("gui.back"), { dismiss() }, Modifier.weight(1f), fillWidth = true)
                    TextButton(
                        t("reizo.export.start"),
                        { startExport() },
                        Modifier.weight(1f),
                        style = ControlStyle.Primary,
                        enabled = problem == null,
                        fillWidth = true,
                    )
                }
            }
        }
    }

    private fun rangeText(): String {
        val server = ReplayPlayback.current() ?: return ""
        val duration = server.script().durationMs()
        val inMs = maxOf(0L, ReplayEditor.inMs())
        val outMs = if (ReplayEditor.outMs() < 0) duration else minOf(ReplayEditor.outMs(), duration)
        val whole = ReplayEditor.inMs() < 0 && ReplayEditor.outMs() < 0
        return t(
            if (whole) "reizo.export.range_all" else "reizo.export.range",
            ReplayHud.time(inMs),
            ReplayHud.time(outMs),
        )
    }

    private fun startExport() {
        val mc = Minecraft.getInstance()
        mc.setScreen(null)
        ExportControls.exportCurrent(mc, null)
    }
}
