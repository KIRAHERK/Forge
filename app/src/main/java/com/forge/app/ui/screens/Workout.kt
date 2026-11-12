package com.forge.app.ui.screens

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forge.app.Graph
import com.forge.app.data.Fmt
import com.forge.app.data.SessionEntity
import com.forge.app.data.SetEntity
import com.forge.app.data.Stats
import com.forge.app.domain.Exercise
import com.forge.app.domain.Exercises
import com.forge.app.domain.Lines
import com.forge.app.domain.Program
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.Progression
import com.forge.app.domain.SetRecord
import com.forge.app.notify.RestTimer
import com.forge.app.ui.LocalSettings
import com.forge.app.ui.Nav
import com.forge.app.ui.components.Bar
import com.forge.app.ui.components.CheckCircle
import com.forge.app.ui.components.Details
import com.forge.app.ui.components.Eyebrow
import com.forge.app.ui.components.ForgeDialog
import com.forge.app.ui.components.Gap
import com.forge.app.ui.components.HGap
import com.forge.app.ui.components.Hairline
import com.forge.app.ui.components.IconBtn
import com.forge.app.ui.components.NumberDialog
import com.forge.app.ui.components.Panel
import com.forge.app.ui.components.PrimaryButton
import com.forge.app.ui.components.SecondaryButton
import com.forge.app.ui.components.Stat
import com.forge.app.ui.components.Stepper
import com.forge.app.ui.components.Tag
import com.forge.app.ui.components.TopBar
import com.forge.app.ui.components.rememberNow
import com.forge.app.ui.theme.C
import com.forge.app.ui.theme.T
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import kotlin.math.roundToInt

fun vibrate(ctx: Context) {
    val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") ctx.getSystemService(Vibrator::class.java)
    }
    v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 260, 140, 260), -1))
}

