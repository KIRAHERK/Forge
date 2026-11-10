package com.forge.app.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.Settings
import com.forge.app.domain.Plan
import com.forge.app.domain.Program
import com.forge.app.notify.Notifier
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.Segmented
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.components.ValueRow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun OnboardingScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(0) }
    var weekStart by remember { mutableStateOf(DayOfWeek.SATURDAY) }
    var workoutTime by remember { mutableIntStateOf(17 * 60) }
    var deadline by remember { mutableIntStateOf(21 * 60 + 30) }
    val habits = remember { androidx.compose.runtime.mutableStateListOf<Pair<String, String>>() }
    var habitName by remember { mutableStateOf("") }
    var habitQuit by remember { mutableStateOf(false) }
    var weight by remember { mutableStateOf(Fmt.kg(Plan.START_WEIGHT)) }
    var goal by remember { mutableStateOf(Fmt.kg(Plan.MILESTONE)) }
    var kcal by remember { mutableStateOf(Plan.KCAL.toString()) }
    var protein by remember { mutableStateOf(Plan.PROTEIN.toString()) }
    var timeEdit by remember { mutableStateOf<String?>(null) }
    var refresh by remember { mutableIntStateOf(0) }

    val owner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh++ }

    fun finish() {
        fun num(t: String) = t.replace(',', '.').toDoubleOrNull()
        val w = num(weight)?.takeIf { it in 30.0..250.0 }
        scope.launch {
            Graph.repo.completeOnboarding({
                it.copy(
                    startWeight = w ?: it.startWeight,
                    goalWeight = num(goal)?.takeIf { g -> g in 30.0..250.0 } ?: it.goalWeight,
                    kcal = num(kcal)?.toInt()?.coerceIn(1200, 6000) ?: it.kcal,
                    protein = num(protein)?.toInt()?.coerceIn(30, 400) ?: it.protein,
                ).copy(
                    weekStart = weekStart.value,
                    schedule = Settings.cycleFrom(weekStart),
                    workoutTime = workoutTime,
                    deadline = deadline,
                )
            }, weight.replace(',', '.').toDoubleOrNull(), habits.toList())
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..3).forEach { i ->
                Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= step) C.Volt else C.S3))
            }
        }
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(320)) { it / 4 * dir } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 4 * dir } + fadeOut(tween(200)))
            },
            modifier = Modifier.weight(1f),
            label = "onboarding",
        ) { st ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
                when (st) {
                    0 -> Intro()
                    1 -> {
                        Gap(12.dp)
                        Eyebrow("Step 2 of 4")
                        Gap(6.dp)
                        Text("WHEN DO\nYOU TRAIN?", style = T.Hero.copy(fontSize = 52.sp, lineHeight = 50.sp), color = C.Hi)
                        Gap(18.dp)
                        Eyebrow("Your week starts on")
                        Gap(8.dp)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).forEach { d ->
                                Chip(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), weekStart == d, { weekStart = d })
                            }
                        }
                        Gap(16.dp)
                        Panel {
                            Eyebrow("Your training week")
                            Gap(8.dp)
                            val sched = Settings.cycleFrom(weekStart)
                            (0..6).forEach { i ->
                                val d = weekStart.plus(i.toLong())
                                val slot = sched[d.value - 1]
                                val day = Program.day(slot)
                                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                    Text(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH), style = T.BodyM, color = C.Mid, modifier = Modifier.width(52.dp))
                                    Text(day?.title ?: "Rest", style = T.BodyM, color = if (day != null) C.Hi else C.Lo)
                                }
                            }
                        }
                        Gap(14.dp)
                        Panel {
                            ValueRow("Workout reminder", Fmt.time(workoutTime)) { timeEdit = "workout" }
                            Hairline()
                            ValueRow("Deadline", Fmt.time(deadline), "Not done by then counts as a miss", valueColor = C.Red) { timeEdit = "deadline" }
                        }
                        Gap(8.dp)
                        Text("You can change all of this later in Settings.", style = T.Small, color = C.Lo)
                    }
                    2 -> {
                        Gap(12.dp)
                        Eyebrow("Step 3 of 4")
                        Gap(6.dp)
                        Text("YOUR NUMBERS.\nYOUR HABITS.", style = T.Hero.copy(fontSize = 52.sp, lineHeight = 50.sp), color = C.Hi)
                        Gap(18.dp)
                        Eyebrow("Your numbers")
                        Gap(8.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ForgeField(weight, { weight = it }, "Weight today", Modifier.weight(1f), number = true, suffix = "kg")
                            ForgeField(goal, { goal = it }, "Milestone", Modifier.weight(1f), number = true, suffix = "kg")
                        }
                        Gap(10.dp)
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ForgeField(kcal, { kcal = it }, "Calories / day", Modifier.weight(1f), number = true, suffix = "kcal")
                            ForgeField(protein, { protein = it }, "Protein / day", Modifier.weight(1f), number = true, suffix = "g")
                        }
                        Gap(6.dp)
                        Text("Prefilled from the built-in plan. Change them to yours; fat and carbs are in Settings. Weigh in tomorrow morning for a clean first reading.", style = T.Small, color = C.Lo)
                        Gap(22.dp)
                        Eyebrow("Habits (optional)")
                        Gap(6.dp)
                        Text("Something to build daily, or something to quit. You can add more later in the Habits tab.", style = T.Small, color = C.Mid)
                        Gap(10.dp)
                        Panel {
                            habits.forEachIndexed { i, (name, kind) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, style = T.BodyM, color = C.Hi, modifier = Modifier.weight(1f))
                                    Text(if (kind == com.forge.app.data.HabitDef.KIND_QUIT) "Quit" else "Build", style = T.Small, color = C.Mid)
                                    HGap(8.dp)
                                    TextAction("Remove", { habits.removeAt(i) }, color = C.Lo, style = T.Small)
                                }
                                Hairline()
                            }
                            if (habits.isNotEmpty()) Gap(10.dp)
                            ForgeField(habitName, { habitName = it.take(40) }, "Habit name")
                            Gap(10.dp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Segmented(listOf("Build", "Quit"), if (habitQuit) 1 else 0, { habitQuit = it == 1 }, colors = listOf(C.Volt, C.Amber))
                                HGap(10.dp)
                                SecondaryButton("Add", {
                                    if (habitName.isNotBlank()) {
                                        habits.add(habitName.trim() to if (habitQuit) com.forge.app.data.HabitDef.KIND_QUIT else com.forge.app.data.HabitDef.KIND_BUILD)
                                        habitName = ""
                                    }
                                }, Modifier.weight(1f), height = 44.dp)
                            }
                        }
                    }
                    else -> {
                        val notifOk = remember(refresh) { Notifier.canPost(ctx) }
                        val batteryOk = remember(refresh) { isIgnoringBattery(ctx) }
                        Gap(12.dp)
                        Eyebrow("Step 4 of 4")
                        Gap(6.dp)
                        Text("LET IT\nPUSH YOU.", style = T.Hero.copy(fontSize = 52.sp, lineHeight = 50.sp), color = C.Hi)
                        Gap(14.dp)
                        Text("Reminders are the point. Without these permissions the app can't warn you before a deadline.", style = T.Body, color = C.Mid)
                        Gap(18.dp)
                        Panel {
                            PermRow("Notifications", "Reminders, deadlines, penalties", notifOk) {
                                if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                            }
                            Hairline()
                            PermRow("Battery: unrestricted", "Stops the phone from killing reminders", batteryOk) { requestBatteryExemption(ctx) }
                        }
                        Gap(14.dp)
                        Text(
                            "On Xiaomi, Samsung, Huawei or Oppo also enable Autostart for Forge in system settings.",
                            style = T.Small, color = C.Lo,
                        )
                    }
                }
                Gap(24.dp)
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step > 0) {
                TextAction("Back", { step-- }, color = C.Mid, style = T.Button)
                HGap(12.dp)
            }
            PrimaryButton(
                when (step) { 0 -> "Set it up"; 3 -> "Start"; else -> "Continue" },
                { if (step < 3) step++ else finish() },
                Modifier.weight(1f),
            )
        }
    }

    timeEdit?.let { which ->
        TimeDialog(
            if (which == "workout") "Workout reminder" else "Deadline",
            if (which == "workout") workoutTime else deadline,
            { timeEdit = null },
        ) { v ->
            if (which == "workout") {
                workoutTime = v
                if (deadline < v + 30) deadline = (v + 120).coerceAtMost(23 * 60 + 30)
            } else deadline = v.coerceAtLeast(workoutTime + 30)
            timeEdit = null
        }
    }
}

