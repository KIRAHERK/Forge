package com.forge.app.ui.screens

import com.forge.app.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.app.Graph
import com.forge.app.data.DayStatus
import com.forge.app.data.Fmt
import com.forge.app.data.HabitDef
import com.forge.app.data.HabitLog
import com.forge.app.data.PenaltyEntity
import com.forge.app.data.Stats
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.DayDots
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.Loading
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.Ring
import com.forge.app.ui.components.ScreenHeader
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Segmented
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.rememberNow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate

private enum class Cell { HIT, MISS, REST, OPEN, UNKNOWN, BLANK, SICK }

@Composable
fun HabitsScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val now by rememberNow(30_000)
    val today = now.toLocalDate()
    var weekOffset by remember { mutableIntStateOf(0) }
    val weekStart = s.weekStartOf(today).plusWeeks(weekOffset.toLong())
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }

    val sessionsN by repo.sessions.collectAsState(null)
    val weightsN by repo.weights.collectAsState(null)
    val defsN by repo.habitDefs.collectAsState(null)
    val logsN by repo.habitLogs.collectAsState(null)
    val penaltiesN by repo.penalties.collectAsState(null)
    val sessions = sessionsN ?: return Loading()
    val weights = weightsN ?: return Loading()
    val defs = defsN ?: return Loading()
    val logs = logsN ?: return Loading()
    val penalties = penaltiesN ?: return Loading()
    val weekFoods by remember(weekStart) { repo.foodBetween(weekStart, weekStart.plusDays(6)) }.collectAsState(emptyList())

    val evalNow = if (weekOffset == 0) now else weekStart.plusDays(6).atTime(23, 59)
    val score = Stats.weekScore(s, weekStart, evalNow, sessions, weekFoods, weights, defs, logs)
    val done = Stats.doneDates(sessions)
    val logMap = logs.associateBy { it.habitId to it.date }
    val weightDays = weights.map { it.date }.toSet()

    var dayDialog by remember { mutableStateOf<Pair<HabitDef, LocalDate>?>(null) }
    var editing by remember { mutableStateOf<HabitDef?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun foodCell(d: LocalDate, hit: (Double, Int) -> Boolean): Cell {
        if (d.isAfter(today) || d.toEpochDay() < s.trackingStart) return Cell.BLANK
        val m = Stats.sum(weekFoods.filter { it.date == d.toEpochDay() })
        return when {
            hit(m.protein, m.kcal) -> Cell.HIT
            d == today -> Cell.OPEN
            else -> Cell.MISS
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item {
            ScreenHeader(t("Tracked daily, no exceptions"), t("Habits")) {
                IconBtn(Icons.Rounded.Add, t("Add a habit"), { adding = true }, tint = C.Volt)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, t("Previous week"), { weekOffset-- }, tint = C.Mid)
                Text(
                    when (weekOffset) { 0 -> t("THIS WEEK"); -1 -> t("LAST WEEK"); else -> "${Fmt.day(weekStart).uppercase()} – ${Fmt.day(weekStart.plusDays(6)).uppercase()}" },
                    style = T.H3, color = C.Hi, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconBtn(Icons.AutoMirrored.Rounded.KeyboardArrowRight, t("Next week"), { if (weekOffset < 0) weekOffset++ }, tint = if (weekOffset < 0) C.Mid else C.S3)
            }
            Gap(10.dp)
        }

        item {
            Panel(accent = true) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val col = when { score.score >= 80 -> C.Volt; score.score >= 55 -> C.Hi; else -> C.Red }
                    Ring(score.score / 100f, size = 112.dp, stroke = 9.dp, color = col) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            com.forge.app.ui.components.RingCenter("${score.score}", t("week score"), col, 112.dp, valueMax = 34.sp, captionMax = 11.sp)
                        }
                    }
                    HGap(18.dp)
                    Column(Modifier.weight(1f)) {
                        ScoreLine(t("Workouts"), "${score.workoutsDone}/${score.workoutsDue}", score.workoutsDue > 0 && score.workoutsDone == score.workoutsDue)
                        ScoreLine(t("Protein"), "${score.proteinDays}/${score.proteinDen}", score.proteinDen > 0 && score.proteinDays >= score.proteinDen)
                        ScoreLine(t("Calories"), "${score.kcalDays}/${score.kcalDen}", score.kcalDen > 0 && score.kcalDays >= score.kcalDen)
                        if (defs.isNotEmpty()) {
                            ScoreLine(t("Habits kept"), "${score.cleanDays}/${score.cleanDen}", score.cleanDen > 0 && score.cleanDays >= score.cleanDen)
                        }
                        ScoreLine(t("Weigh-ins"), "${score.weighIns}/${score.weighDen}", score.weighDen > 0 && score.weighIns >= score.weighDen)
                    }
                }
            }
            Gap(12.dp)
        }

        item {
            Panel(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 10.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(96.dp))
                    days.forEach { d ->
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(Fmt.dowLetter(d), style = T.Tiny, color = if (d == today) C.Hi else C.Mid)
                            Text("${d.dayOfMonth}", style = T.Tiny, color = if (d == today) C.Volt else C.Lo)
                        }
                    }
                }
                Gap(8.dp)
                GridRow(t("Workout"), days) { d ->
                    when (Stats.dayStatus(s, d, now, done)) {
                        DayStatus.DONE -> Cell.HIT
                        DayStatus.MISSED, DayStatus.MAKEUP_OPEN -> Cell.MISS
                        DayStatus.REST -> Cell.REST
                        DayStatus.SICK -> Cell.SICK
                        DayStatus.PENDING, DayStatus.FUTURE -> Cell.OPEN
                        DayStatus.BEFORE -> Cell.BLANK
                    } to null
                }
                GridRow(t("Calories"), days) { d -> foodCell(d) { _, k -> Stats.kcalHit(k, s.kcal) } to null }
                GridRow(t("Protein"), days) { d -> foodCell(d) { p, _ -> Stats.proteinHit(p, s.protein) } to null }
                GridRow(t("Weigh-in"), days) { d ->
                    when {
                        d.isAfter(today) || d.toEpochDay() < s.trackingStart -> Cell.BLANK
                        d.toEpochDay() in weightDays -> Cell.HIT
                        d == today -> Cell.OPEN
                        else -> Cell.MISS
                    } to null
                }
                defs.forEach { def ->
                    GridRow(def.name, days, onClick = { d -> if (!d.isAfter(today) && d.toEpochDay() >= def.startDate) dayDialog = def to d }) { d ->
                        val log = logMap[def.id to d.toEpochDay()]
                        val cell = when {
                            d.isAfter(today) || d.toEpochDay() < def.startDate -> Cell.BLANK
                            else -> when (Stats.kept(def, log)) {
                                true -> Cell.HIT
                                false -> Cell.MISS
                                null -> if (d == today) Cell.OPEN else Cell.UNKNOWN
                            }
                        }
                        cell to log?.value?.takeIf { def.isQuit && def.counted && it > 0 }?.toString()
                    }
                }
                Gap(6.dp)
                Text(
                    if (defs.isEmpty()) t("Add a habit to track it here.") else t("Tap a habit cell to change that day."),
                    style = T.Tiny, color = C.Lo,
                )
            }
            Gap(12.dp)
        }

        item { SectionTitle(t("Your habits"), action = t("Add"), onAction = { adding = true }) }

        if (defs.isEmpty()) {
            item {
                Panel {
                    Text(t("NOTHING TRACKED YET"), style = T.H3, color = C.Hi)
                    Gap(4.dp)
                    Text(t("Something to build, or something to quit."), style = T.Small, color = C.Mid)
                    Gap(14.dp)
                    PrimaryButton(t("Add a habit"), { adding = true }, icon = Icons.Rounded.Add, height = 48.dp)
                }
                Gap(12.dp)
            }
        }

        items(defs, key = { "def${it.id}" }) { def ->
            HabitCard(def, logs, today, weekStart) { editing = def }
            Gap(10.dp)
        }

        if (defs.any { it.isQuit }) {
            item {
                Gap(2.dp)
                Panel(onClick = { nav.go("urge") }) {
                    Text(t("HAVING AN URGE?"), style = T.H2, color = C.Hi)
                    Gap(12.dp)
                    PrimaryButton(t("Open urge timer"), { nav.go("urge") }, height = 46.dp)
                }
                Gap(12.dp)
            }
        }

        val visible = penalties.filter { it.status != "VOID" }
        val owed = visible.filter { it.jarAmount > 0 && !it.jarPaid }.sumOf { it.jarAmount }
        if (s.jarAmount > 0 || owed > 0) {
            item {
                Panel(color = Color(0xFF1C1810), border = C.Amber.copy(alpha = 0.4f)) {
                    Eyebrow(t("Penalty jar"), color = C.Amber)
                    Gap(6.dp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$owed", style = T.Display, color = C.Hi)
                        HGap(6.dp)
                        Text("${s.jarCurrency} owed".trim(), style = T.Small, color = C.Mid, modifier = Modifier.padding(bottom = 6.dp))
                    }
                    Details(t("How the jar works")) {
                        Text(t("Every miss and slip adds %s. Put it aside for real: savings or charity.", "${s.jarAmount}" + (if (s.jarCurrency.isNotBlank()) " " + s.jarCurrency else "")), style = T.Small, color = C.Mid)
                    }
                    if (owed > 0) {
                        Gap(12.dp)
                        SecondaryButton(t("I put it aside"), { scope.launch { repo.payJar() } }, Modifier.fillMaxWidth(), color = C.Amber, border = C.Amber.copy(alpha = 0.5f), height = 46.dp)
                    }
                }
                Gap(12.dp)
            }
        }

        item { SectionTitle(t("Penalty record")) }
        if (visible.isEmpty()) {
            item { Text(t("Clean record. Keep it that way."), style = T.Small, color = C.Lo) }
        }
        items(visible.take(20), key = { it.id }) { p ->
            PenaltyRow(p)
            Gap(8.dp)
        }
        item { BottomSpace() }
    }

    dayDialog?.let { (def, d) ->
        HabitDayDialog(def, d, logMap[def.id to d.toEpochDay()], onDismiss = { dayDialog = null })
    }
    if (adding) HabitEditor(null, onDismiss = { adding = false })
    editing?.let { def -> HabitEditor(def, onDismiss = { editing = null }) }
}