private data class Edit(val set: SetEntity, val field: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(nav: Nav, sessionId: Long) {
    val repo = Graph.repo
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by repo.session(sessionId).collectAsState(null)
    val sets by repo.setsFor(sessionId).collectAsState(emptyList())
    val rest by RestTimer.state.collectAsState()
    val now by rememberNow(1000)

    val ses = session
    if (ses == null) {
        Box(Modifier.fillMaxSize().background(C.Bg))
        return
    }
    val day = Program.day(ses.dayId) ?: return
    if (ses.finishedAt != null) {
        LaunchedEffect(Unit) { nav.summary(ses.id) }
        return
    }

    val history by produceState(emptyMap<Int, List<SetRecord>>(), sessionId) {
        value = day.exercises.mapIndexed { i, pe -> i to repo.lastPerformance(pe.exerciseId, excludeSession = sessionId) }.toMap()
    }

    var guide by remember { mutableStateOf<ProgramExercise?>(null) }
    var edit by remember { mutableStateOf<Edit?>(null) }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmDiscard by remember { mutableStateOf(false) }

    val doneCount = sets.count { it.done }
    val total = sets.size
    val elapsedSec = ((Graph.repo.millis(now) - ses.startedAt) / 1000).toInt().coerceAtLeast(0)

    BackHandler { nav.back() }

    fun toggle(set: SetEntity) {
        scope.launch {
            val nowDone = !set.done
            repo.setDone(set, nowDone)
            if (nowDone) {
                val ordered = sets.sortedWith(compareBy({ it.exerciseIndex }, { it.setIndex }))
                val next = ordered.firstOrNull { it.id != set.id && !it.done && (it.exerciseIndex > set.exerciseIndex || (it.exerciseIndex == set.exerciseIndex && it.setIndex > set.setIndex)) }
                    ?: ordered.firstOrNull { it.id != set.id && !it.done }
                if (next != null) {
                    val pe = day.exercises[set.exerciseIndex]
                    val nextPe = day.exercises[next.exerciseIndex]
                    RestTimer.start(ctx, pe.exercise.restSec, "Next: ${nextPe.exercise.name} · set ${next.setIndex + 1} of ${nextPe.sets}")
                } else {
                    RestTimer.stop(ctx)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize().background(C.Bg)) {
        TopBar(
            title = day.title,
            subtitle = (if (ses.makeUp) "Make-up · " else "") + Fmt.clock(elapsedSec) + " elapsed",
            onBack = { nav.back() },
        ) {
            IconBtn(Icons.Rounded.DeleteOutline, "Discard", { confirmDiscard = true }, tint = C.Mid)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$doneCount / $total sets", style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
                Text("${if (total == 0) 0 else doneCount * 100 / total}%", style = T.Small, color = C.Volt)
            }
            Gap(6.dp)
            Bar(if (total == 0) 0f else doneCount.toFloat() / total, height = 4.dp)
        }
        Gap(8.dp)

        LazyColumn(Modifier.weight(1f).fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            itemsIndexed(day.exercises, key = { i, _ -> i }) { i, pe ->
                ExerciseCard(
                    index = i,
                    pe = pe,
                    sets = sets.filter { it.exerciseIndex == i }.sortedBy { it.setIndex },
                    last = history[i].orEmpty(),
                    onInfo = { guide = pe },
                    onToggle = ::toggle,
                    onAdjust = { set, field, delta ->
                        scope.launch {
                            if (field == "reps") repo.updateSet(set.copy(reps = (set.reps + delta.toInt()).coerceIn(0, 100)))
                            else repo.updateSet(set.copy(weightKg = (set.weightKg + delta).coerceIn(0.0, 500.0)))
                        }
                    },
                    onEdit = { set, field -> edit = Edit(set, field) },
                )
                Gap(12.dp)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }

        // Bottom: rest timer or finish
        val r = rest
        if (r != null) {
            RestBar(r, onAdd = { RestTimer.add(ctx, it) }, onSkip = { RestTimer.stop(ctx) }, onDone = {
                vibrate(ctx)
                RestTimer.stop(ctx)
            })
        } else {
            Column(Modifier.fillMaxWidth().background(C.Bg).navigationBarsPadding().padding(16.dp)) {
                PrimaryButton(
                    if (doneCount == total && total > 0) "Finish workout" else "Finish workout · $doneCount/$total",
                    onClick = { if (doneCount < total) confirmFinish = true else scope.launch { RestTimer.stop(ctx); repo.finishSession(ses.id); nav.summary(ses.id) } },
                    icon = Icons.Rounded.Check,
                )
            }
        }
    }

    guide?.let { pe ->
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { guide = null },
            sheetState = state,
            containerColor = C.S1,
            modifier = Modifier.statusBarsPadding(),
            dragHandle = { BottomSheetDefaults.DragHandle(color = C.LineStrong) },
        ) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp)) {
                GuideContent(pe.exercise, pe)
            }
        }
    }

    edit?.let { e ->
        val isReps = e.field == "reps"
        NumberDialog(
            title = if (isReps) "Reps" else "Weight",
            initial = if (isReps) e.set.reps.toString() else Progression.fmtKg(e.set.weightKg),
            suffix = if (isReps) null else "kg",
            onDismiss = { edit = null },
            onConfirm = { v ->
                scope.launch {
                    if (isReps) repo.updateSet(e.set.copy(reps = v.roundToInt().coerceIn(0, 100)))
                    else repo.updateSet(e.set.copy(weightKg = v.coerceIn(0.0, 500.0)))
                }
                edit = null
            },
        )
    }

    if (confirmFinish) {
        val required = Stats.requiredSets(total)
        val counts = doneCount >= required
        ForgeDialog(
            title = if (counts) "Finish early?" else "This won't count",
            onDismiss = { confirmFinish = false },
            confirm = if (counts) "Finish" else "Finish anyway",
            destructive = !counts,
            onConfirm = {
                confirmFinish = false
                scope.launch { RestTimer.stop(ctx); repo.finishSession(ses.id); nav.summary(ses.id) }
            },
            dismiss = "Keep going",
        ) {
            Text(
                if (counts) "You've done $doneCount of $total sets. Unticked sets won't be logged."
                else "You've done $doneCount of $total sets. A session needs at least $required to count as done. " +
                    "Finish now and it's logged as partial: no streak, and the deadline and penalty still apply.",
                style = T.Body, color = C.Mid,
            )
        }
    }
    if (confirmDiscard) {
        ForgeDialog(
            title = "Discard session?",
            onDismiss = { confirmDiscard = false },
            confirm = "Discard",
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                scope.launch { RestTimer.stop(ctx); repo.discardSession(ses.id); nav.back() }
            },
        ) {
            Text("Everything logged in this session is deleted. It will not count as done.", style = T.Body, color = C.Mid)
        }
    }
}

