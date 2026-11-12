package com.forge.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.Settings
import com.forge.app.data.Stats
import com.forge.app.data.WeightEntity
import com.forge.app.domain.Plan
import com.forge.app.notify.Scheduler
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.UiEvents
import com.forge.app.ui.components.Loading
import com.forge.app.ui.components.BottomSpace
import com.forge.app.ui.components.Chip
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.ForgeField
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SectionTitle
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.ToggleRow
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.ValueRow
import com.forge.app.ui.components.WeightChart
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Locale

/** Consecutive mornings logged, ending today (or yesterday if today isn't logged yet). */
fun weighInStreak(weights: List<WeightEntity>, today: LocalDate): Int {
    val days = weights.map { it.date }.toSet()
    var d = if (today.toEpochDay() in days) today else today.minusDays(1)
    var n = 0
    while (d.toEpochDay() in days) { n++; d = d.minusDays(1) }
    return n
}

data class WeekAvg(val start: LocalDate, val avg: Double, val count: Int, val delta: Double?)

fun weeklyAverages(s: Settings, weights: List<WeightEntity>): List<WeekAvg> {
    val grouped = weights.groupBy { s.weekStartOf(LocalDate.ofEpochDay(it.date)) }.toSortedMap()
    var prev: Double? = null
    return grouped.map { (start, list) ->
        val avg = list.map { it.kg }.average()
        WeekAvg(start, avg, list.size, prev?.let { avg - it }).also { prev = avg }
    }
}

/** Weigh-in dialog with a choice of day, so a forgotten morning can still be filled in. */
@Composable
fun WeighDialog(initialKg: Double, onDismiss: () -> Unit, onSave: (LocalDate, Double) -> Unit, fixedDate: LocalDate? = null) {
    val today = LocalDate.now()
    var date by remember { mutableStateOf(fixedDate ?: today) }
    var text by remember { mutableStateOf(Fmt.kg(initialKg)) }
    val value = text.replace(',', '.').toDoubleOrNull()
    val valid = value != null && value in 30.0..250.0
    ForgeDialog(
        title = "Weigh-in",
        onDismiss = onDismiss,
        confirm = "Save",
        onConfirm = { if (valid) onSave(date, value!!) },
    ) {
        if (fixedDate == null) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..3).forEach { i ->
                    val d = today.minusDays(i.toLong())
                    Chip(when (i) { 0 -> "Today"; 1 -> "Yesterday"; else -> Fmt.day(d) }, date == d, { date = d })
                }
            }
            Gap(12.dp)
        }
        ForgeField(text, { text = it }, "Weight", number = true, suffix = "kg")
        if (!valid && text.isNotBlank()) {
            Gap(4.dp)
            Text("Enter a weight between 30 and 250 kg.", style = T.Tiny, color = C.Red)
        }
    }
}