@Composable
private fun ScoreLine(label: String, value: String, good: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
        Text(value, style = T.Small, color = if (good) C.Volt else C.Hi)
    }
}

@Composable
private fun GridRow(label: String, days: List<LocalDate>, onClick: ((LocalDate) -> Unit)? = null, cell: (LocalDate) -> Pair<Cell, String?>) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.Small, color = C.Hi, modifier = Modifier.width(96.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
        days.forEach { d ->
            val (c, txt) = cell(d)
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                HabitCell(c, txt, onClick?.let { { it(d) } })
            }
        }
    }
}

@Composable
private fun HabitCell(c: Cell, text: String?, onClick: (() -> Unit)?) {
    val shape = RoundedCornerShape(8.dp)
    val base = Modifier.widthIn(max = 32.dp).fillMaxWidth(0.86f).aspectRatio(1f).clip(shape)
    val m = when (c) {
        Cell.HIT -> base.background(C.Volt)
        Cell.MISS -> base.background(C.RedSoft).border(1.dp, C.Red.copy(alpha = 0.6f), shape)
        Cell.OPEN -> base.border(1.dp, C.LineStrong, shape)
        Cell.UNKNOWN -> base.border(1.dp, C.Line, shape)
        Cell.SICK -> base.background(C.S3)
        Cell.REST, Cell.BLANK -> base
    }
    Box((if (onClick != null) m.clickable(onClick = onClick) else m), contentAlignment = Alignment.Center) {
        when (c) {
            Cell.HIT -> Icon(Icons.Rounded.Check, null, tint = C.OnVolt, modifier = Modifier.size(16.dp))
            Cell.MISS -> if (text != null) Text(text, style = T.Tiny.copy(fontSize = 12.sp), color = C.Red) else Icon(Icons.Rounded.Close, null, tint = C.Red, modifier = Modifier.size(14.dp))
            Cell.REST -> Text("–", style = T.Small, color = C.Lo)
            Cell.SICK -> Icon(Icons.Rounded.Sick, t("Sick day"), tint = C.Mid, modifier = Modifier.size(16.dp))
            Cell.UNKNOWN -> Text("?", style = T.Tiny, color = C.Lo)
            else -> {}
        }
    }
}

