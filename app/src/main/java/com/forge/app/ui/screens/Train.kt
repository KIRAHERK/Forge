package com.forge.app.ui.screens

import com.forge.app.i18n.t

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sick
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forge.app.Graph
import com.forge.app.data.DayStatus
import com.forge.app.data.Fmt
import com.forge.app.data.SessionEntity
import com.forge.app.data.SetEntity
import com.forge.app.data.Stats
import com.forge.app.domain.Exercises
import com.forge.app.domain.Program
import com.forge.app.domain.Progression
import com.forge.app.domain.WorkoutDay
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.Loading
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.ScreenHeader
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Sparkline
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TextAction
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.rememberNow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

@Composable
fun TrainScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val now by rememberNow(30_000)
    val today = now.toLocalDate()
    val sessionsN by repo.sessions.collectAsState(null)
    val sessions = sessionsN ?: return Loading()
    val active by repo.activeSession.collectAsState(null)
    val done = Stats.doneDates(sessions)
    var pick by remember { mutableStateOf<WorkoutDay?>(null) }
    val programs = com.forge.app.ui.LocalPrograms.current
    val program = programs?.active

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item {
            ScreenHeader(program?.name?.let { t(it) } ?: t("Program"), t("Train")) {
                TextAction(t("Programs"), { nav.go("programs") })
            }
        }

        item {
            WeekStrip(s.weekStartOf(today), today) { d -> Stats.dayStatus(s, d, now, done) to s.slotFor(d) }
            Gap(14.dp)
        }

        if (active != null) {
            item {
                Panel(border = C.Volt.copy(alpha = 0.5f), onClick = { nav.workout(active!!.id) }, accent = true) {
                    Eyebrow(t("In progress"), color = C.Volt)
                    Gap(6.dp)
                    Text(Program.day(active!!.dayId)?.title?.uppercase() ?: "", style = T.H2, color = C.Hi)
                    Gap(12.dp)
                    PrimaryButton(t("Resume"), { nav.workout(active!!.id) }, icon = Icons.Rounded.PlayArrow, height = 48.dp)
                }
                Gap(14.dp)
            }
        }

        item {
            SectionTitle(
                t("The program · %s days a week", program?.daysPerWeek ?: 0),
                action = if (program != null) t("Edit") else null,
                onAction = { program?.let { nav.go("program/${it.id}") } },
            )
        }

        items(program?.days.orEmpty(), key = { it.id }) { day ->
            DayCard(day, onExercise = { nav.exercise(it) }, onStart = { pick = day })
            Gap(12.dp)
        }
        if (program != null && program.days.isEmpty()) {
            item {
                Panel(onClick = { nav.go("program/${program.id}") }) {
                    Text(t("NO WORKOUTS YET"), style = T.H3, color = C.Hi)
                    Gap(4.dp)
                    Text(t("Add days and exercises to this program."), style = T.Small, color = C.Mid)
                }
                Gap(12.dp)
            }
        }

        item {
            Panel(padding = PaddingValues(horizontal = 18.dp, vertical = 4.dp)) {
                Details(t("Training rules")) {
                    Text(Exercises.EFFORT_RULE, style = T.Body, color = C.Hi)
                    Gap(8.dp)
                    Text(Exercises.PROGRESSION_RULE, style = T.Body, color = C.Mid)
                    Gap(8.dp)
                    Text(Program.REST_NOTE, style = T.Body, color = C.Mid)
                }
            }
            Gap(12.dp)
        }

        item {
            SectionTitle(t("Recent sessions"), action = if (sessions.any { it.finishedAt != null }) t("All") else null, onAction = { nav.go("history") })
            val recent = sessions.filter { it.finishedAt != null }.take(4)
            if (recent.isEmpty()) {
                Text(t("Nothing logged yet."), style = T.Small, color = C.Lo)
            }
            recent.forEach { SessionRow(it); Gap(8.dp) }
            BottomSpace()
        }
    }

    pick?.let { day ->
        val todaySlot = s.slotFor(today)
        val makeUps = Stats.makeUpOpen(s, sessions, now)
        val makeUpDate = makeUps.firstOrNull { s.slotFor(it) == day.id }
        val creditDate = makeUpDate ?: today
        val isMakeUp = makeUpDate != null && makeUpDate != today || (makeUpDate == today && Stats.dayStatus(s, today, now, done) == DayStatus.MAKEUP_OPEN)
        ForgeDialog(
            title = t("Start %s?", day.title),
            onDismiss = { pick = null },
            confirm = t("Start"),
            onConfirm = {
                pick = null
                scope.launch { nav.workout(repo.startSession(day.id, creditDate, isMakeUp)) }
            },
        ) {
            val msg = when {
                isMakeUp -> t("This counts as the make-up for %s and restores your streak.", Fmt.day(creditDate))
                todaySlot == day.id -> t("This is today's scheduled session.")
                todaySlot == Program.REST -> t("Today is a rest day. This is logged as an extra session.")
                else -> t("Today's scheduled session is %s. This one is logged in its place for today.", s.dayFor(today)?.title)
            }
            Text(msg, style = T.Body, color = C.Mid)
        }
    }
}

