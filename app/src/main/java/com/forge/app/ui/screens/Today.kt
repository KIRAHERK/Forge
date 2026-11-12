package com.forge.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forge.app.Graph
import com.forge.app.data.DayStatus
import com.forge.app.data.Fmt
import com.forge.app.data.HabitDef
import com.forge.app.data.HabitLog
import com.forge.app.data.PenaltyEntity
import com.forge.app.data.SessionEntity
import com.forge.app.data.Settings
import com.forge.app.data.SickCheck
import com.forge.app.data.Stats
import com.forge.app.domain.Lines
import com.forge.app.domain.PenaltyKind
import com.forge.app.domain.Penalties
import com.forge.app.domain.Program
import com.forge.app.domain.WorkoutDay
import com.forge.app.domain.Xp
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.UiEvents
import com.forge.app.ui.components.Loading
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Sparkline
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.MacroLine
import com.forge.app.ui.components.NumberDialog
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.Ring
import com.forge.app.ui.components.ScreenHeader
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.Segmented
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.rememberHaptic
import com.forge.app.ui.components.rememberNow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun TodayScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    // Read so the screen redraws when a program is edited.
    @Suppress("UNUSED_VARIABLE") val programs = com.forge.app.ui.LocalPrograms.current
    val scope = rememberCoroutineScope()
    val now by rememberNow(15_000)
    val today = now.toLocalDate()

    val sessionsN by repo.sessions.collectAsState(null)
    val active by repo.activeSession.collectAsState(null)
    val foods by remember(today) { repo.foodForDay(today) }.collectAsState(emptyList())
    val weightsN by repo.weights.collectAsState(null)
    val defsN by repo.habitDefs.collectAsState(null)
    val logsN by repo.habitLogs.collectAsState(null)
    val penaltiesN by repo.penalties.collectAsState(null)
    val xpN by repo.xpTotal.collectAsState(null)
    val sessions = sessionsN ?: return Loading()
    val weights = weightsN ?: return Loading()
    val habitDefs = defsN ?: return Loading()
    val habitLogs = logsN ?: return Loading()
    val penalties = penaltiesN ?: return Loading()
    val xp = xpN ?: return Loading()
    val weekStart = s.weekStartOf(today)
    val weekFoods by remember(weekStart) { repo.foodBetween(weekStart, weekStart.plusDays(6)) }.collectAsState(emptyList())

    val done = Stats.doneDates(sessions)
    val status = Stats.dayStatus(s, today, now, done)
    val day = s.dayFor(today)
    val macros = Stats.sum(foods)
    val todayWeight = weights.firstOrNull { it.date == today.toEpochDay() }
    val activePenalties = penalties.filter { it.status == "ACTIVE" }.sortedBy { it.dueAt }
    val makeUps = Stats.makeUpOpen(s, sessions, now)
    val streak = Stats.workoutStreak(s, sessions, now)
    val score = Stats.weekScore(s, weekStart, now, sessions, weekFoods, weights, habitDefs, habitLogs)
    val level = Xp.level(xp)

    var showWeigh by remember { mutableStateOf(false) }
    var habitDialog by remember { mutableStateOf<HabitDef?>(null) }
    var sickAsk by remember { mutableStateOf<LocalDate?>(null) }
    fun sickCheck(date: LocalDate) = Stats.canMarkSick(s, date, today, done)


    fun start(dayId: String, date: LocalDate, makeUp: Boolean) {
        scope.launch { nav.workout(repo.startSession(dayId, date, makeUp)) }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item {
            val week = s.programWeek(today)
            ScreenHeader(
                eyebrow = Fmt.longDay(today),
                title = "Week $week",
            ) {
                Box(Modifier.clip(CircleShape).clickable { nav.tab("progress") }) {
                    Ring(level.progress, size = 46.dp, stroke = 3.dp) {
                        Text("${level.level}", style = T.Num.copy(fontSize = T.Num.fontSize * 0.85f), color = C.Hi)
                    }
                }
                HGap(6.dp)
                IconBtn(Icons.Rounded.Settings, "Settings", { nav.go("settings") }, tint = C.Mid)
            }
        }

        // The line for the day sits up top, where it actually gets read.
        item {
            val seed = today.toEpochDay()
            val line = when {
                activePenalties.isNotEmpty() -> Lines.missed(seed)
                status == DayStatus.SICK -> "Sick day. Rest, eat, drink water. Back at it when you're well."
                status == DayStatus.DONE -> Lines.done(seed)
                status == DayStatus.PENDING && day != null -> Lines.preWorkout(seed, day.name)
                now.hour >= 18 && macros.protein < s.protein * 0.7 -> Lines.proteinLow(seed, (s.protein - macros.protein).roundToInt())
                day == null -> Lines.restDay(seed)
                streak >= 3 -> Lines.streak(seed, streak)
                else -> Lines.general(seed)
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(bottom = 18.dp)) {
                Box(Modifier.width(3.dp).fillMaxHeight().background(C.Volt))
                HGap(14.dp)
                Text(line, style = T.H3, color = C.Hi)
            }
        }

        item {
            UpdateBanner { nav.go("update") }
        }

        items(activePenalties, key = { "pen${it.id}" }) { p ->
            PenaltyCard(p, now, onSeen = { scope.launch { repo.markPenaltySeen(p.id) } }, onPaid = { scope.launch { repo.completePenalty(p.id) } })
            Gap(12.dp)
        }

        items(makeUps.filter { it != today || status == DayStatus.MAKEUP_OPEN }, key = { "mk$it" }) { date ->
            val d = s.dayFor(date) ?: return@items
            if (active == null) {
                MakeUpCard(
                    d, date, now, s, onStart = { start(d.id, date, true) },
                    sick = sickCheck(date), onSick = { sickAsk = date },
                )
                Gap(12.dp)
            }
        }

        item {
            val activeSets by remember(active?.id) { active?.let { repo.setsFor(it.id) } ?: kotlinx.coroutines.flow.flowOf(emptyList()) }
                .collectAsState(emptyList())
            HeroCard(
                s = s, now = now, status = status, day = day, active = active,
                activeProgress = activeSets.count { it.done } to activeSets.size,
                todaySession = sessions.firstOrNull { it.date == today.toEpochDay() && it.finishedAt != null && it.complete },
                onStart = { if (day != null) start(day.id, today, false) },
                onResume = { active?.let { nav.workout(it.id) } },
                onTrain = { nav.tab("train") },
                sick = sickCheck(today),
                onSick = { sickAsk = today },
                onUndoSick = { scope.launch { repo.setSick(today, false) } },
            )
            Gap(12.dp)
        }

        item {
            FuelCard(s, macros.kcal, macros.protein, macros.fat, macros.carbs,
                onOpen = { nav.tab("fuel") },
                onAdd = { UiEvents.addFoodRequest.value = true; nav.tab("fuel") },
                onRecipes = { nav.go("recipes") })
            Gap(12.dp)
        }

        item {
            CheckInCard(
                today = today, defs = habitDefs, logs = habitLogs,
                weightLogged = todayWeight?.kg,
                onWeigh = { showWeigh = true },
                onHabit = { def, v ->
                    scope.launch { repo.setHabit(def, today, v) }
                    // A counted slip opens the dialog so the number can be set.
                    if (def.isQuit && def.counted && v != 0) habitDialog = def
                },
                onUrge = { nav.go("urge") },
                onAddHabit = { nav.tab("habits") },
            )
            Gap(12.dp)
        }

        item {
            val latest = weights.lastOrNull()?.kg
            val avg = Stats.movingAverage(weights).lastOrNull()?.second ?: latest
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(14.dp), onClick = { nav.tab("train") }) {
                    Stat("Streak", "$streak", caption = if (streak == 1) "session" else "sessions", color = if (streak > 0) C.Volt else C.Hi)
                }
                Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(14.dp), onClick = { nav.tab("habits") }) {
                    Stat("Week", "${score.score}", unit = "/100", color = when {
                        score.score >= 80 -> C.Volt; score.score >= 55 -> C.Hi; else -> C.Red
                    })
                }
                Panel(Modifier.weight(1f).fillMaxHeight(), padding = PaddingValues(14.dp), onClick = { nav.go("weight") }) {
                    val toGo = avg?.let { (s.goalWeight - it).coerceAtLeast(0.0) }
                    Stat("To ${s.goalWeight.roundToInt()} kg", toGo?.let { Fmt.kg(it) } ?: "—", unit = if (toGo != null) "kg" else null)
                    Gap(8.dp)
                    Sparkline(Stats.movingAverage(weights).takeLast(21).map { it.second }, Modifier.fillMaxWidth().height(18.dp))
                }
            }
            BottomSpace()
        }
    }

    if (showWeigh) {
        val last = weights.lastOrNull()?.kg ?: s.startWeight
        WeighDialog(
            initialKg = todayWeight?.kg ?: last,
            onDismiss = { showWeigh = false },
            onSave = { d, kg -> scope.launch { repo.logWeight(d, kg) }; showWeigh = false },
        )
    }
    sickAsk?.let { date ->
        val left = Penalties.SICK_LIMIT - s.sickDays.count { it > today.toEpochDay() - Penalties.SICK_WINDOW_DAYS && it <= today.toEpochDay() }
        ForgeDialog(
            title = if (date == today) "Sick today?" else "Sick yesterday?",
            onDismiss = { sickAsk = null },
            confirm = "Mark sick",
            onConfirm = { sickAsk = null; scope.launch { repo.setSick(date, true) } },
        ) {
            Text(
                "${s.dayFor(date)?.title ?: "The workout"} is skipped with no penalty and no XP lost. " +
                    "Your streak pauses instead of resetting.",
                style = T.Body, color = C.Mid,
            )
            Gap(10.dp)
            Text(
                "Use it only when you're actually sick. After this you have ${left - 1} of ${Penalties.SICK_LIMIT} left in the next ${Penalties.SICK_WINDOW_DAYS} days.",
                style = T.Small, color = C.Lo,
            )
        }
    }
    habitDialog?.let { def ->
        HabitDayDialog(def, today, habitLogs.firstOrNull { it.habitId == def.id && it.date == today.toEpochDay() }, onDismiss = { habitDialog = null })
    }
}