@Composable
private fun ExerciseCard(
    index: Int,
    pe: ProgramExercise,
    sets: List<SetEntity>,
    last: List<SetRecord>,
    onInfo: () -> Unit,
    onToggle: (SetEntity) -> Unit,
    onAdjust: (SetEntity, String, Double) -> Unit,
    onEdit: (SetEntity, String) -> Unit,
) {
    val ex = pe.exercise
    val allDone = sets.isNotEmpty() && sets.all { it.done }
    Panel(border = if (allDone) C.Volt.copy(alpha = 0.4f) else C.Line, padding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(String.format("%02d", index + 1), style = T.Num, color = if (allDone) C.Volt else C.Lo)
            HGap(12.dp)
            Column(Modifier.weight(1f)) {
                Text(pe.displayName, style = T.H3, color = C.Hi, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${pe.prescription} · rest ${ex.restSec}s", style = T.Small, color = C.Mid)
            }
            IconBtn(Icons.Outlined.Info, "How to", onInfo, tint = C.Mid, size = 40.dp)
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(C.Volt))
            HGap(8.dp)
            Text(Progression.advice(pe, last), style = T.Small, color = C.Mid)
        }
        Gap(12.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("SET", style = T.Tiny, color = C.Lo, modifier = Modifier.width(24.dp))
            Text("PREV", style = T.Tiny, color = C.Lo, modifier = Modifier.width(46.dp))
            Text("KG", style = T.Tiny, color = C.Lo, modifier = Modifier.weight(1.15f).padding(start = 6.dp))
            Text("REPS${if (pe.unit != com.forge.app.domain.RepUnit.NONE) " " + pe.unit.label.uppercase() else ""}", style = T.Tiny, color = C.Lo, modifier = Modifier.weight(1f).padding(start = 6.dp))
            Spacer(Modifier.width(44.dp))
        }
        Gap(6.dp)
        sets.forEach { set ->
            val prev = last.getOrNull(set.setIndex)
            val rowBg by animateColorAsState(if (set.done) C.Volt.copy(alpha = 0.07f) else Color.Transparent, label = "row")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(rowBg).padding(horizontal = 2.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${set.setIndex + 1}", style = T.Num.copy(fontSize = 17.sp), color = if (set.done) C.Volt else C.Mid, modifier = Modifier.width(24.dp))
                Text(
                    prev?.let { if (ex.loaded || it.weight > 0) "${it.reps}×${Progression.fmtKg(it.weight)}" else "${it.reps}" } ?: "—",
                    style = T.Tiny, color = C.Lo, modifier = Modifier.width(46.dp), maxLines = 1,
                )
                Stepper(
                    value = if (!ex.loaded && set.weightKg == 0.0) "BW" else Progression.fmtKg(set.weightKg),
                    onMinus = { onAdjust(set, "w", -ex.weightStep.let { if (!ex.loaded) 2.5 else it }) },
                    onPlus = { onAdjust(set, "w", ex.weightStep.let { if (!ex.loaded) 2.5 else it }) },
                    onValueClick = { onEdit(set, "w") },
                    modifier = Modifier.weight(1.15f),
                    dim = set.done,
                    btn = 28.dp,
                )
                HGap(6.dp)
                Stepper(
                    value = "${set.reps}",
                    onMinus = { onAdjust(set, "reps", -1.0) },
                    onPlus = { onAdjust(set, "reps", 1.0) },
                    onValueClick = { onEdit(set, "reps") },
                    modifier = Modifier.weight(1f),
                    dim = set.done,
                    btn = 28.dp,
                )
                HGap(6.dp)
                CheckCircle(set.done, { onToggle(set) }, size = 40.dp)
            }
            Gap(4.dp)
        }
    }
}

