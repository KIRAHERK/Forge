package com.forge.app.ui.screens

import com.forge.app.i18n.t

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.Settings
import com.forge.app.domain.Plan
import com.forge.app.domain.Program
import com.forge.app.notify.Notifier
import com.forge.app.notify.Scheduler
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.AccentPicker
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.pickAccent
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.NumberDialog
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.ValueRow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeDialog(title: String, minutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = minutes / 60, initialMinute = minutes % 60, is24Hour = true)
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.clip(RoundedCornerShape(24.dp)).background(C.S1).padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title.uppercase(), style = T.H2, color = C.Hi, modifier = Modifier.fillMaxWidth())
            Gap(16.dp)
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = C.S2,
                    clockDialSelectedContentColor = C.OnVolt,
                    clockDialUnselectedContentColor = C.Hi,
                    selectorColor = C.Volt,
                    containerColor = C.S1,
                    timeSelectorSelectedContainerColor = C.Volt,
                    timeSelectorUnselectedContainerColor = C.S2,
                    timeSelectorSelectedContentColor = C.OnVolt,
                    timeSelectorUnselectedContentColor = C.Hi,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextAction(t("CANCEL"), onDismiss, color = C.Mid, style = T.Button)
                TextAction(t("SET"), { onConfirm(state.hour * 60 + state.minute) }, style = T.Button)
            }
        }
    }
}

fun isIgnoringBattery(ctx: Context): Boolean =
    ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) == true

@SuppressLint("BatteryLife")
fun requestBatteryExemption(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")))
    }.onFailure {
        runCatching { ctx.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
    }
}

fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName))
    }
}

private val weekdays = DayOfWeek.entries