@Composable
private fun HabitCard(def: HabitDef, logs: List<HabitLog>, today: LocalDate, weekStart: LocalDate, onEdit: () -> Unit) {
    val streak = Stats.habitStreak(def, logs, today)
    val best = Stats.habitBest(def, logs, today)
    Panel(onClick = onEdit, padding = PaddingValues(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(if (def.isQuit) t("Quit") else t("Build"), color = if (def.isQuit) C.Amber else C.Volt, bg = (if (def.isQuit) C.Amber else C.Volt).copy(alpha = 0.14f))
                }
                Gap(6.dp)
                Text(def.name, style = T.H3, color = C.Hi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(t("Best: %s", best), style = T.Small, color = C.Lo)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$streak", style = T.hero(48, 46), color = if (streak > 0) C.Volt else C.Hi)
                Text(
                    if (def.isQuit) (if (streak == 1) t("day clean") else t("days clean")) else (if (streak == 1) t("day in a row") else t("days in a row")),
                    style = T.Tiny, color = C.Mid,
                )
            }
        }
        // Last 4 weeks at a glance, oldest on the left.
        Gap(12.dp)
        val logByDay = logs.filter { it.habitId == def.id }.associateBy { it.date }
        DayDots((27 downTo 0).map { back ->
            val d = today.minusDays(back.toLong()).toEpochDay()
            when {
                d < def.startDate -> C.S2
                else -> when (Stats.kept(def, logByDay[d])) {
                    true -> C.Volt
                    false -> C.Red.copy(alpha = 0.75f)
                    null -> C.S3
                }
            }
        })
        Gap(4.dp)
        Row {
            Text(t("4 weeks ago"), style = T.Tiny, color = C.Lo, modifier = Modifier.weight(1f))
            Text(t("Today"), style = T.Tiny, color = C.Lo)
        }
        if (def.isQuit && def.counted) {
            val ws = weekStart.toEpochDay()
            val thisWeek = logs.filter { it.habitId == def.id && it.date in ws..ws + 6 }.sumOf { it.value ?: 0 }
            val lastWeek = logs.filter { it.habitId == def.id && it.date in ws - 7 until ws }.sumOf { it.value ?: 0 }
            Gap(10.dp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("%1${'$'}s this week · %2${'$'}s last week", thisWeek, lastWeek), style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
                val trend = when {
                    thisWeek == 0 && lastWeek == 0 -> t("Zero") to C.Volt
                    thisWeek < lastWeek -> t("Down") to C.Volt
                    thisWeek > lastWeek -> t("Up") to C.Red
                    else -> t("Flat") to C.Mid
                }
                Tag(trend.first, color = trend.second, bg = trend.second.copy(alpha = 0.14f))
            }
        }
    }
}

