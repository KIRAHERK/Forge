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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.app.Graph
import com.forge.app.data.CheckIn
import com.forge.app.data.Fmt
import com.forge.app.data.Stats
import com.forge.app.domain.Plan
import com.forge.app.domain.Program
import com.forge.app.domain.Progression
import com.forge.app.domain.Xp
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.Loading
import com.forge.app.ui.components.Bar
import com.forge.app.ui.components.BarChart
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.NumberDialog
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.ScreenHeader
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Sparkline
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.WeightChart
import com.forge.app.ui.components.rememberNow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
fun ProgressScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val scope = rememberCoroutineScope()
    val now by rememberNow(60_000)
    val today = now.toLocalDate()
    val weightsN by repo.weights.collectAsState(null)
    val sessionsN by repo.sessions.collectAsState(null)
    val xpN by repo.xpTotal.collectAsState(null)
    val weights = weightsN ?: return Loading()
    val sessions = sessionsN ?: return Loading()
    val xp = xpN ?: return Loading()
    val xpRecent by repo.xpRecent.collectAsState(emptyList())
    val doneSets by repo.allDoneSets.collectAsState(emptyList())
    val measurements by repo.measurements.collectAsState(emptyList())
    var showWeigh by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }

    val finished = sessions.filter { it.finishedAt != null && it.complete }
    val avgSeries = Stats.movingAverage(weights)
    val currentAvg = avgSeries.lastOrNull()?.second
    val rate = Stats.weeklyRate(weights, today)
    val level = Xp.level(xp)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp)) {
        item { ScreenHeader(t("%1${'$'}s → %2${'$'}s kg", s.startWeight.roundToInt(), s.goalWeight.roundToInt()), t("Progress")) }

        // ---------------- weight
        item {
            Panel(accent = true) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Eyebrow(t("7-day average"))
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(currentAvg?.let { Fmt.kg(it) } ?: "—", style = T.Display, color = C.Hi)
                            if (currentAvg != null) Text(t(" kg"), style = T.H3, color = C.Mid, modifier = Modifier.padding(bottom = 4.dp))
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Eyebrow(t("Per week"))
                        val col = when {
                            rate == null -> C.Mid
                            rate < Plan.RATE_MIN - 0.05 -> C.Red
                            rate > Plan.RATE_MAX + 0.15 -> C.Amber
                            else -> C.Volt
                        }
                        Text(rate?.let { (if (it >= 0) "+" else "") + String.format(Locale.US, "%.2f", it) } ?: "—", style = T.NumL, color = col)
                        Text(t("target %1${'$'}s–%2${'$'}s", Plan.RATE_MIN, Plan.RATE_MAX), style = T.Tiny, color = C.Lo)
                    }
                    HGap(6.dp)
                    com.forge.app.ui.share.ShareIcon { sharing = true }
                }
                Gap(14.dp)
                WeightChart(weights.map { it.date to it.kg }, avgSeries, goal = s.goalWeight)
                Gap(16.dp)
                val start = s.startWeight
                val cur = currentAvg ?: weights.lastOrNull()?.kg ?: start
                val p = ((cur - start) / (s.goalWeight - start)).toFloat()
                Row {
                    Text(t("%s kg", Fmt.kg(start)), style = T.Tiny, color = C.Mid, modifier = Modifier.weight(1f))
                    Text(t("%s kg", Fmt.kg(s.goalWeight)), style = T.Tiny, color = C.Volt)
                }
                Gap(6.dp)
                Bar(p, height = 8.dp)
                Gap(8.dp)
                val gained = cur - start
                val toGo = (s.goalWeight - cur).coerceAtLeast(0.0)
                Text(t("%1${'$'}s kg so far · %2${'$'}s kg to go", (if (gained >= 0) "+" else "") + Fmt.kg(gained), Fmt.kg(toGo)), style = T.Small, color = C.Hi)
                val eta = if (rate != null && rate > 0.05 && toGo > 0) {
                    val weeks = ceil(toGo / rate).toLong()
                    "At this rate: about $weeks weeks (${today.plusWeeks(weeks).format(DateTimeFormatter.ofPattern("MMM yyyy", com.forge.app.i18n.Lang.locale))}). Plan estimate: ${Plan.TIMELINE.lowercase()}."
                } else t("Plan estimate: %s. Needs two weeks of weigh-ins for your own estimate.", Plan.TIMELINE.lowercase())
                Details(t("When will I get there?")) {
                    Text(eta, style = T.Small, color = C.Mid)
                }
                Gap(6.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (weights.none { it.date == today.toEpochDay() }) {
                        PrimaryButton(t("Log weight"), { showWeigh = true }, Modifier.weight(1f), height = 46.dp)
                    }
                    SecondaryButton(t("Weight log"), { nav.go("weight") }, Modifier.weight(1f), height = 46.dp)
                }
            }
            Gap(12.dp)
        }

        // ---------------- 2-week check-in
        if (Stats.checkInDue(s, today)) {
            item {
                val ci = Stats.checkIn(weights, today)
                CheckInPanel(ci,
                    onAdjust = { deltaKcal ->
                        scope.launch {
                            Graph.settings.update {
                                it.copy(
                                    kcal = it.kcal + deltaKcal,
                                    carbs = (it.carbs + deltaKcal / 4).coerceAtLeast(0),
                                    lastCheckInAck = today.toEpochDay(),
                                )
                            }
                        }
                    },
                    onAck = { scope.launch { Graph.settings.update { it.copy(lastCheckInAck = today.toEpochDay()) } } },
                )
                Gap(12.dp)
            }
        }

        // ---------------- weekly training charts
        item {
            WeeklyTrainingPanel(s, finished, today)
            Gap(12.dp)
        }

        // ---------------- level
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${level.level}", style = T.Hero, color = C.Hi)
                    HGap(14.dp)
                    Column(Modifier.weight(1f)) {
                        Eyebrow(t("Level · %s XP total", xp))
                        Text(level.title.uppercase(), style = T.H2, color = C.Volt)
                        Text(t("%1${'$'}s / %2${'$'}s XP to level %3${'$'}s", level.into, level.needed, level.level + 1), style = T.Small, color = C.Mid)
                    }
                }
                Gap(10.dp)
                Bar(level.progress)
                if (xpRecent.isNotEmpty()) {
                    Gap(6.dp)
                    Details(t("Recent XP")) {
                        xpRecent.take(8).forEach { e ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Text(e.reason, style = T.Small, color = C.Mid, modifier = Modifier.weight(1f), maxLines = 1)
                                Text((if (e.amount > 0) "+" else "") + e.amount, style = T.Small, color = if (e.amount >= 0) C.Volt else C.Red)
                            }
                        }
                    }
                }
            }
            Gap(12.dp)
        }

        // ---------------- totals
        item {
            Panel {
                Eyebrow(t("All time"))
                Gap(10.dp)
                Row(Modifier.fillMaxWidth()) {
                    Stat(t("Workouts"), "${finished.size}", Modifier.weight(1f))
                    Stat(t("Sets"), Fmt.num(doneSets.size), Modifier.weight(1f))
                    Stat(t("Volume"), String.format(Locale.US, "%.1f", finished.sumOf { it.volumeKg } / 1000), Modifier.weight(1f), unit = t("t"))
                    Stat(t("PRs"), "${finished.sumOf { it.prs }}", Modifier.weight(0.8f), color = C.Volt)
                }
            }
            Gap(12.dp)
        }

        // ---------------- lifts
        item {
            SectionTitle(t("Lifts"), action = if (finished.isNotEmpty()) t("History") else null, onAction = { nav.go("history") })
            val active = com.forge.app.ui.LocalPrograms.current?.active
            // Lifts in the current program first, then anything else ever logged.
            val exIds = (active?.days.orEmpty().flatMap { d -> d.exercises.map { it.exerciseId } } + doneSets.map { it.exerciseId }).distinct()
            Panel(padding = PaddingValues(vertical = 6.dp)) {
                exIds.forEachIndexed { i, id ->
                    val ex = com.forge.app.domain.Exercises.byId(id)
                    val sets = doneSets.filter { it.exerciseId == id }
                    val bySession = sets.groupBy { it.sessionId }.toSortedMap()
                    val series = bySession.values.map { list -> list.maxOf { Progression.e1rm(it.reps, it.weightKg) } }
                    val best = sets.maxByOrNull { Progression.e1rm(it.reps, it.weightKg) }
                    if (i > 0) Hairline(Modifier.padding(horizontal = 16.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { nav.exercise(id) }.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(ex.name, style = T.BodyM, color = C.Hi)
                            com.forge.app.ui.components.EnglishName(ex)
                            Text(
                                best?.let { if (ex.loaded || it.weightKg > 0) t("Best %1${'$'}s × %2${'$'}s kg", it.reps, Progression.fmtKg(it.weightKg)) else t("Best %s reps", it.reps) } ?: t("Not logged yet"),
                                style = T.Small, color = if (best != null) C.Mid else C.Lo,
                            )
                        }
                        Sparkline(series, Modifier.width(72.dp).height(26.dp))
                    }
                }
            }
            Gap(12.dp)
        }

        // ---------------- body (least used, so last)
        item {
            PhotosPanel()
            Gap(12.dp)
            MeasurementsPanel(measurements)
            BottomSpace()
        }
    }

    if (sharing) com.forge.app.ui.share.ShareProgressDialog { sharing = false }
    if (showWeigh) {
        NumberDialog(
            title = t("Morning weight"),
            initial = Fmt.kg(weights.lastOrNull()?.kg ?: s.startWeight),
            suffix = t("kg"),
            message = t("After the bathroom, before food or water."),
            onDismiss = { showWeigh = false },
            onConfirm = { v -> scope.launch { repo.logWeight(today, v) }; showWeigh = false },
        )
    }
}