// ------------------------------------------------------------------ cards

@Composable
private fun HeroCard(
    s: Settings,
    now: LocalDateTime,
    status: DayStatus,
    day: WorkoutDay?,
    active: SessionEntity?,
    activeProgress: Pair<Int, Int>,
    todaySession: SessionEntity?,
    onStart: () -> Unit,
    onResume: () -> Unit,
    onTrain: () -> Unit,
    sick: SickCheck,
    onSick: () -> Unit,
    onUndoSick: () -> Unit,
) {
    val today = now.toLocalDate()
    var sharing by remember { mutableStateOf(false) }
    if (sharing && todaySession != null) com.forge.app.ui.share.ShareWorkoutDialog(todaySession.id) { sharing = false }
    val next = (1..7).map { today.plusDays(it.toLong()) }.firstOrNull { s.isTraining(it) }
    val nextText = next?.let { "Next: ${s.dayFor(it)?.title} · ${Fmt.dow(it)}" }

    if (active != null) {
        val activeDay = Program.day(active.dayId)
        Panel(border = C.Volt.copy(alpha = 0.5f), accent = true) {
            Eyebrow(if (active.makeUp) "Make-up in progress" else "In progress", color = C.Volt)
            Gap(8.dp)
            Text(activeDay?.name?.uppercase() ?: "", style = T.Display, color = C.Hi)
            Gap(14.dp)
            com.forge.app.ui.components.Bar(if (activeProgress.second == 0) 0f else activeProgress.first.toFloat() / activeProgress.second)
            Gap(8.dp)
            Text("${activeProgress.first} of ${activeProgress.second} sets done", style = T.Small, color = C.Mid)
            Gap(16.dp)
            PrimaryButton("Resume workout", onResume, icon = Icons.Rounded.PlayArrow)
        }
        return
    }

    when {
        status == DayStatus.DONE -> {
            Panel(border = C.Volt.copy(alpha = 0.45f), accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(C.Volt), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Check, null, tint = C.OnVolt, modifier = Modifier.size(22.dp))
                    }
                    HGap(12.dp)
                    Column(Modifier.weight(1f)) {
                        Eyebrow("Today · done", color = C.Volt)
                        Text((day?.title ?: todaySession?.let { Program.day(it.dayId)?.title } ?: "Session").uppercase(), style = T.H2, color = C.Hi)
                    }
                    if (todaySession != null) com.forge.app.ui.share.ShareIcon { sharing = true }
                }
                todaySession?.let { ts ->
                    Gap(16.dp)
                    Row(Modifier.fillMaxWidth()) {
                        Stat("XP", "+${ts.xp}", Modifier.weight(1f), color = C.Volt)
                        Stat("Volume", Fmt.num(ts.volumeKg.roundToInt()), Modifier.weight(1f), unit = "kg")
                        Stat("Time", "${((ts.finishedAt ?: ts.startedAt) - ts.startedAt) / 60000}", Modifier.weight(1f), unit = "min")
                    }
                }
                if (nextText != null) {
                    Gap(14.dp)
                    Hairline()
                    Gap(12.dp)
                    Text(nextText, style = T.Small, color = C.Mid)
                }
            }
        }

        status == DayStatus.SICK -> {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Today · sick day", modifier = Modifier.weight(1f))
                    TextAction("Undo", onUndoSick, color = C.Mid, style = T.Small)
                }
                Gap(8.dp)
                Text("REST UP.", style = T.Display, color = C.Hi)
                Gap(6.dp)
                Text("${day?.title ?: "Today's workout"} is skipped. No penalty, and your streak is paused, not lost.", style = T.Body, color = C.Mid)
                if (nextText != null) {
                    Gap(14.dp)
                    Hairline()
                    Gap(12.dp)
                    Text(nextText, style = T.Small, color = C.Mid)
                }
            }
        }

        day == null -> {
            Panel(accent = true) {
                Eyebrow("Today")
                Gap(8.dp)
                Text("REST DAY", style = T.Display, color = C.Hi)
                if (nextText != null) {
                    Gap(16.dp)
                    Hairline()
                    Gap(12.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(nextText, style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
                        TextAction("Train anyway", onTrain)
                    }
                }
            }
        }

        status == DayStatus.PENDING -> {
            val deadline = s.deadlineAt(today)
            val minsLeft = java.time.Duration.between(now, deadline).toMinutes()
            val urgency = when {
                minsLeft <= 45 -> C.Red
                minsLeft <= 120 -> C.Amber
                else -> C.Volt
            }
            Panel(accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Eyebrow("Today · Day ${day.number}", modifier = Modifier.weight(1f))
                    Tag("Due ${Fmt.time(s.deadline)}", color = urgency, bg = urgency.copy(alpha = 0.14f))
                }
                Gap(10.dp)
                Text(day.name.uppercase(), style = T.Display, color = C.Hi)
                Gap(16.dp)
                Row(Modifier.fillMaxWidth()) {
                    Stat("Exercises", "${day.exercises.size}", Modifier.weight(1f))
                    Stat("Sets", "${day.totalSets}", Modifier.weight(1f))
                    Stat("Time left", Fmt.left(now, deadline), Modifier.weight(1.3f), color = urgency)
                }
                Gap(8.dp)
                Details("Exercises") {
                    day.exercises.forEach { pe ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(pe.displayName, style = T.Small, color = C.Hi, modifier = Modifier.weight(1f))
                            Text(pe.prescription, style = T.Small, color = C.Mid)
                        }
                    }
                }
                Gap(8.dp)
                PrimaryButton("Start workout", onStart, icon = Icons.Rounded.PlayArrow)
                SickRow(sick, "I'm sick today", onSick)
            }
        }

        else -> {
            Panel(color = Color(0xFF1C1212), border = C.Red.copy(alpha = 0.5f)) {
                Eyebrow("Today · missed", color = C.Red)
                Gap(8.dp)
                Text(day.name.uppercase(), style = T.Display, color = C.Hi)
                Gap(6.dp)
                Text("Missed at ${Fmt.time(s.deadline)}. You can still make it up.", style = T.Body, color = C.Mid)
                SickRow(sick, "I was sick today", onSick)
            }
        }
    }
}

