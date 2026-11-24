/*
 * Copyright (C) 2026 Reizo contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package io.github.thexykril.reizo.fabric.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.thexykril.reizo.fabric.RecorderClient
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.gui.screens.options.controls.KeyBindsScreen
import net.minecraft.network.chat.Component
import org.polyfrost.oneconfig.api.ui.v1.components.ControlRow
import org.polyfrost.oneconfig.api.ui.v1.components.ControlStyle
import org.polyfrost.oneconfig.api.ui.v1.components.Select
import org.polyfrost.oneconfig.api.ui.v1.components.SurfaceKind
import org.polyfrost.oneconfig.api.ui.v1.components.Text
import org.polyfrost.oneconfig.api.ui.v1.components.TextButton
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleControl
import org.polyfrost.oneconfig.api.ui.v1.components.ToggleRow
import org.polyfrost.oneconfig.api.ui.v1.components.themedSurface
import org.polyfrost.oneconfig.api.ui.v1.platform.OneConfigComposeScreen
import org.polyfrost.oneconfig.api.ui.v1.theme.OneConfigTheme

/**
 * Reizo's settings on OneConfig's Compose UI (ADR 0017): the same [io.github.thexykril.reizo.fabric.ClientConfig]
 * values as [SettingsScreen] and the OneConfig page.
 */
class ComposeSettingsScreen(private val parent: Screen?) :
    OneConfigComposeScreen(Component.translatable("reizo.settings.title"), parent) {

    private fun t(key: String): String = Component.translatable(key).string

    @Composable
    override fun Content() {
        val client = RecorderClient.get()
        val config = client.config()
        // Any change recomposes, reading the config values again.
        var version by remember { mutableIntStateOf(0) }
        version.let { }
        fun changed() {
            client.saveConfig()
            version++
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(420.dp).themedSurface(SurfaceKind.Panel).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(t("reizo.settings.title"), style = OneConfigTheme.typography.titleSmall)

                Section(t("reizo.settings.section.recording"))
                Toggle(t("reizo.settings.auto_record"), config.autoRecordOnJoin) {
                    config.autoRecordOnJoin = it
                    changed()
                }
                if (RecordingIndicator.drawnByReizo()) {
                    Toggle(t("reizo.settings.indicator"), config.showIndicator) {
                        config.showIndicator = it
                        changed()
                    }
                }
                Toggle(t("reizo.settings.server_name"), config.storeServerName) {
                    config.storeServerName = it
                    changed()
                }
                ControlRow(t("reizo.settings.toggle_modifier")) {
                    Select(
                        selected = KeyModifier.parse(config.toggleModifier),
                        options = KeyModifier.values().toList(),
                        onSelect = {
                            config.toggleModifier = it.configValue()
                            changed()
                        },
                        modifier = Modifier.width(140.dp),
                        label = { it.label().string },
                    )
                }

                Section(t("reizo.settings.section.shadow"))
                Toggle(t("reizo.settings.shadow"), config.shadowEnabled) {
                    config.shadowEnabled = it
                    client.applyShadowSettings()
                    changed()
                }
                ControlRow(t("reizo.settings.shadow_length"), enabled = config.shadowEnabled) {
                    Select(
                        selected = if (config.shadowSeconds in RecorderClient.SHADOW_LENGTHS) config.shadowSeconds else 120,
                        options = RecorderClient.SHADOW_LENGTHS,
                        onSelect = {
                            config.shadowSeconds = it
                            client.applyShadowSettings()
                            changed()
                        },
                        modifier = Modifier.width(140.dp),
                        label = { ReplayHud.time(it * 1000L) },
                    )
                }

                Section(t("reizo.settings.section.replay"))
                Toggle(t("reizo.settings.player_hud_short"), config.showPlayerHud) {
                    config.showPlayerHud = it
                    changed()
                }
                Toggle(t("reizo.settings.player_screens_short"), config.showPlayerScreens) {
                    config.showPlayerScreens = it
                    changed()
                }

                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        t("reizo.settings.keys"),
                        {
                            val mc = Minecraft.getInstance()
                            mc.setScreen(KeyBindsScreen(this@ComposeSettingsScreen, mc.options))
                        },
                        Modifier.weight(1f),
                        fillWidth = true,
                    )
                    TextButton(t("gui.done"), { dismiss() }, Modifier.weight(1f), style = ControlStyle.Primary, fillWidth = true)
                }
            }
        }
    }

    @Composable
    private fun Section(title: String) {
        Text(
            title.uppercase(),
            Modifier.padding(top = 6.dp),
            color = OneConfigTheme.current.textColorSecondary,
            style = OneConfigTheme.typography.eyebrow,
        )
    }

    @Composable
    private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        ToggleRow(label, checked, onChange, Modifier.fillMaxWidth(), control = ToggleControl.Switch)
    }

    override fun removed() {
        super.removed()
        RecorderClient.get().saveConfig()
    }
}