@Composable
fun WeekStrip(weekStart: LocalDate, today: LocalDate, info: (LocalDate) -> Pair<DayStatus, String>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (0..6).forEach { i ->
            val d = weekStart.plusDays(i.toLong())
            val (st, slot) = info(d)
            val isToday = d == today
            val (bg, fg, border) = when (st) {
                DayStatus.DONE -> Triple(C.Volt, C.OnVolt, C.Volt)
                DayStatus.MISSED, DayStatus.MAKEUP_OPEN -> Triple(C.RedSoft, C.Red, C.Red.copy(alpha = 0.6f))
                DayStatus.SICK -> Triple(C.S3, C.Mid, if (isToday) C.Hi else C.LineStrong)
                else -> Triple(C.S1, if (slot == Program.REST) C.Lo else C.Hi, if (isToday) C.Hi else C.Line)
            }
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(bg)
                    .border(if (isToday) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp)).padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(Fmt.dowLetter(d), style = T.Tiny, color = if (st == DayStatus.DONE) C.OnVolt else C.Mid)
                Gap(4.dp)
                if (st == DayStatus.SICK) {
                    Icon(Icons.Rounded.Sick, t("Sick day"), tint = fg, modifier = Modifier.padding(vertical = 2.dp).size(20.dp))
                } else {
                    Text(
                        if (slot == Program.REST) "–" else (Program.day(slot)?.number?.toString() ?: "•"),
                        style = T.Num.copy(fontSize = T.Num.fontSize * 0.9f), color = fg, textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCard(day: WorkoutDay, onExercise: (String) -> Unit, onStart: () -> Unit) {
    Panel(padding = PaddingValues(0.dp)) {
        Row(Modifier.padding(start = 18.dp, end = 12.dp, top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow(t("Day %1${'$'}s · %2${'$'}s sets · ~%3${'$'}s min", day.number, day.totalSets, day.estMinutes))
                Gap(4.dp)
                Text(t(day.name).uppercase(), style = T.H2, color = C.Hi)
            }
            TextAction(t("Start"), onStart)
        }
        day.exercises.forEachIndexed { i, pe ->
            Hairline(Modifier.padding(horizontal = 18.dp))
            Row(
                Modifier.fillMaxWidth().clickable { onExercise(pe.exerciseId) }.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${i + 1}", style = T.Num.copy(fontSize = T.Num.fontSize * 0.8f), color = C.Lo, modifier = Modifier.width(24.dp))
                Text(pe.displayName, style = T.BodyM, color = C.Hi, modifier = Modifier.weight(1f))
                Text(pe.prescription, style = T.Small, color = C.Mid)
                HGap(4.dp)
                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = C.Lo)
            }
        }
        Gap(6.dp)
    }
}

@Composable
fun SessionRow(ses: SessionEntity) {
    val day = Program.day(ses.dayId)
    var sharing by remember { mutableStateOf(false) }
    Panel(padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day?.title ?: ses.dayId, style = T.BodyS, color = C.Hi)
                Text(
                    t("%1${'$'}s · %2${'$'}s min · %3${'$'}s kg", Fmt.day(LocalDate.ofEpochDay(ses.date)), ses.minutes(), Fmt.num(ses.volumeKg.roundToInt())),
                    style = T.Small, color = C.Mid,
                )
            }
            if (!ses.complete) { Tag(t("Partial"), color = C.Red, bg = C.RedSoft); HGap(6.dp) }
            if (ses.makeUp) { Tag(t("Make-up"), color = C.Amber, bg = C.AmberSoft); HGap(6.dp) }
            if (ses.prs > 0) { Tag(t("%s PR", ses.prs), color = C.OnVolt, bg = C.Volt); HGap(6.dp) }
            Text("+${ses.xp}", style = T.Num.copy(fontSize = T.Num.fontSize * 0.8f), color = C.Volt)
            HGap(2.dp)
            com.forge.app.ui.share.ShareIcon { sharing = true }
        }
    }
    if (sharing) com.forge.app.ui.share.ShareWorkoutDialog(ses.id) { sharing = false }
}