@Composable
private fun PenaltyCard(p: PenaltyEntity, now: LocalDateTime, onSeen: () -> Unit, onPaid: () -> Unit) {
    val haptic = rememberHaptic()
    val kind = runCatching { PenaltyKind.valueOf(p.kind) }.getOrDefault(PenaltyKind.MISSED_WORKOUT)
    val reel = remember(kind) { Penalties.reel(kind) }
    var shown by remember(p.id) { mutableStateOf(if (p.seen) p.taskText else reel.first()) }
    var rolling by remember(p.id) { mutableStateOf(!p.seen) }
    var confirm by remember { mutableStateOf(false) }

    val overlay = UiEvents.overlayOpen.value
    LaunchedEffect(p.id, p.seen, overlay) {
        if (!p.seen && !overlay) {
            var wait = 60L
            repeat(18) { i ->
                shown = reel[i % reel.size]
                haptic(false)
                delay(wait)
                wait = (wait * 1.13).toLong()
            }
            shown = p.taskText
            rolling = false
            haptic(true)
            onSeen()
        }
    }

    val left = p.dueAt - Graph.repo.millis(now)
    Panel(color = Color(0xFF1C1212), border = C.Red.copy(alpha = 0.55f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(if (p.escalated) "Penalty · doubled" else "Penalty", color = C.Red, modifier = Modifier.weight(1f))
            Text(if (rolling) "rolling…" else "due in ${Fmt.leftMillis(left)}", style = T.Small, color = C.Red)
        }
        Gap(10.dp)
        AnimatedContent(
            targetState = shown,
            transitionSpec = { (slideInVertically { -it } + fadeIn()) togetherWith (slideOutVertically { it } + fadeOut()) },
            label = "reel",
        ) { txt ->
            Text(txt.uppercase(), style = T.H1, color = C.Hi)
        }
        Gap(6.dp)
        Text(p.reason, style = T.Small, color = C.Mid)
        if (p.jarAmount > 0) {
            Gap(4.dp)
            Text("+${p.jarAmount} to the penalty jar", style = T.Small, color = C.Amber)
        }
        Gap(14.dp)
        PrimaryButton("I paid it", { confirm = true }, color = C.Red, textColor = C.Hi, height = 48.dp, enabled = !rolling)
    }
    if (confirm) {
        ForgeDialog(
            title = "Paid in full?",
            onDismiss = { confirm = false },
            confirm = "Yes, done",
            onConfirm = { confirm = false; onPaid() },
            dismiss = "Not yet",
        ) {
            Text("${p.taskText}. All of it, not most of it.", style = T.Body, color = C.Mid)
        }
    }
}

