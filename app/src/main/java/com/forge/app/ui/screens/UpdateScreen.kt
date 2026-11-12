package com.forge.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.Bar
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import com.forge.app.update.Updater
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun UpdateScreen(nav: Nav) {
    val ctx = LocalContext.current
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check the "install unknown apps" permission when coming back from system settings.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val canInstall = remember(refresh) { Updater.canInstall(ctx) }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("Updates", "By ${com.forge.app.Credits.AUTHOR} · github.com/${Updater.repo}", onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            Gap(6.dp)
            Panel {
                Eyebrow("Installed")
                Text("v${Updater.currentVersion}", style = T.Display, color = C.Hi)
                Gap(14.dp)
                when (val st = state) {
                    Updater.State.Idle -> {
                        Text("Check GitHub for a newer version.", style = T.Body, color = C.Mid)
                        Gap(14.dp)
                        PrimaryButton("Check for updates", { scope.launch { Updater.check() } }, icon = Icons.Rounded.Refresh)
                    }
                    Updater.State.Checking -> {
                        Text("Checking GitHub…", style = T.Body, color = C.Mid)
                        Gap(14.dp)
                        PrimaryButton("Checking…", {}, enabled = false)
                    }
                    is Updater.State.UpToDate -> {
                        Text("You're on the latest version.", style = T.BodyS, color = C.Volt)
                        Text("Checked at ${SimpleDateFormat("HH:mm", Locale.US).format(Date(st.at))}.", style = T.Small, color = C.Lo)
                        Gap(14.dp)
                        SecondaryButton("Check again", { scope.launch { Updater.check() } }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
                    }
                    is Updater.State.Available -> {
                        Eyebrow("New version", color = C.Volt)
                        Text("v${st.info.version}", style = T.H1, color = C.Hi)
                        Notes(st.info.notes)
                        Gap(14.dp)
                        PrimaryButton(
                            "Download · ${sizeText(st.info.size)}",
                            { scope.launch { Updater.download(ctx, st.info) } },
                            icon = Icons.Rounded.Download,
                        )
                    }
                    is Updater.State.Downloading -> {
                        Eyebrow("Downloading v${st.info.version}", color = C.Volt)
                        Gap(10.dp)
                        Bar(st.progress, height = 8.dp)
                        Gap(6.dp)
                        Text("${(st.progress * 100).roundToInt()}%", style = T.Small, color = C.Mid)
                    }
                    is Updater.State.Ready -> {
                        Eyebrow("Ready to install", color = C.Volt)
                        Text("v${st.info.version}", style = T.H1, color = C.Hi)
                        Gap(6.dp)
                        Text("Signature checked: same app, same key. Your data stays where it is, and a backup is saved right before installing.", style = T.Small, color = C.Mid)
                        Gap(14.dp)
                        if (!canInstall) {
                            Text("Android needs your OK once to let Forge install its own updates.", style = T.Small, color = C.Amber)
                            Gap(10.dp)
                            SecondaryButton("Allow updates from Forge", { Updater.openInstallPermission(ctx) }, Modifier.fillMaxWidth(), color = C.Amber, border = C.Amber.copy(alpha = 0.5f))
                            Gap(10.dp)
                        }
                        PrimaryButton("Install update", { scope.launch { Updater.install(ctx, st.file) } }, enabled = canInstall, icon = Icons.Rounded.SystemUpdate)
                    }
                    is Updater.State.Failed -> {
                        Text(st.message, style = T.Body, color = C.Red)
                        Gap(14.dp)
                        SecondaryButton("Try again", {
                            scope.launch { if (st.info != null) Updater.download(ctx, st.info) else Updater.check() }
                        }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Refresh)
                    }
                }
            }
            Gap(12.dp)
            Panel {
                ToggleRow("Check daily", "A notification when a new version is published", s.autoUpdateCheck) { v ->
                    scope.launch { Graph.settings.update { it.copy(autoUpdateCheck = v) } }
                }
                Hairline()
                ToggleRow("Daily automatic backup", "Also saved to Downloads/Forge, so even a reinstall can't lose it", s.autoBackup) { v ->
                    scope.launch { Graph.settings.update { it.copy(autoBackup = v) } }
                }
                if (s.lastBackupAt > 0) {
                    Text(
                        "Last backup: ${Fmt.day(java.time.Instant.ofEpochMilli(s.lastBackupAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate())} " +
                            SimpleDateFormat("HH:mm", Locale.US).format(Date(s.lastBackupAt)) +
                            (if (s.lastBackupReason.isNotBlank()) " · ${s.lastBackupReason}" else ""),
                        style = T.Small, color = C.Lo,
                    )
                }
            }
            Gap(12.dp)
            SecondaryButton("All releases on GitHub", {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Updater.releasesPage))) }
            }, Modifier.fillMaxWidth())
            Gap(40.dp)
        }
    }
}

@Composable
private fun Notes(notes: String) {
    if (notes.isBlank()) return
    Gap(8.dp)
    notes.lines().filter { it.isNotBlank() }.take(12).forEach { line ->
        val l = line.trim()
        val bullet = l.startsWith("-") || l.startsWith("*")
        val heading = l.startsWith("#")
        Row(Modifier.padding(vertical = 2.dp)) {
            if (bullet) Text("•", style = T.Small, color = C.Volt, modifier = Modifier.width(14.dp))
            Text(
                l.trimStart('-', '*', '#', ' '),
                style = if (heading) T.BodyS else T.Small,
                color = if (heading) C.Hi else C.Mid,
            )
        }
    }
}

private fun sizeText(bytes: Long): String =
    if (bytes <= 0) "APK" else String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