@Composable
private fun PenaltyRow(p: PenaltyEntity) {
    val (label, color) = when (p.status) {
        "ACTIVE" -> t("Active") to C.Red
        "DONE" -> t("Paid") to C.Volt
        "FAILED" -> t("Failed") to C.Red
        else -> p.status to C.Mid
    }
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.taskText, style = T.BodyS, color = if (p.status == "DONE") C.Mid else C.Hi)
                Text(p.reason + if (p.escalated) t(" · doubled") else "", style = T.Small, color = C.Lo)
            }
            HGap(8.dp)
            Tag(label, color = color, bg = color.copy(alpha = 0.14f))
        }
    }
}

/** Log or correct one day of one habit. */
@Composable
fun HabitDayDialog(def: HabitDef, date: LocalDate, log: HabitLog?, onDismiss: () -> Unit) {
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    var value by remember(log) { mutableStateOf(log?.value) }
    fun set(v: Int?) { value = v; scope.launch { repo.setHabit(def, date, v) } }

    ForgeDialog(title = def.name, onDismiss = onDismiss, confirm = t("Done"), onConfirm = onDismiss, dismiss = t("Close")) {
        Text(Fmt.day(date), style = T.Small, color = C.Mid)
        Gap(10.dp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (def.isQuit) {
                Segmented(listOf(t("Clean"), t("Slipped")), when (value) { null -> null; 0 -> 0; else -> 1 }, { i ->
                    set(if (i == 0) 0 else (value?.takeIf { it > 0 } ?: 1))
                })
            } else {
                Segmented(listOf(t("Done"), t("Not done")), when (value) { null -> null; 0 -> 1; else -> 0 }, { i ->
                    set(if (i == 0) 1 else 0)
                }, colors = listOf(C.Volt, C.Red))
            }
            HGap(8.dp)
            if (value == null) Text(t("not logged"), style = T.Tiny, color = C.Lo)
            else TextAction(t("Clear"), { set(null) }, color = C.Mid, style = T.Small)
        }
        if (def.isQuit && def.counted && (value ?: 0) > 0) {
            Gap(10.dp)
            Text(t("How many times?"), style = T.Small, color = C.Mid)
            Gap(6.dp)
            Stepper(
                "${value}",
                onMinus = { set(((value ?: 1) - 1).coerceAtLeast(1)) },
                onPlus = { set((value ?: 0) + 1) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Create a habit (def == null) or edit one. */
@Composable
fun HabitEditor(def: HabitDef?, onDismiss: () -> Unit) {
    val repo = Graph.repo
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(def?.name ?: "") }
    var quit by remember { mutableStateOf(def?.isQuit ?: false) }
    var counted by remember { mutableStateOf(def?.counted ?: false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val valid = name.isNotBlank()

    ForgeDialog(
        title = if (def == null) t("New habit") else t("Edit habit"),
        onDismiss = onDismiss,
        confirm = if (def == null) t("Add") else t("Save"),
        onConfirm = {
            if (valid) {
                scope.launch {
                    if (def == null) repo.addHabit(name, if (quit) HabitDef.KIND_QUIT else HabitDef.KIND_BUILD, counted)
                    else repo.updateHabit(def.copy(name = name, counted = counted))
                }
                onDismiss()
            }
        },
    ) {
        ForgeField(name, { name = it.take(40) }, t("Name"))
        Gap(12.dp)
        if (def == null) {
            Segmented(listOf(t("Build"), t("Quit")), if (quit) 1 else 0, { quit = it == 1 }, colors = listOf(C.Volt, C.Amber))
            Gap(8.dp)
            Text(
                if (quit) t("Something to stay clean of. Each day you log clean or slipped. A slip resets the streak and issues a penalty.")
                else t("Something to do every day. Each day you log done or not. The streak counts days in a row."),
                style = T.Small, color = C.Mid,
            )
        } else {
            Text(if (quit) t("Quit habit") else t("Build habit"), style = T.Small, color = C.Mid)
        }
        if (quit) {
            Gap(4.dp)
            ToggleRow(t("Count how many times"), t("Ask for a number when you slip"), counted) { counted = it }
        }
        if (def != null) {
            Gap(8.dp)
            TextAction(t("Delete habit"), { confirmDelete = true }, color = C.Red, style = T.Small)
        }
    }

    if (confirmDelete && def != null) {
        ForgeDialog(
            title = t("Delete %s?", def.name),
            onDismiss = { confirmDelete = false },
            confirm = t("Delete"),
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch { repo.deleteHabit(def.id) }
                onDismiss()
            },
        ) {
            Text(t("Its history is deleted. Penalties already issued stay on your record."), style = T.Body, color = C.Mid)
        }
    }
}

// ------------------------------------------------------------------ urge timer

private const val URGE_MS = 5 * 60_000L

@Composable
fun UrgeScreen(nav: Nav) {
    val repo = Graph.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val defsN by repo.habitDefs.collectAsState(null)
    val defs = defsN ?: return Loading()
    val quits = defs.filter { it.isQuit }
    var selected by remember { mutableStateOf<Long?>(null) }
    val habit = quits.firstOrNull { it.id == selected } ?: quits.firstOrNull()
    var running by remember { mutableStateOf(false) }
    var endsAt by remember { mutableLongStateOf(0L) }
    var remaining by remember { mutableLongStateOf(URGE_MS) }
    var confirmGaveIn by remember { mutableStateOf(false) }
    var resisted by remember { mutableStateOf(false) }

    LaunchedEffect(running, endsAt) {
        while (running) {
            remaining = (endsAt - System.currentTimeMillis()).coerceAtLeast(0)
            if (remaining == 0L) {
                running = false
                vibrate(ctx)
            }
            delay(250)
        }
    }

    val steps = listOf(
        t("Stand up and change rooms."),
        t("Drink a full glass of cold water, slowly."),
        t("20 push-ups or 30 squats. Right now."),
        t("Breathe in for 4 seconds, out for 6. Ten times."),
        t("Go outside, call someone, or keep your hands busy until the timer ends."),
    )

    Column(Modifier.fillMaxSize()) {
        TopBar(t("Urge timer"), t("Wait it out. It passes."), onBack = { nav.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (quits.size > 1) {
                Gap(8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    quits.forEach { q -> Chip(q.name, q.id == habit?.id, { selected = q.id }) }
                }
            }
            Gap(24.dp)
            Ring(1f - remaining.toFloat() / URGE_MS, size = 232.dp, stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(Fmt.clock((remaining / 1000).toInt()), style = T.Hero, color = C.Hi)
                    Text(
                        when {
                            resisted -> "logged"
                            running -> t("hold on")
                            remaining == 0L -> t("it passed")
                            else -> t("5 minutes")
                        },
                        style = T.Small, color = C.Mid,
                    )
                }
            }
            Gap(22.dp)
            if (!running && remaining == URGE_MS) {
                PrimaryButton(t("Start the timer"), { endsAt = System.currentTimeMillis() + URGE_MS; running = true })
                Gap(18.dp)
            }
            Text(
                t("An urge rises, peaks and fades — usually within a few minutes. You don't have to fight it. You only have to not act on it until it passes."),
                style = T.Body, color = C.Mid, textAlign = TextAlign.Center,
            )
            Gap(20.dp)
            Panel {
                Eyebrow(t("Do this now"))
                Gap(8.dp)
                steps.forEachIndexed { i, st ->
                    Row(Modifier.padding(vertical = 5.dp)) {
                        Text("${i + 1}", style = T.Num.copy(fontSize = 16.sp), color = C.Volt, modifier = Modifier.width(22.dp))
                        Text(st, style = T.Body, color = C.Hi)
                    }
                }
            }
            Gap(24.dp)
        }
        if (habit != null) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton(t("I gave in"), { confirmGaveIn = true }, Modifier.weight(1f), color = C.Red, border = C.Red.copy(alpha = 0.5f), height = 54.dp)
                PrimaryButton(if (resisted) t("Logged · +10 XP") else t("I resisted"), {
                    if (!resisted) {
                        resisted = true
                        running = false
                        scope.launch { repo.urgeResisted(habit.id, LocalDate.now()); delay(900); nav.back() }
                    }
                }, Modifier.weight(1f), height = 54.dp)
            }
        } else {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
                Text(t("Add a habit you're quitting to log urges against it."), style = T.Small, color = C.Mid)
                Gap(8.dp)
                SecondaryButton(t("Go to Habits"), { nav.tab("habits") }, Modifier.fillMaxWidth(), height = 50.dp)
            }
        }
    }

    if (confirmGaveIn && habit != null) {
        ForgeDialog(
            title = t("Log it honestly"),
            onDismiss = { confirmGaveIn = false },
            confirm = t("Log slip"),
            destructive = true,
            onConfirm = {
                confirmGaveIn = false
                scope.launch {
                    val today = LocalDate.now()
                    val cur = Graph.db.habits().log(habit.id, today.toEpochDay())?.value ?: 0
                    repo.setHabit(habit, today, if (habit.counted) cur + 1 else 1)
                    nav.back()
                }
            },
            dismiss = t("Back"),
        ) {
            Text(t("The streak for %s resets and a penalty is issued. Hiding it changes nothing — logging it keeps the count real.", habit.name), style = T.Body, color = C.Mid)
        }
    }
}