@Composable
private fun RestBar(r: RestTimer.State, onAdd: (Int) -> Unit, onSkip: () -> Unit, onDone: () -> Unit) {
    var remaining by remember(r.endsAt) { mutableLongStateOf(r.endsAt - System.currentTimeMillis()) }
    LaunchedEffect(r.endsAt) {
        while (true) {
            remaining = r.endsAt - System.currentTimeMillis()
            if (remaining <= 0) { onDone(); break }
            delay(200)
        }
    }
    val sec = ((remaining + 999) / 1000).toInt().coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().background(C.S1)) {
        Bar(1f - (remaining.toFloat() / (r.totalSec * 1000f)).coerceIn(0f, 1f), height = 3.dp, track = C.S2)
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Eyebrow("Rest", color = C.Volt)
                AnimatedContent(sec, transitionSpec = { (slideInVertically { -it / 2 } + fadeIn(tween(120))) togetherWith (slideOutVertically { it / 2 } + fadeOut(tween(120))) }, label = "rest") {
                    Text(Fmt.clock(it), style = T.Display, color = C.Hi)
                }
                Text(r.label, style = T.Small, color = C.Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SecondaryButton("−15", { onAdd(-15) }, height = 44.dp)
            HGap(6.dp)
            SecondaryButton("+15", { onAdd(15) }, height = 44.dp)
            HGap(6.dp)
            PrimaryButton("Skip", onSkip, Modifier.width(76.dp), height = 44.dp)
        }
    }
}

// ------------------------------------------------------------------ summary

