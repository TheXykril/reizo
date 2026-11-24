/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.thexykril.reizo.core.storage.RecordingStorage
import io.github.thexykril.reizo.fabric.playback.ReplayPlayback
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.components.ConfirmDialog
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.EmptyState
import org.polyfrost.oneconfig.api.ui.v1.components.ScrollContainer
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.TextPromptDialog
import org.polyfrost.oneconfig.api.ui.v1.components.selectableRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme
import org.slf4j.LoggerFactory
import java.io.IOException

/**
 * The replay browser on OneConfig's Compose UI (ADR 0017): lists saved recordings and plays,
 * renames or deletes them, like [ReplayBrowserScreen].
 */
class ComposeBrowserScreen(private val parent: Screen?) :
    OneConfigComposeScreen(Component.translatable("reizo.browser.title"), parent),
    ReplayBrowserView {

    private val log = LoggerFactory.getLogger("Reizo/Browser")
    private val rows = mutableStateListOf<RecordingStorage.Entry>()
    private var selected by mutableIntStateOf(-1)
    private var error by mutableStateOf<String?>(null)
    private var renaming by mutableStateOf<RecordingStorage.Entry?>(null)
    private var deleting by mutableStateOf<RecordingStorage.Entry?>(null)
    private var lastClick = 0L
    private var lastClickRow = -1

    init {
        reload()
    }

    private fun t(key: String, vararg args: Any): String = Component.translatable(key, *args).string

    private fun reload() {
        val keep = selectedEntry()?.path()
        val entries = try {
            ReplayBrowserScreen.storage().listRecordings()
        } catch (e: IOException) {
            log.warn("Could not list recordings", e)
            error = t("reizo.browser.list_failed")
            listOf()
        }
        rows.clear()
        rows.addAll(entries)
        selected = entries.indexOfFirst { it.path() == keep }
    }

    private fun selectedEntry(): RecordingStorage.Entry? = rows.getOrNull(selected)

    override fun entries(): List<RecordingStorage.Entry> = rows.toList()

    override fun select(index: Int) {
        selected = index
    }

    override fun pressPlay() = play()

    @Composable
    override fun Content() {
        val theme = OneConfigTheme.current
        Box(Modifier.fillMaxSize().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(560.dp).fillMaxHeight().themedSurface(SurfaceKind.Panel).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("reizo.browser.title"), Modifier.weight(1f), style = OneConfigTheme.typography.titleSmall)
                    TextButton(t("reizo.browser.open_folder"), { ReplayBrowserScreen.openFolder() }, style = ControlStyle.Ghost)
                    TextButton(
                        t("reizo.browser.settings"),
                        { Minecraft.getInstance().setScreen(SettingsScreen.create(this@ComposeBrowserScreen)) },
                        style = ControlStyle.Ghost,
                    )
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (rows.isEmpty()) {
                        EmptyState(t("reizo.browser.empty"), Modifier.fillMaxSize())
                    } else {
                        val state = rememberLazyListState()
                        ScrollContainer(state, Modifier.fillMaxSize()) {
                            LazyColumn(state = state, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                itemsIndexed(rows, key = { _, e -> e.path().toString() }) { index, entry ->
                                    RecordingRow(entry, index == selected) { click(index) }
                                }
                            }
                        }
                    }
                }

                error?.let { Text(it, color = theme.error, style = OneConfigTheme.typography.small) }

                val row = selectedEntry()
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(t("gui.done"), { dismiss() }, Modifier.weight(1f), fillWidth = true)
                    TextButton(
                        t("reizo.browser.rename"),
                        { renaming = row },
                        Modifier.weight(1f),
                        enabled = row != null,
                        fillWidth = true,
                    )
                    TextButton(
                        t("reizo.browser.delete"),
                        { deleting = row },
                        Modifier.weight(1f),
                        style = ControlStyle.Danger,
                        enabled = row != null,
                        fillWidth = true,
                    )
                    TextButton(
                        t("reizo.browser.play"),
                        { play() },
                        Modifier.weight(1f),
                        style = ControlStyle.Primary,
                        enabled = row != null && ReplayBrowserScreen.playable(row),
                        fillWidth = true,
                    )
                }
            }
        }

        renaming?.let { entry ->
            TextPromptDialog(
                title = t("reizo.browser.rename"),
                initial = entry.metadata().name,
                onConfirm = { name ->
                    renaming = null
                    try {
                        ReplayBrowserScreen.storage().rename(entry.path(), name)
                    } catch (e: Exception) {
                        log.warn("Rename failed", e)
                        error = t("reizo.browser.rename_failed")
                    }
                    reload()
                },
                onDismiss = { renaming = null },
                validate = { if (it.isBlank()) "" else null },
            )
        }
        deleting?.let { entry ->
            ConfirmDialog(
                title = t("reizo.browser.delete_question"),
                text = t("reizo.browser.delete_warning", entry.metadata().name),
                confirmLabel = t("reizo.browser.delete"),
                onConfirm = {
                    deleting = null
                    try {
                        ReplayBrowserScreen.storage().delete(entry.path())
                    } catch (e: Exception) {
                        log.warn("Delete failed", e)
                        error = t("reizo.browser.delete_failed")
                    }
                    reload()
                },
                onDismiss = { deleting = null },
                danger = true,
            )
        }
    }

    @Composable
    private fun RecordingRow(entry: RecordingStorage.Entry, isSelected: Boolean, onClick: () -> Unit) {
        val theme = OneConfigTheme.current
        val m = entry.metadata()
        Column(
            Modifier.fillMaxWidth()
                .selectableRow(isSelected, onClick, m.name)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    m.name,
                    Modifier.weight(1f),
                    style = OneConfigTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(ReplayBrowserScreen.duration(m.durationMs), color = theme.textColorSecondary, style = OneConfigTheme.typography.small)
            }
            Text(
                ReplayBrowserScreen.recordedLine(m),
                color = theme.textColorSecondary,
                style = OneConfigTheme.typography.small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (ReplayBrowserScreen.playable(entry)) {
                Text(ReplayBrowserScreen.detailsLine(entry), color = theme.textColorSecondary, style = OneConfigTheme.typography.caption)
            } else {
                Text(t("reizo.browser.other_version", m.minecraftVersion), color = theme.error, style = OneConfigTheme.typography.caption)
            }
        }
    }

    /** Selects a row; a second click on the same row within 250 ms plays it. */
    private fun click(index: Int) {
        val now = System.currentTimeMillis()
        val again = index == lastClickRow && now - lastClick < 250
        selected = index
        lastClick = now
        lastClickRow = index
        if (again) play()
    }

    private fun play() {
        val entry = selectedEntry() ?: return
        if (!ReplayBrowserScreen.playable(entry)) return
        val mc = Minecraft.getInstance()
        try {
            ReplayPlayback.start(mc, entry.path())
        } catch (e: Exception) {
            log.warn("Could not play {}", entry.path(), e)
            error = t("reizo.browser.play_failed", e.message.toString())
            mc.setScreen(this)
        }
    }
}