@Composable
private fun MakeUpCard(day: WorkoutDay, date: LocalDate, now: LocalDateTime, s: Settings, onStart: () -> Unit, sick: SickCheck, onSick: () -> Unit) {
    val closes = s.deadlineAt(date).plusHours(Penalties.MAKE_UP_HOURS)
    Panel(color = Color(0xFF1C1810), border = C.Amber.copy(alpha = 0.45f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Make-up window", color = C.Amber, modifier = Modifier.weight(1f))
            Text("${Fmt.left(now, closes)} left", style = T.Small, color = C.Amber)
        }
        Gap(8.dp)
        Text(day.title.uppercase(), style = T.H2, color = C.Hi)
        Gap(4.dp)
        Text("Missed ${Fmt.day(date)}. Restores your streak.", style = T.Small, color = C.Mid)
        Gap(14.dp)
        PrimaryButton("Make it up", onStart, color = C.Amber, height = 48.dp)
        SickRow(sick, if (date == now.toLocalDate()) "I was sick today" else "I was sick yesterday", onSick)
    }
}

/** The sick-day action under a workout card, or why it isn't available. */
@Composable
private fun SickRow(sick: SickCheck, label: String, onSick: () -> Unit) {
    when (sick) {
        SickCheck.Allowed -> {
            Gap(4.dp)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                TextAction(label, onSick, color = C.Mid, style = T.Small)
            }
        }
        is SickCheck.LimitReached -> {
            Gap(8.dp)
            Text(
                "Sick days used: ${Penalties.SICK_LIMIT} of ${Penalties.SICK_LIMIT}. Next one frees up ${Fmt.day(sick.nextFree)}.",
                style = T.Tiny, color = C.Lo, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
        }
        SickCheck.NotAllowed -> {}
    }
}