@Composable
fun HistoryScreen(nav: Nav) {
    val sessionsN by Graph.repo.sessions.collectAsState(null)
    val sessions = sessionsN ?: return Loading()
    Column(Modifier.fillMaxSize()) {
        TopBar(t("Session history"), "${sessions.count { it.finishedAt != null }} sessions", onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
            items(sessions.filter { it.finishedAt != null }, key = { it.id }) { SessionRow(it); Gap(8.dp) }
            item { BottomSpace() }
        }
    }
}

@Composable
fun ExerciseScreen(nav: Nav, exerciseId: String) {
    val ex = runCatching { Exercises.byId(exerciseId) }.getOrNull() ?: return
    val repo = Graph.repo
    val data by produceState<Pair<List<SetEntity>, Map<Long, SessionEntity>>?>(null, exerciseId) {
        val sets = repo.db.sets().historyFor(exerciseId)
        val sessions = repo.db.sessions().allNow().associateBy { it.id }
        value = sets to sessions
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(ex.name, ex.muscles, onBack = { nav.back() })
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp)) {
            Gap(8.dp)
            val d = data
            if (d != null && d.first.isNotEmpty()) {
                val bySession = d.first.groupBy { it.sessionId }.toList()
                val best = d.first.maxBy { Progression.e1rm(it.reps, it.weightKg) }
                Panel {
                    Eyebrow(t("Your numbers"))
                    Gap(10.dp)
                    Row(Modifier.fillMaxWidth()) {
                        Stat(t("Best set"), if (ex.loaded || best.weightKg > 0) "${best.reps}×${Progression.fmtKg(best.weightKg)}" else "${best.reps}", Modifier.weight(1f), color = C.Volt)
                        Stat(t("Sessions"), "${bySession.size}", Modifier.weight(1f))
                    }
                    Gap(16.dp)
                    val series = bySession.mapNotNull { (sid, sets) ->
                        val date = d.second[sid]?.date ?: return@mapNotNull null
                        date to sets.maxOf { if (ex.loaded || it.weightKg > 0) Progression.e1rm(it.reps, it.weightKg) else it.reps.toDouble() }
                    }.groupBy { it.first }.map { (date, l) -> date to l.maxOf { it.second } }.sortedBy { it.first }
                    Eyebrow(if (ex.loaded) t("Estimated 1-rep max, kg") else t("Best reps per session"))
                    Gap(10.dp)
                    com.forge.app.ui.components.LineChart(
                        points = emptyList(), line = series, height = 140.dp,
                        decimals = if (ex.loaded) 1 else 0,
                        empty = t("Log a second session to see the trend."),
                    )
                    Gap(16.dp)
                    Eyebrow(t("Recent sessions"))
                    Gap(6.dp)
                    bySession.takeLast(5).reversed().forEach { (sid, sets) ->
                        val ses = d.second[sid]
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Text(ses?.let { Fmt.day(LocalDate.ofEpochDay(it.date)) } ?: "", style = T.Small, color = C.Mid, modifier = Modifier.width(96.dp))
                            Text(
                                sets.joinToString("  ") { if (ex.loaded || it.weightKg > 0) "${it.reps}×${Progression.fmtKg(it.weightKg)}" else "${it.reps}" },
                                style = T.Small, color = C.Hi,
                            )
                        }
                    }
                }
                Gap(20.dp)
            }
            GuideContent(ex)
            Gap(40.dp)
        }
    }
}