/** Workouts and volume for the last 8 weeks. */
@Composable
private fun WeeklyTrainingPanel(s: com.forge.app.data.Settings, finished: List<com.forge.app.data.SessionEntity>, today: LocalDate) {
    val perWeek = s.schedule.count { it != Program.REST }.coerceAtLeast(1)
    var volume by remember { mutableStateOf(false) }
    val thisWeek = s.weekStartOf(today)
    val weeks = (7 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
    val byWeek = weeks.map { w ->
        val from = w.toEpochDay(); val to = from + 6
        finished.filter { it.date in from..to }
    }
    val values = byWeek.map { list -> if (volume) (list.sumOf { it.volumeKg } / 1000).toFloat() else list.size.toFloat() }
    val past = values.dropLast(1).filter { it > 0f }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(t("Last 8 weeks"), modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.forge.app.ui.components.Chip(t("Workouts"), !volume, { volume = false })
                com.forge.app.ui.components.Chip(t("Volume"), volume, { volume = true })
            }
        }
        Gap(14.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat(t("This week"), if (volume) String.format(Locale.US, "%.1f", values.last()) else "${values.last().toInt()}", Modifier.weight(1f), unit = if (volume) "t" else "/ $perWeek")
            Stat(t("Weekly avg"), if (past.isEmpty()) "—" else String.format(Locale.US, if (volume) "%.1f" else "%.1f", past.average()), Modifier.weight(1f), unit = if (volume && past.isNotEmpty()) "t" else null)
        }
        Gap(16.dp)
        BarChart(
            values = values,
            labels = weeks.map { "${it.dayOfMonth}/${it.monthValue}" },
            target = if (volume) null else perWeek.toFloat(),
            highlight = weeks.size - 1,
            valueText = { if (it <= 0f) "" else if (volume) String.format(Locale.US, "%.1f", it) else it.toInt().toString() },
        )
    }
}


