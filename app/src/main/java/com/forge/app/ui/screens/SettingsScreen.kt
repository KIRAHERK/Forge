package com.forge.app.ui.screens

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
import com.forge.app.ui.components.Chip
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
                TextAction("CANCEL", onDismiss, color = C.Mid, style = T.Button)
                TextAction("SET", { onConfirm(state.hour * 60 + state.minute) }, style = T.Button)
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
            message = if (ok) "Backup saved." else "Couldn't save the backup."
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            importText = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (importText == null) message = "Couldn't read that file."
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar("Settings", onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {

            // ------------------------------------------------ schedule
            Group("Schedule") {
                Eyebrow("Week starts on")
                Gap(8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    weekdays.forEach { d ->
                        Chip(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), s.weekStart == d.value, {
                            upd {
                                val n = it.copy(weekStart = d.value, schedule = Settings.cycleFrom(d))
                                n.copy(programStart = n.weekStartOf(LocalDate.ofEpochDay(it.trackingStart)).toEpochDay())
                            }
                        })
                    }
                }
                Gap(14.dp)
                Eyebrow("Each day")
                Gap(4.dp)
                weekdays.forEachIndexed { i, d ->
                    val slot = s.schedule.getOrElse(d.value - 1) { Program.REST }
                    var open by remember { mutableStateOf(false) }
                    if (i > 0) Hairline()
                    Box {
                        Row(
                            Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = T.BodyM, color = C.Mid, modifier = Modifier.width(52.dp))
                            Text(
                                Program.day(slot)?.title ?: "Rest",
                                style = T.BodyM, color = if (slot == Program.REST) C.Lo else C.Hi,
                                modifier = Modifier.weight(1f), maxLines = 1,
                            )
                            Icon(Icons.Rounded.ExpandMore, null, tint = C.Mid)
                        }
                        DropdownMenu(open, { open = false }, containerColor = C.S2) {
                            (Program.days.map { it.id } + Program.REST).forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            Program.day(opt)?.title ?: "Rest",
                                            style = T.BodyM, color = if (opt == slot) C.Volt else C.Hi,
                                        )
                                    },
                                    onClick = {
                                        open = false
                                        upd { st -> st.copy(schedule = st.schedule.toMutableList().also { it[d.value - 1] = opt }) }
                                    },
                                )
                            }
                        }
                    }
                }
                val count = s.schedule.count { it != Program.REST }
                if (count != Program.WORKOUTS_PER_WEEK) {
                    Gap(6.dp)
                    Text("The program is ${Program.WORKOUTS_PER_WEEK} workouts a week. You have $count scheduled.", style = T.Small, color = C.Amber)
                }
            }

            // ------------------------------------------------ reminders
            Group("Reminders") {
                ValueRow("Workout reminder", Fmt.time(s.workoutTime), "Training days only") {
                    timeEdit = Triple("Workout reminder", s.workoutTime) { v -> upd { it.copy(workoutTime = v) } }
                }
                Hairline()
                ValueRow("Deadline", Fmt.time(s.deadline), "Warnings at ${Fmt.time(Scheduler.nudgeMinutes(s))} and ${Fmt.time(Scheduler.finalMinutes(s))}", valueColor = C.Red) {
                    timeEdit = Triple("Deadline", s.deadline) { v -> upd { it.copy(deadline = v.coerceAtLeast(it.workoutTime + 30)) } }
                }
                Hairline()
                ValueRow("Morning weigh-in", Fmt.time(s.weighInTime)) {
                    timeEdit = Triple("Morning weigh-in", s.weighInTime) { v -> upd { it.copy(weighInTime = v) } }
                }
                Hairline()
                ValueRow("Food check 1", Fmt.time(s.proteinCheck1), "Only if you're behind") {
                    timeEdit = Triple("Food check 1", s.proteinCheck1) { v -> upd { it.copy(proteinCheck1 = v) } }
                }
                Hairline()
                ValueRow("Food check 2", Fmt.time(s.proteinCheck2), "Only if you're behind") {
                    timeEdit = Triple("Food check 2", s.proteinCheck2) { v -> upd { it.copy(proteinCheck2 = v) } }
                }
                Hairline()
                ValueRow("Night check-in", Fmt.time(s.nightTime), "Log habits and food") {
                    timeEdit = Triple("Night check-in", s.nightTime) { v -> upd { it.copy(nightTime = v) } }
                }
                Gap(8.dp)
                ToggleRow("Workout reminders", null, s.remindWorkout) { v -> upd { it.copy(remindWorkout = v) } }
                ToggleRow("Weigh-in reminder", null, s.remindWeighIn) { v -> upd { it.copy(remindWeighIn = v) } }
                ToggleRow("Food checks", null, s.remindNutrition) { v -> upd { it.copy(remindNutrition = v) } }
                ToggleRow("Night check-in", null, s.remindNight) { v -> upd { it.copy(remindNight = v) } }
                ToggleRow("Weekly review", "Score at the end of each week", s.remindWeekly) { v -> upd { it.copy(remindWeekly = v) } }
            }

            // ------------------------------------------------ accountability
            Group("Accountability") {
                ToggleRow("Penalties", "Missed workouts and habit slips issue a penalty. Ignore it and it doubles.", s.penaltiesOn) { v -> upd { it.copy(penaltiesOn = v) } }
                Hairline()
                ValueRow(
                    "Penalty jar",
                    if (s.jarAmount == 0) "Off" else "${s.jarAmount} ${s.jarCurrency}".trim(),
                    "Money you set aside for every miss or slip",
                    valueColor = if (s.jarAmount == 0) C.Mid else C.Amber,
                ) {
                    numEdit = Triple("Amount per miss", s.jarAmount.toString()) { v -> upd { it.copy(jarAmount = v.toInt().coerceIn(0, 100000)) } }
                }
                if (s.jarAmount > 0) {
                    var cur by remember(s.jarCurrency) { mutableStateOf(s.jarCurrency) }
                    ForgeField(cur, { cur = it.take(6); upd { st -> st.copy(jarCurrency = cur) } }, "Currency (e.g. EGP, USD)")
                    Gap(8.dp)
                }
            }

            // ------------------------------------------------ habits
            Group("Habits") {
                ValueRow("Your habits", "Manage", "Add, rename or delete them in the Habits tab") { nav.tab("habits") }
            }

            // ------------------------------------------------ targets
            Group("Targets") {
                ValueRow("Calories", "${Fmt.num(s.kcal)} kcal") { numEdit = Triple("Calories", s.kcal.toString()) { v -> upd { it.copy(kcal = v.toInt().coerceIn(1200, 6000)) } } }
                Hairline()
                ValueRow("Protein", "${s.protein} g") { numEdit = Triple("Protein (g)", s.protein.toString()) { v -> upd { it.copy(protein = v.toInt().coerceIn(30, 400)) } } }
                Hairline()
                ValueRow("Fat", "${s.fat} g") { numEdit = Triple("Fat (g)", s.fat.toString()) { v -> upd { it.copy(fat = v.toInt().coerceIn(10, 300)) } } }
                Hairline()
                ValueRow("Carbs", "${s.carbs} g") { numEdit = Triple("Carbs (g)", s.carbs.toString()) { v -> upd { it.copy(carbs = v.toInt().coerceIn(20, 900)) } } }
                Hairline()
                ValueRow("Milestone weight", "${Fmt.kg(s.goalWeight)} kg") { numEdit = Triple("Milestone (kg)", Fmt.kg(s.goalWeight)) { v -> upd { it.copy(goalWeight = v.coerceIn(40.0, 200.0)) } } }
                Gap(6.dp)
                TextAction("Reset to plan: ${Fmt.num(Plan.KCAL)} kcal · ${Plan.PROTEIN} g · ${Plan.FAT} g · ${Plan.CARBS} g", {
                    upd { it.copy(kcal = Plan.KCAL, protein = Plan.PROTEIN, fat = Plan.FAT, carbs = Plan.CARBS, goalWeight = Plan.MILESTONE) }
                }, style = T.Small)
                Gap(10.dp)
                var note by remember(s.planNote) { mutableStateOf(s.planNote) }
                ForgeField(note, { note = it }, "Plan notes (e.g. a doctor's protein limit)", singleLine = false)
                if (note != s.planNote) {
                    Gap(6.dp)
                    TextAction("Save notes", { scope.launch { Graph.settings.update { it.copy(planNote = note.trim()) } } }, style = T.Small)
                }
                Text("Stays on this phone. Shown in Fuel → Your nutrition plan.", style = T.Tiny, color = C.Lo)
            }

            // ------------------------------------------------ updates
            Group("Updates") {
                ValueRow("Version", "v${com.forge.app.update.Updater.currentVersion}", "Check GitHub for a newer release") { nav.go("update") }
                Hairline()
                ToggleRow("Check daily", "Get a notification when a new version is out", s.autoUpdateCheck) { v -> upd { it.copy(autoUpdateCheck = v) } }
            }

            // ------------------------------------------------ reliability
            Group("Make sure reminders arrive") {
                val notifOk = remember(refresh) { Notifier.canPost(ctx) }
                val exactOk = remember(refresh) { Scheduler.canExact(ctx) }
                val batteryOk = remember(refresh) { isIgnoringBattery(ctx) }
                StatusRow("Notifications", notifOk, "Allow") {
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                }
                Hairline()
                StatusRow("Exact timing", exactOk, "Allow") {
                    if (Build.VERSION.SDK_INT >= 31) runCatching {
                        ctx.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
                    }
                }
                Hairline()
                StatusRow("Not battery-restricted", batteryOk, "Allow") { requestBatteryExemption(ctx) }
                Gap(8.dp)
                Text(
                    "Xiaomi, Samsung, Huawei, Oppo and similar phones kill background apps. If reminders don't arrive, enable Autostart for Forge and set its battery use to Unrestricted.",
                    style = T.Small, color = C.Mid,
                )
                Gap(10.dp)
                SecondaryButton("Send a test notification", {
                    Notifier.post(ctx, Notifier.ID_TEST, Notifier.CH_WORKOUT, "Test", "If you can read this, reminders work.")
                }, Modifier.fillMaxWidth(), height = 46.dp)
            }

            // ------------------------------------------------ data
            Group("Data") {
                Text("Everything is stored on this phone only. Updates keep it in place; backups cover the rest.", style = T.Small, color = C.Mid)
                Gap(6.dp)
                ToggleRow("Daily automatic backup", "Saved in the app and in Downloads/Forge (Android 10+)", s.autoBackup) { v -> upd { it.copy(autoBackup = v) } }
                if (s.lastBackupAt > 0) {
                    Text(
                        "Last backup: ${java.text.SimpleDateFormat("EEE d MMM, HH:mm", Locale.ENGLISH).format(java.util.Date(s.lastBackupAt))}" +
                            (if (s.lastBackupReason.isNotBlank()) " · ${s.lastBackupReason}" else ""),
                        style = T.Small, color = C.Lo,
                    )
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Back up now", {
                        scope.launch { message = if (runCatching { com.forge.app.data.AutoBackup.run(ctx, "manual") }.getOrDefault(false)) "Backup saved." else "Backup failed." }
                    }, Modifier.weight(1f), height = 46.dp)
                    SecondaryButton("Restore last", {
                        scope.launch {
                            val f = com.forge.app.data.AutoBackup.latestFile(ctx)
                            if (f.exists()) importText = f.readText() else message = "No automatic backup on this phone yet."
                        }
                    }, Modifier.weight(1f), height = 46.dp)
                }
                Gap(10.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Export file", { exportLauncher.launch("forge-backup-${LocalDate.now()}.json") }, Modifier.weight(1f), height = 46.dp)
                    SecondaryButton("Import file", { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.weight(1f), height = 46.dp)
                }
            }

            Group("About") {
                Text("Forge v${com.forge.app.update.Updater.currentVersion}", style = T.BodyS, color = C.Hi)
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
                SecondaryButton("Source code", {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/${com.forge.app.update.Updater.repo}"))) }
                }, Modifier.fillMaxWidth(), height = 46.dp)
                com.forge.app.ui.components.Details("Sources and license") {
                    Text(
                        "Ships with a 4-day home muscle program and a lean-bulk nutrition plan; their sets, reps and targets are used as written. " +
                            "Exercise cues were compiled from coaching references (ACE, NASM, Motra and others). Food values come from USDA FoodData Central, " +
                            "published dish studies and Open Food Facts.\n\nOpen source under the MIT license. Copyright ${com.forge.app.Credits.AUTHOR}.",
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
            title = "Replace all data?",
            onDismiss = { importText = null },
            confirm = "Replace",
            destructive = true,
            onConfirm = {
                importText = null
                scope.launch {
                    message = if (runCatching { Graph.repo.importJson(txt) }.isSuccess) "Backup restored." else "That file isn't a valid Forge backup."
                }
            },
        ) {
            Text("Everything currently in the app is replaced by the backup. This can't be undone.", style = T.Body, color = C.Mid)
        }
    }
    message?.let { m ->
        LaunchedEffect(m) { kotlinx.coroutines.delay(2500); message = null }
        ForgeDialog(title = "Done", onDismiss = { message = null }, confirm = "OK", onConfirm = { message = null }, dismiss = "") {
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
            Text(if (ok) "On" else "Off — reminders may not arrive", style = T.Small, color = if (ok) C.Volt else C.Red)
        }
        if (!ok) SecondaryButton(action, onFix, height = 38.dp, color = C.Volt, border = C.Volt.copy(alpha = 0.5f))
    }
}