@Composable
fun WeightScreen(nav: Nav) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val weightsN by repo.weights.collectAsState(null)
    val weights = weightsN ?: return Loading()
    val today = LocalDate.now()
    val todayEntry = weights.firstOrNull { it.date == today.toEpochDay() }
    val avgSeries = Stats.movingAverage(weights)
    val rate = Stats.weeklyRate(weights, today)
    val streak = weighInStreak(weights, today)
    val weeks = weeklyAverages(s, weights).reversed()

    var logDialog by remember { mutableStateOf(false) }
    var editEntry by remember { mutableStateOf<WeightEntity?>(null) }
    var deleteEntry by remember { mutableStateOf<WeightEntity?>(null) }
    var timeEdit by remember { mutableStateOf(false) }

    LaunchedEffect(UiEvents.weighRequest.value) {
        if (UiEvents.weighRequest.value) { logDialog = true; UiEvents.weighRequest.value = false }
    }

    fun upd(t: (Settings) -> Settings) { scope.launch { Graph.settings.update(t); Scheduler.rescheduleAll(ctx) } }

    Column(Modifier.fillMaxSize()) {
        TopBar("Weight", "Morning weigh-ins · ${s.startWeight.toInt()} → ${s.goalWeight.toInt()} kg", onBack = { nav.back() })
        LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 6.dp)) {
            item {
                Panel(border = if (todayEntry == null) C.Volt.copy(alpha = 0.45f) else C.Line, accent = true) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow(if (todayEntry == null) "Today · not logged" else "Today", color = if (todayEntry == null) C.Volt else C.Mid)
                            Gap(4.dp)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(todayEntry?.let { Fmt.kg(it.kg) } ?: "—", style = T.Hero, color = C.Hi)
                                if (todayEntry != null) Text(" kg", style = T.H3, color = C.Mid, modifier = Modifier.padding(bottom = 8.dp))
                            }
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Eyebrow("Streak")
                            Text("$streak", style = T.NumL, color = if (streak > 0) C.Volt else C.Hi)
                            Text(if (streak == 1) "morning" else "mornings", style = T.Tiny, color = C.Lo)
                        }
                    }
                    Gap(16.dp)
                    PrimaryButton(if (todayEntry == null) "Log today's weight" else "Update or add a day", { logDialog = true }, icon = Icons.Rounded.MonitorWeight)
                }
                Gap(12.dp)
            }

            item {
                Panel {
                    Eyebrow("Reminder")
                    ToggleRow("Morning weigh-in reminder", null, s.remindWeighIn) { v -> upd { it.copy(remindWeighIn = v) } }
                    Hairline()
                    ValueRow("Reminder time", Fmt.time(s.weighInTime)) { timeEdit = true }
                    Details("How it works") {
                        Text(
                            "Skipped once you've logged. A second nudge comes at ${Fmt.time(Scheduler.weighLateMinutes(s))} if you still haven't. " +
                                "You can type your weight straight into the notification.\n\n" +
                                "Weigh in the morning, after the bathroom, before food or water. Same scale every time.",
                            style = T.Small, color = C.Mid,
                        )
                    }
                }
                Gap(12.dp)
            }

            item {
                Panel {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Column(Modifier.weight(1f)) {
                            Eyebrow("7-day average")
                            Text(avgSeries.lastOrNull()?.second?.let { "${Fmt.kg(it)} kg" } ?: "—", style = T.Display, color = C.Hi)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Eyebrow("Per week")
                            Text(rate?.let { String.format(Locale.US, "%+.2f", it) } ?: "—", style = T.NumL, color = rateColor(rate))
                            Text("target ${Plan.RATE_MIN}–${Plan.RATE_MAX}", style = T.Tiny, color = C.Lo)
                        }
                    }
                    Gap(16.dp)
                    WeightChart(weights.map { it.date to it.kg }, avgSeries, goal = s.goalWeight)
                    if (weights.isNotEmpty()) {
                        Gap(16.dp)
                        Hairline()
                        Gap(14.dp)
                        val monthAgo = today.minusDays(30).toEpochDay()
                        val base = weights.filter { it.date <= monthAgo }.maxByOrNull { it.date } ?: weights.first()
                        val change = (avgSeries.lastOrNull()?.second ?: weights.last().kg) - base.kg
                        Row(Modifier.fillMaxWidth()) {
                            Stat("30 days", String.format(Locale.US, "%+.1f", change), Modifier.weight(1f), unit = "kg", color = rateColor(change / 4.3))
                            Stat("Lowest", Fmt.kg(weights.minOf { it.kg }), Modifier.weight(1f), unit = "kg")
                            Stat("Highest", Fmt.kg(weights.maxOf { it.kg }), Modifier.weight(1f), unit = "kg")
                        }
                    }
                }
                Gap(12.dp)
            }

            if (weeks.isNotEmpty()) {
                item {
                    SectionTitle("Weekly averages")
                    Panel(padding = PaddingValues(vertical = 6.dp)) {
                        weeks.take(10).forEachIndexed { i, w ->
                            if (i > 0) Hairline(Modifier.padding(horizontal = 16.dp))
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("Week of ${Fmt.day(w.start)}", style = T.BodyM, color = C.Hi)
                                    Text("${w.count} ${if (w.count == 1) "reading" else "readings"}", style = T.Small, color = C.Lo)
                                }
                                Text("${Fmt.kg(w.avg)} kg", style = T.Num, color = C.Hi)
                                HGap(12.dp)
                                Text(
                                    w.delta?.let { String.format(Locale.US, "%+.1f", it) } ?: "",
                                    style = T.Small, color = rateColor(w.delta), modifier = Modifier.width(44.dp),
                                )
                            }
                        }
                    }
                    Gap(12.dp)
                }
            }

            item { SectionTitle("All weigh-ins · ${weights.size}") }
            if (weights.isEmpty()) {
                item { Text("No weigh-ins yet. Tomorrow morning: bathroom, scale, then breakfast.", style = T.Small, color = C.Lo) }
            }
            val sorted = weights.sortedByDescending { it.date }
            items(sorted, key = { it.date }) { w ->
                val prev = weights.filter { it.date < w.date }.maxByOrNull { it.date }
                Row(
                    Modifier.fillMaxWidth().clickable { editEntry = w }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(Fmt.day(LocalDate.ofEpochDay(w.date)), style = T.BodyM, color = C.Hi, modifier = Modifier.weight(1f))
                    Text("${Fmt.kg(w.kg)} kg", style = T.Num.copy(fontSize = T.Num.fontSize * 0.85f), color = C.Hi)
                    HGap(12.dp)
                    val d = prev?.let { w.kg - it.kg }
                    Text(d?.let { String.format(Locale.US, "%+.1f", it) } ?: "", style = T.Small, color = C.Mid, modifier = Modifier.width(40.dp))
                    IconBtn(Icons.Rounded.Close, "Delete", { deleteEntry = w }, tint = C.Lo, size = 36.dp)
                }
                Hairline()
            }
            item { BottomSpace() }
        }
    }

    if (logDialog) {
        WeighDialog(
            initialKg = todayEntry?.kg ?: weights.lastOrNull()?.kg ?: s.startWeight,
            onDismiss = { logDialog = false },
            onSave = { d, kg -> scope.launch { repo.logWeight(d, kg) }; logDialog = false },
        )
    }
    editEntry?.let { e ->
        WeighDialog(
            initialKg = e.kg,
            fixedDate = LocalDate.ofEpochDay(e.date),
            onDismiss = { editEntry = null },
            onSave = { d, kg -> scope.launch { repo.logWeight(d, kg) }; editEntry = null },
        )
    }
    deleteEntry?.let { e ->
        ForgeDialog(
            "Delete weigh-in?", { deleteEntry = null }, "Delete",
            { scope.launch { repo.deleteWeight(LocalDate.ofEpochDay(e.date)) }; deleteEntry = null },
            destructive = true,
        ) {
            Text("${Fmt.day(LocalDate.ofEpochDay(e.date))}: ${Fmt.kg(e.kg)} kg will be removed.", style = T.Body, color = C.Mid)
        }
    }
    if (timeEdit) {
        TimeDialog("Weigh-in reminder", s.weighInTime, { timeEdit = false }) { v -> upd { it.copy(weighInTime = v) }; timeEdit = false }
    }
}

private fun rateColor(rate: Double?) = when {
    rate == null -> C.Mid
    rate < Plan.RATE_MIN - 0.05 -> C.Red
    rate > Plan.RATE_MAX + 0.15 -> C.Amber
    else -> C.Volt
}