@Composable
fun SettingsScreen(nav: Nav) {
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    fun upd(t: (Settings) -> Settings) { scope.launch { Graph.settings.update(t); Scheduler.rescheduleAll(ctx) } }

    var timeEdit by remember { mutableStateOf<Triple<String, Int, (Int) -> Unit>?>(null) }
    var numEdit by remember { mutableStateOf<Triple<String, String, (Double) -> Unit>?>(null) }
    var importText by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    // Re-check permission states when returning from system settings.
    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = Graph.repo.exportJson()
                withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) } }
            }.isSuccess
            message = if (ok) t("Backup saved.") else t("Couldn't save the backup.")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importText = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (importText == null) message = t("Couldn't read that file.")
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Settings"), onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {

            // ------------------------------------------------ look
            Group(t("Color")) {
                AccentPicker(C.accent, { pickAccent(scope, it) })
                Gap(6.dp)
                Text(t("Changes the whole app, the widget and the pictures you share."), style = T.Small, color = C.Lo)
            }

            Group(t("Language")) {
                com.forge.app.ui.components.LanguagePicker()
                Gap(6.dp)
                Text(t("The app restarts to switch."), style = T.Small, color = C.Lo)
            }

            Group(t("Help")) {
                ValueRow(t("Guide"), t("Show"), t("A quick tour of every tab")) {
                    com.forge.app.ui.UiEvents.guideRequest.value = true
                    nav.tab("today")
                }
            }

            // ------------------------------------------------ schedule
            Group(t("Schedule")) {
                Eyebrow(t("Week starts on"))
                Gap(8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekdays.forEach { d ->
                        Chip(d.getDisplayName(TextStyle.SHORT, com.forge.app.i18n.Lang.locale), s.weekStart == d.value, {
                            upd {
                                val n = it.copy(weekStart = d.value)
                                n.copy(programStart = n.weekStartOf(LocalDate.ofEpochDay(it.programStart)).toEpochDay())
                            }
                        })
                    }
                }
                Gap(10.dp)
                Hairline()
                val active = com.forge.app.ui.LocalPrograms.current?.active
                ValueRow(
                    t("Program"),
                    active?.let { t("%s / week", it.daysPerWeek) } ?: "",
                    active?.name ?: t("None"),
                ) { nav.go("programs") }
            }

            // ------------------------------------------------ reminders
            Group(t("Reminders")) {
                ValueRow(t("Workout reminder"), Fmt.time(s.workoutTime), t("Training days only")) {
                    timeEdit = Triple(t("Workout reminder"), s.workoutTime) { v -> upd { it.copy(workoutTime = v) } }
                }
                Hairline()
                ValueRow(t("Deadline"), Fmt.time(s.deadline), t("Warnings at %1${'$'}s and %2${'$'}s", Fmt.time(Scheduler.nudgeMinutes(s)), Fmt.time(Scheduler.finalMinutes(s))), valueColor = C.Red) {
                    timeEdit = Triple(t("Deadline"), s.deadline) { v -> upd { it.copy(deadline = v.coerceAtLeast(it.workoutTime + 30)) } }
                }
                Hairline()
                ValueRow(t("Morning weigh-in"), Fmt.time(s.weighInTime)) {
                    timeEdit = Triple(t("Morning weigh-in"), s.weighInTime) { v -> upd { it.copy(weighInTime = v) } }
                }
                Hairline()
                ValueRow(t("Food check 1"), Fmt.time(s.proteinCheck1), t("Only if you're behind")) {
                    timeEdit = Triple(t("Food check 1"), s.proteinCheck1) { v -> upd { it.copy(proteinCheck1 = v) } }
                }
                Hairline()
                ValueRow(t("Food check 2"), Fmt.time(s.proteinCheck2), t("Only if you're behind")) {
                    timeEdit = Triple(t("Food check 2"), s.proteinCheck2) { v -> upd { it.copy(proteinCheck2 = v) } }
                }
                Hairline()
                ValueRow(t("Night check-in"), Fmt.time(s.nightTime), t("Log habits and food")) {
                    timeEdit = Triple(t("Night check-in"), s.nightTime) { v -> upd { it.copy(nightTime = v) } }
                }
                Gap(8.dp)
                ToggleRow(t("Workout reminders"), null, s.remindWorkout) { v -> upd { it.copy(remindWorkout = v) } }
                ToggleRow(t("Weigh-in reminder"), null, s.remindWeighIn) { v -> upd { it.copy(remindWeighIn = v) } }
                ToggleRow(t("Food checks"), null, s.remindNutrition) { v -> upd { it.copy(remindNutrition = v) } }
                ToggleRow(t("Night check-in"), null, s.remindNight) { v -> upd { it.copy(remindNight = v) } }
                ToggleRow(t("Weekly review"), t("Score at the end of each week"), s.remindWeekly) { v -> upd { it.copy(remindWeekly = v) } }
            }

            // ------------------------------------------------ accountability
            Group(t("Accountability")) {
                ToggleRow(t("Penalties"), t("Missed workouts and habit slips issue a penalty. Ignore it and it doubles."), s.penaltiesOn) { v -> upd { it.copy(penaltiesOn = v) } }
                Hairline()
                ValueRow(
                    t("Penalty jar"),
                    if (s.jarAmount == 0) t("Off") else "${s.jarAmount} ${s.jarCurrency}".trim(),
                    t("Money you set aside for every miss or slip"),
                    valueColor = if (s.jarAmount == 0) C.Mid else C.Amber,
                ) {
                    numEdit = Triple(t("Amount per miss"), s.jarAmount.toString()) { v -> upd { it.copy(jarAmount = v.toInt().coerceIn(0, 100000)) } }
                }
                if (s.jarAmount > 0) {
                    var cur by remember(s.jarCurrency) { mutableStateOf(s.jarCurrency) }
                    ForgeField(cur, { cur = it.take(6); upd { st -> st.copy(jarCurrency = cur) } }, t("Currency (e.g. EGP, USD)"))
                    Gap(8.dp)
                }
            }

            // ------------------------------------------------ habits
            Group(t("Habits")) {
                ValueRow(t("Your habits"), t("Manage"), t("Add, rename or delete them in the Habits tab")) { nav.tab("habits") }
            }

            // ------------------------------------------------ targets
            Group(t("Targets")) {
                ValueRow(t("Calories"), t("%s kcal", Fmt.num(s.kcal))) { numEdit = Triple(t("Calories"), s.kcal.toString()) { v -> upd { it.copy(kcal = v.toInt().coerceIn(1200, 6000)) } } }
                Hairline()
                ValueRow(t("Protein"), com.forge.app.i18n.t("%s g", s.protein)) { numEdit = Triple(t("Protein (g)"), s.protein.toString()) { v -> upd { it.copy(protein = v.toInt().coerceIn(30, 400)) } } }
                Hairline()
                ValueRow(t("Fat"), com.forge.app.i18n.t("%s g", s.fat)) { numEdit = Triple(t("Fat (g)"), s.fat.toString()) { v -> upd { it.copy(fat = v.toInt().coerceIn(10, 300)) } } }
                Hairline()
                ValueRow(t("Carbs"), com.forge.app.i18n.t("%s g", s.carbs)) { numEdit = Triple(t("Carbs (g)"), s.carbs.toString()) { v -> upd { it.copy(carbs = v.toInt().coerceIn(20, 900)) } } }
                Hairline()
                ValueRow(t("Milestone weight"), t("%s kg", Fmt.kg(s.goalWeight))) { numEdit = Triple(t("Milestone (kg)"), Fmt.kg(s.goalWeight)) { v -> upd { it.copy(goalWeight = v.coerceIn(40.0, 200.0)) } } }
                Gap(6.dp)
                TextAction(t("Reset to plan: %1${'$'}s kcal · %2${'$'}s g · %3${'$'}s g · %4${'$'}s g", Fmt.num(Plan.KCAL), Plan.PROTEIN, Plan.FAT, Plan.CARBS), {
                    upd { it.copy(kcal = Plan.KCAL, protein = Plan.PROTEIN, fat = Plan.FAT, carbs = Plan.CARBS, goalWeight = Plan.MILESTONE) }
                }, style = T.Small)
                Gap(10.dp)
                var note by remember(s.planNote) { mutableStateOf(s.planNote) }
                ForgeField(note, { note = it }, t("Plan notes (e.g. a doctor's protein limit)"), singleLine = false)
                if (note != s.planNote) {
                    Gap(6.dp)
                    TextAction(t("Save notes"), { scope.launch { Graph.settings.update { it.copy(planNote = note.trim()) } } }, style = T.Small)
                }
                Text(t("Stays on this phone. Shown in Fuel → Your nutrition plan."), style = T.Tiny, color = C.Lo)
            }

            // ------------------------------------------------ updates
            Group(t("Updates")) {
                ValueRow(t("Version"), "v${com.forge.app.update.Updater.currentVersion}", t("Check GitHub for a newer release")) { nav.go("update") }
                Hairline()
                ToggleRow(t("Check daily"), t("Get a notification when a new version is out"), s.autoUpdateCheck) { v -> upd { it.copy(autoUpdateCheck = v) } }
            }

            // ------------------------------------------------ reliability
            Group(t("Make sure reminders arrive")) {
                val notifOk = remember(refresh) { Notifier.canPost(ctx) }
                val exactOk = remember(refresh) { Scheduler.canExact(ctx) }
                val batteryOk = remember(refresh) { isIgnoringBattery(ctx) }
                StatusRow(t("Notifications"), notifOk, t("Allow")) {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                }
                Hairline()
                StatusRow(t("Exact timing"), exactOk, t("Allow")) {
                    if (Build.VERSION.SDK_INT >= 31) runCatching {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                Hairline()
                StatusRow(t("Not battery-restricted"), batteryOk, t("Allow")) { requestBatteryExemption(ctx) }
                Gap(8.dp)
                Text(
                    t("Xiaomi, Samsung, Huawei, Oppo and similar phones kill background apps. If reminders don't arrive, enable Autostart for Forge and set its battery use to Unrestricted."),
                    style = T.Small, color = C.Mid,
                )
                Gap(10.dp)
                SecondaryButton(t("Send a test notification"), {
                    Notifier.post(ctx, Notifier.ID_TEST, Notifier.CH_WORKOUT, t("Test"), t("If you can read this, reminders work."))
                }, Modifier.fillMaxWidth(), height = 46.dp)
            }

            // ------------------------------------------------ data
            Group(t("Data")) {
                Text(t("Everything is stored on this phone only. Updates keep it in place; backups cover the rest."), style = T.Small, color = C.Mid)
                Gap(6.dp)
                ToggleRow(t("Daily automatic backup"), t("Saved in the app and in Downloads/Forge (Android 10+)"), s.autoBackup) { v -> upd { it.copy(autoBackup = v) } }
                if (s.lastBackupAt > 0) {
                    Text(
                        "Last backup: ${java.text.SimpleDateFormat("EEE d MMM, HH:mm", com.forge.app.i18n.Lang.locale).format(java.util.Date(s.lastBackupAt))}" +
                            (if (s.lastBackupReason.isNotBlank()) " · ${s.lastBackupReason}" else ""),
                        style = T.Small, color = C.Lo,
                    )
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(t("Back up now"), {
                        scope.launch { message = if (runCatching { com.forge.app.data.AutoBackup.run(ctx, "manual") }.getOrDefault(false)) t("Backup saved.") else t("Backup failed.") }
                    }, Modifier.weight(1f), height = 46.dp)
                    SecondaryButton(t("Restore last"), {
                        scope.launch {
                            val f = com.forge.app.data.AutoBackup.latestFile(ctx)
                            if (f.exists()) importText = f.readText() else message = t("No automatic backup on this phone yet.")
                        }
                    }, Modifier.weight(1f), height = 46.dp)
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(t("Export file"), { exportLauncher.launch("forge-backup-${LocalDate.now()}.json") }, Modifier.weight(1f), height = 46.dp)
                    SecondaryButton(t("Import file"), { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f), height = 46.dp)
                }
            }

            Group(t("About")) {
                Text(t("Forge v%s", com.forge.app.update.Updater.currentVersion), style = T.BodyS, color = C.Hi)
                Gap(2.dp)
                Text(com.forge.app.Credits.LINE, style = T.Small, color = C.Volt)
                Gap(12.dp)
                com.forge.app.Credits.links.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { (label, url) ->
                            SecondaryButton(label, {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            }, Modifier.weight(1f), height = 46.dp, hPad = 4.dp)
                        }
                    }
                    Gap(10.dp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton(t("Share Forge"), {
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                t("Forge: a training and nutrition app for Android that holds you to your plan. Free for personal use.\n") +
                                    t("Download: %s", com.forge.app.Credits.DOWNLOAD_PAGE),
                            )
                        }
                        runCatching { ctx.startActivity(Intent.createChooser(send, t("Share Forge"))) }
                    }, Modifier.weight(1f), height = 46.dp, hPad = 6.dp)
                    SecondaryButton(t("Source code"), {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${com.forge.app.update.Updater.repo}"))) }
                    }, Modifier.weight(1f), height = 46.dp, hPad = 6.dp)
                }
                com.forge.app.ui.components.Details(t("Sources and license")) {
                    Text(
                        t("Ships with a 4-day home muscle program and a lean-bulk nutrition plan; their sets, reps and targets are used as written. ") +
                            t("Exercise cues were compiled from coaching references (ACE, NASM, Motra and others). Food values come from USDA FoodData Central, ") +
                            t("published dish studies and Open Food Facts.\n\nSource-available under the PolyForm Noncommercial License 1.0.0. Free for personal use; ") +
                            t("selling, rebranding or business use needs a commercial license. Copyright %s.", com.forge.app.Credits.AUTHOR),
                        style = T.Small, color = C.Mid,
                    )
                }
            }
            Gap(40.dp)
        }
    }

    timeEdit?.let { (title, value, set) ->
        TimeDialog(title, value, { timeEdit = null }) { v -> set(v); timeEdit = null }
    }
    numEdit?.let { (title, value, set) ->
        NumberDialog(title, value, null, { numEdit = null }, { v -> set(v); numEdit = null })
    }
    importText?.let { txt ->
        ForgeDialog(
            title = t("Replace all data?"),
            onDismiss = { importText = null },
            confirm = t("Replace"),
            destructive = true,
            onConfirm = {
                importText = null
                scope.launch {
                    message = if (runCatching { Graph.repo.importJson(txt) }.isSuccess) t("Backup restored.") else t("That file isn't a valid Forge backup.")
                }
            },
        ) {
            Text(t("Everything currently in the app is replaced by the backup. This can't be undone."), style = T.Body, color = C.Mid)
        }
    }
    message?.let { m ->
        LaunchedEffect(m) { kotlinx.coroutines.delay(2500); message = null }
        ForgeDialog(title = t("Done"), onDismiss = { message = null }, confirm = t("OK"), onConfirm = { message = null }, dismiss = "") {
            Text(m, style = T.Body, color = C.Mid)
        }
    }
}

@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Gap(14.dp)
    Text(title.uppercase(), style = T.H3, color = C.Hi)
    Gap(10.dp)
    Panel { content() }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, action: String, onFix: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.BodyM, color = C.Hi)
            Text(if (ok) t("On") else t("Off — reminders may not arrive"), style = T.Small, color = if (ok) C.Volt else C.Red)
        }
        if (!ok) SecondaryButton(action, onFix, height = 38.dp, color = C.Volt, border = C.Volt.copy(alpha = 0.5f))
    }
}