@Composable
private fun FuelCard(s: Settings, kcal: Int, protein: Double, fat: Double, carbs: Double, onOpen: () -> Unit, onAdd: () -> Unit, onRecipes: () -> Unit) {
    Panel(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Fuel today", modifier = Modifier.weight(1f))
            val left = s.kcal - kcal
            Text(if (left > 0) "${Fmt.num(left)} kcal left" else "Target hit", style = T.Small, color = if (left > 0) C.Mid else C.Volt)
        }
        Gap(16.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Ring(kcal.toFloat() / s.kcal, size = 132.dp, stroke = 11.dp, color = C.Hi) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.num(kcal), style = T.NumL, color = C.Hi)
                    Text("of ${Fmt.num(s.kcal)} kcal", style = T.Tiny, color = C.Mid)
                }
            }
            Ring(protein.toFloat() / s.protein, size = 132.dp, stroke = 11.dp, color = C.Volt) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.g(protein), style = T.NumL, color = C.Volt)
                    Text("of ${s.protein} g protein", style = T.Tiny, color = C.Mid)
                }
            }
        }
        Gap(18.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(Modifier.weight(1f)) { MacroLine("Fat", "${Fmt.g(fat)} / ${s.fat} g", (fat / s.fat).toFloat(), color = C.Mid) }
            Box(Modifier.weight(1f)) { MacroLine("Carbs", "${Fmt.g(carbs)} / ${s.carbs} g", (carbs / s.carbs).toFloat(), color = C.Mid) }
        }
        Gap(16.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PrimaryButton("Add food", onAdd, Modifier.weight(1f), icon = Icons.Rounded.Add, height = 48.dp)
            SecondaryButton("Recipes", onRecipes, Modifier.weight(1f), height = 48.dp)
        }
    }
}