@Composable
private fun CheckInPanel(ci: CheckIn?, onAdjust: (Int) -> Unit, onAck: () -> Unit) {
    Panel(border = C.Volt.copy(alpha = 0.45f)) {
        Eyebrow(t("2-week check-in"), color = C.Volt)
        Gap(6.dp)
        if (ci == null) {
            Text(t("Not enough data yet."), style = T.H3, color = C.Hi)
            Gap(4.dp)
            Text(t("Weigh in at least 3 mornings in each of the last two weeks. The plan compares weekly averages, not single readings."), style = T.Small, color = C.Mid)
            Gap(12.dp)
            SecondaryButton(t("Remind me later"), onAck, Modifier.fillMaxWidth(), height = 44.dp)
            return@Panel
        }
        val r = String.format(Locale.US, "%+.2f", ci.rate)
        Text(t("%s kg per week", r), style = T.H2, color = C.Hi)
        Text(t("Average %1${'$'}s → %2${'$'}s kg", Fmt.kg(ci.priorAvg), Fmt.kg(ci.recentAvg)), style = T.Small, color = C.Mid)
        Gap(10.dp)
        when (ci.verdict) {
            CheckIn.Verdict.NOT_RISING -> {
                Text(t("Average weight is not rising. Your plan says: add 150–200 calories a day."), style = T.Body, color = C.Hi)
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(t("Add 175 kcal"), { onAdjust(175) }, Modifier.weight(1f), height = 46.dp)
                    SecondaryButton(t("Not now"), onAck, Modifier.weight(1f), height = 46.dp)
                }
            }
            CheckIn.Verdict.TOO_FAST -> {
                Text(t("Rising much faster than 0.2–0.4 kg a week. Your plan says: reduce intake slightly."), style = T.Body, color = C.Hi)
                Gap(12.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton(t("Remove 150 kcal"), { onAdjust(-150) }, Modifier.weight(1f), height = 46.dp, color = C.Amber)
                    SecondaryButton(t("Not now"), onAck, Modifier.weight(1f), height = 46.dp)
                }
            }
            CheckIn.Verdict.ON_TRACK -> {
                Text(t("Inside the target range. Change nothing."), style = T.Body, color = C.Hi)
                Gap(12.dp)
                PrimaryButton(t("Got it"), onAck, height = 46.dp)
            }
        }
    }
}