@Composable
private fun Intro() {
    Gap(20.dp)
    Eyebrow("Forge", color = C.Volt)
    Gap(8.dp)
    Text("YOUR PLAN.\nENFORCED.", style = T.Hero.copy(fontSize = 64.sp, lineHeight = 60.sp), color = C.Hi)
    Gap(14.dp)
    Text("A 4-day home muscle program and a lean-bulk nutrition plan, with reminders, deadlines and consequences.", style = T.Body, color = C.Mid)
    Gap(24.dp)
    Panel {
        Row(Modifier.fillMaxWidth()) {
            Stat("Workouts", "4", Modifier.weight(1f), unit = "/ week")
            Stat("Calories", Fmt.num(Plan.KCAL), Modifier.weight(1f), unit = "/ day")
        }
        Gap(16.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Protein", "${Plan.PROTEIN}", Modifier.weight(1f), unit = "g / day", color = C.Volt)
            Stat("Milestone", "${Plan.START_WEIGHT.toInt()}→${Plan.MILESTONE.toInt()}", Modifier.weight(1f), unit = "kg")
        }
    }
    Gap(20.dp)
    listOf(
        "Reminds you before every session and warns you before the deadline.",
        "Logs every set and tells you when to add weight.",
        "Tracks calories, protein and morning weight against the plan.",
        "Tracks the habits you choose, every day.",
        "Misses cost you: penalties that double if ignored, streaks that reset, XP you lose.",
    ).forEach {
        Row(Modifier.padding(vertical = 6.dp)) {
            Box(Modifier.padding(top = 8.dp).size(6.dp).clip(RoundedCornerShape(1.dp)).background(C.Volt))
            HGap(12.dp)
            Text(it, style = T.Body, color = C.Hi)
        }
    }
}

@Composable
private fun PermRow(title: String, sub: String, ok: Boolean, onAllow: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = T.BodyM, color = C.Hi)
            Text(sub, style = T.Small, color = C.Mid)
        }
        if (ok) Text("ON", style = T.Button, color = C.Volt)
        else SecondaryButton("Allow", onAllow, height = 40.dp, color = C.Volt, border = C.Volt.copy(alpha = 0.5f))
    }
}