@Composable
private fun CheckInCard(
    today: LocalDate,
    defs: List<HabitDef>,
    logs: List<HabitLog>,
    weightLogged: Double?,
    onWeigh: () -> Unit,
    onHabit: (HabitDef, Int?) -> Unit,
    onUrge: () -> Unit,
    onAddHabit: () -> Unit,
) {
    Panel {
        Eyebrow("Daily check-in")
        Gap(6.dp)
        Row(Modifier.fillMaxWidth().clickable(onClick = onWeigh).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Morning weight", style = T.BodyM, color = C.Hi)
            }
            if (weightLogged != null) Text("${Fmt.kg(weightLogged)} kg", style = T.Num, color = C.Volt)
            else SecondaryButton("Log", onWeigh, height = 40.dp)
        }
        val d = today.toEpochDay()
        defs.filter { it.startDate <= d }.forEach { def ->
            Hairline()
            val log = logs.firstOrNull { it.habitId == def.id && it.date == d }
            val streak = Stats.habitStreak(def, logs, today)
            val sub = if (def.isQuit) {
                "$streak ${if (streak == 1) "day" else "days"} clean" + (log?.value?.takeIf { def.counted && it > 0 }?.let { " · $it today" } ?: "")
            } else {
                "$streak ${if (streak == 1) "day" else "days"} in a row"
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(def.name, style = T.BodyM, color = C.Hi, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(sub, style = T.Small, color = C.Mid)
                }
                HGap(8.dp)
                if (def.isQuit) {
                    // Clean is the good answer, so it sits first and in the accent colour.
                    Segmented(listOf("Clean", "Slipped"), when (log?.value) { null -> null; 0 -> 0; else -> 1 }, { i -> onHabit(def, if (i == 0) 0 else 1) })
                } else {
                    Segmented(listOf("Done", "Not yet"), when (log?.value) { null -> null; 0 -> 1; else -> 0 }, { i -> onHabit(def, if (i == 0) 1 else 0) })
                }
            }
        }
        if (defs.none { it.startDate <= d }) {
            Hairline()
            Text(
                "Add habits to check in on them here",
                style = T.Small, color = C.Volt,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAddHabit).padding(vertical = 12.dp),
            )
        }
        if (defs.any { it.isQuit }) {
            Gap(6.dp)
            Text(
                "Having an urge? Start the urge timer",
                style = T.Small, color = C.Volt,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onUrge).padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun UpdateBanner(onOpen: () -> Unit) {
    val st by com.forge.app.update.Updater.state.collectAsState()
    val version = when (val x = st) {
        is com.forge.app.update.Updater.State.Available -> x.info.version
        is com.forge.app.update.Updater.State.Ready -> x.info.version
        else -> null
    } ?: return
    Panel(onClick = onOpen, border = C.Volt.copy(alpha = 0.45f), padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Update available", color = C.Volt)
                Text("Forge v$version", style = T.H3, color = C.Hi)
            }
            Text("See what's new", style = T.Small, color = C.Volt)
        }
    }
    Gap(12.dp)
}