@Composable
fun SummaryScreen(nav: Nav, sessionId: Long) {
    val repo = Graph.repo
    val s = LocalSettings.current
    val session by repo.session(sessionId).collectAsState(null)
    val sets by repo.setsFor(sessionId).collectAsState(emptyList())
    val sessions by repo.sessions.collectAsState(emptyList())
    val ses = session ?: return
    val day = Program.day(ses.dayId)
    val xpShown by animateIntAsState(ses.xp, tween(1400), label = "xp")
    val streak = Stats.workoutStreak(s, sessions, LocalDateTime.now())
    val prs = ses.prNames.split("|").filter { it.isNotBlank() }

    BackHandler { nav.tab("today") }

    Column(
        Modifier.fillMaxSize().background(C.Bg).verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Gap(36.dp)
        if (ses.complete) {
            Eyebrow(if (ses.makeUp) "Make-up complete" else "Session complete", color = C.Volt)
            Text("DONE.", style = T.Hero.copy(fontSize = 104.sp, lineHeight = 100.sp), color = C.Hi)
        } else {
            Eyebrow("Logged as partial · doesn't count", color = C.Red)
            Text("PARTIAL.", style = T.Hero.copy(fontSize = 96.sp, lineHeight = 92.sp), color = C.Hi)
        }
        Text(day?.title?.uppercase() ?: "", style = T.H2, color = C.Mid)
        Gap(28.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("XP earned", "+$xpShown", Modifier.weight(1f), color = C.Volt)
            Stat("Sets", "${sets.count { it.done }}/${sets.size}", Modifier.weight(1f))
        }
        Gap(20.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Volume", Fmt.num(ses.volumeKg.roundToInt()), Modifier.weight(1f), unit = "kg")
            Stat("Time", "${((ses.finishedAt ?: ses.startedAt) - ses.startedAt) / 60000}", Modifier.weight(1f), unit = "min")
        }
        Gap(20.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat("Streak", "$streak", Modifier.weight(1f), unit = if (streak == 1) "session" else "sessions", color = if (streak > 0) C.Volt else C.Hi)
            Stat("Records", "${prs.size}", Modifier.weight(1f), color = if (prs.isNotEmpty()) C.Volt else C.Hi)
        }
        if (prs.isNotEmpty()) {
            Gap(24.dp)
            Panel(border = C.Volt.copy(alpha = 0.4f)) {
                Eyebrow("Personal records", color = C.Volt)
                Gap(10.dp)
                prs.forEach {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag("PR", color = C.OnVolt, bg = C.Volt)
                        HGap(10.dp)
                        Text(it, style = T.BodyM, color = C.Hi)
                    }
                }
            }
        }
        Gap(28.dp)
        Text(
            if (ses.complete) Lines.done(ses.id)
            else "Half a session is still a missed session. Start again and finish it before the deadline.",
            style = T.H2, color = C.Hi,
        )
        Gap(36.dp)
        var sharing by remember { mutableStateOf(false) }
        if (ses.finishedAt != null) {
            SecondaryButton("Share this workout", { sharing = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
            Gap(10.dp)
        }
        PrimaryButton("Back to today", { nav.tab("today") })
        if (sharing) com.forge.app.ui.share.ShareWorkoutDialog(ses.id) { sharing = false }
        Gap(24.dp)
    }
}

// ------------------------------------------------------------------ guide

@Composable
fun GuideContent(ex: Exercise, pe: ProgramExercise? = null) {
    val ctx = LocalContext.current
    Eyebrow(ex.muscles)
    Gap(4.dp)
    Text(ex.name.uppercase(), style = T.H1, color = C.Hi)
    if (pe != null) {
        Gap(8.dp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tag(pe.prescription, color = C.OnVolt, bg = C.Volt)
            Tag("Rest ${ex.restSec}s")
        }
    }
    Gap(14.dp)
    GuideSection("How to do it", ex.steps, numbered = true, initiallyOpen = true)
    GuideSection("Setup", ex.setup, numbered = true)
    GuideBullets("Cues", ex.cues, C.Volt, C.Hi)
    GuideBullets("Common mistakes", ex.mistakes, C.Red, C.Mid)
    ex.homeTip?.let { tip ->
        Hairline()
        Details("Note") { Text(tip, style = T.Body, color = C.Hi) }
    }
    Hairline()
    Details("Effort and progression") {
        Text(Exercises.EFFORT_RULE, style = T.Small, color = C.Hi)
        Gap(6.dp)
        Text(Exercises.PROGRESSION_RULE, style = T.Small, color = C.Mid)
    }
    Gap(18.dp)
    SecondaryButton(
        "Watch a demo", {
            runCatching {
                ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(Exercises.demoUrl(ex))))
            }
        },
        Modifier.fillMaxWidth(),
    )
}

@Composable
private fun GuideSection(title: String, lines: List<String>, numbered: Boolean, initiallyOpen: Boolean = false) {
    Hairline()
    Details(title, initiallyOpen = initiallyOpen) {
        lines.forEachIndexed { i, l ->
            Row(Modifier.padding(vertical = 4.dp)) {
                if (numbered) Text("${i + 1}", style = T.Num.copy(fontSize = 16.sp), color = C.Volt, modifier = Modifier.width(22.dp))
                Text(l, style = T.Body, color = C.Hi)
            }
        }
    }
}

@Composable
private fun GuideBullets(title: String, lines: List<String>, dot: androidx.compose.ui.graphics.Color, text: androidx.compose.ui.graphics.Color) {
    Hairline()
    Details(title) {
        lines.forEach {
            Row(Modifier.padding(vertical = 4.dp)) {
                Box(Modifier.padding(top = 8.dp).size(6.dp).clip(CircleShape).background(dot))
                HGap(10.dp)
                Text(it, style = T.Body, color = text)
            }
        }
    }
}

fun SessionEntity.minutes(): Long = ((finishedAt ?: startedAt) - startedAt) / 60000
