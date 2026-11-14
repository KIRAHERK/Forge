package com.forge.app.ui.screens

import com.forge.app.i18n.t

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
    val programs = com.forge.app.ui.LocalPrograms.current
    val savedDay = remember(ses.dayId, programs) { Program.day(ses.dayId) }
    val plan = remember(sets, savedDay) { com.forge.app.data.ProgramStore.sessionPlan(savedDay, sets) }
    val day = (savedDay ?: com.forge.app.domain.WorkoutDay(ses.dayId, 0, t("Workout"), plan)).copy(exercises = plan)
    if (ses.finishedAt != null) {
        LaunchedEffect(Unit) { nav.summary(ses.id) }
        return
    }

    val history by produceState(emptyMap<Int, List<SetRecord>>(), sessionId, plan.size) {
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
                    val pe = day.exercises.getOrNull(set.exerciseIndex)
                    val nextPe = day.exercises.getOrNull(next.exerciseIndex)
                    if (pe != null && nextPe != null) {
                        RestTimer.start(ctx, pe.exercise.restSec, t("Next: %1${'$'}s · set %2${'$'}s of %3${'$'}s", nextPe.exercise.name, next.setIndex + 1, nextPe.sets))
                    }
                } else {
                    RestTimer.stop(ctx)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = day.title,
            subtitle = (if (ses.makeUp) t("Make-up · ") else "") + Fmt.clock(elapsedSec) + " elapsed",
            onBack = { nav.back() },
        ) {
            IconBtn(Icons.Rounded.DeleteOutline, t("Discard"), { confirmDiscard = true }, tint = C.Mid)
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t("%1${'$'}s / %2${'$'}s sets", doneCount, total), style = T.Small, color = C.Mid, modifier = Modifier.weight(1f))
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
                    if (doneCount == total && total > 0) t("Finish workout") else t("Finish workout · %1${'$'}s/%2${'$'}s", doneCount, total),
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
            title = if (isReps) t("Reps") else t("Weight"),
            initial = if (isReps) e.set.reps.toString() else Progression.fmtKg(e.set.weightKg),
            suffix = if (isReps) null else t("kg"),
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
            title = if (counts) t("Finish early?") else t("This won't count"),
            onDismiss = { confirmFinish = false },
            confirm = if (counts) t("Finish") else t("Finish anyway"),
            destructive = !counts,
            onConfirm = {
                confirmFinish = false
                scope.launch { RestTimer.stop(ctx); repo.finishSession(ses.id); nav.summary(ses.id) }
            },
            dismiss = t("Keep going"),
        ) {
            Text(
                if (counts) t("You've done %1${'$'}s of %2${'$'}s sets. Unticked sets won't be logged.", doneCount, total)
                else t("You've done %1${'$'}s of %2${'$'}s sets. A session needs at least %3${'$'}s to count as done. ", doneCount, total, required) +
                    t("Finish now and it's logged as partial: no streak, and the deadline and penalty still apply."),
                style = T.Body, color = C.Mid,
            )
        }
    }
    if (confirmDiscard) {
        ForgeDialog(
            title = t("Discard session?"),
            onDismiss = { confirmDiscard = false },
            confirm = t("Discard"),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                scope.launch { RestTimer.stop(ctx); repo.discardSession(ses.id); nav.back() }
            },
        ) {
            Text(t("Everything logged in this session is deleted. It will not count as done."), style = T.Body, color = C.Mid)
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
                Text(t("%1${'$'}s · rest %2${'$'}ss", pe.prescription, ex.restSec), style = T.Small, color = C.Mid)
            }
            IconBtn(Icons.Outlined.Info, t("How to"), onInfo, tint = C.Mid, size = 40.dp)
        }
        Gap(8.dp)
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = 7.dp).size(5.dp).clip(CircleShape).background(C.Volt))
            HGap(8.dp)
            Text(Progression.advice(pe, last), style = T.Small, color = C.Mid)
        }
        Gap(12.dp)
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("SET"), style = T.Tiny, color = C.Lo, modifier = Modifier.width(24.dp))
            Text(t("PREV"), style = T.Tiny, color = C.Lo, modifier = Modifier.width(46.dp))
            Text(t("KG"), style = T.Tiny, color = C.Lo, modifier = Modifier.weight(1.15f).padding(start = 6.dp))
            Text(
                when {
                    pe.unit == com.forge.app.domain.RepUnit.SEC_SIDE -> t("SECONDS / SIDE")
                    pe.unit.timed -> t("SECONDS")
                    pe.unit != com.forge.app.domain.RepUnit.NONE -> t("REPS ") + pe.unit.label.uppercase()
                    else -> t("REPS")
                }, style = T.Tiny, color = C.Lo, modifier = Modifier.weight(1f).padding(start = 6.dp))
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
                    value = if (!ex.loaded && set.weightKg == 0.0) t("BW") else Progression.fmtKg(set.weightKg),
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
                    onMinus = { onAdjust(set, t("reps"), -1.0) },
                    onPlus = { onAdjust(set, t("reps"), 1.0) },
                    onValueClick = { onEdit(set, t("reps")) },
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
                Eyebrow(t("Rest"), color = C.Volt)
                AnimatedContent(sec, transitionSpec = { (slideInVertically { -it / 2 } + fadeIn(tween(120))) togetherWith (slideOutVertically { it / 2 } + fadeOut(tween(120))) }, label = "rest") {
                    Text(Fmt.clock(it), style = T.Display, color = C.Hi)
                }
                Text(r.label, style = T.Small, color = C.Mid, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SecondaryButton("−15", { onAdd(-15) }, height = 44.dp)
            HGap(6.dp)
            SecondaryButton("+15", { onAdd(15) }, height = 44.dp)
            HGap(6.dp)
            PrimaryButton(t("Skip"), onSkip, Modifier.width(76.dp), height = 44.dp)
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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
    ) {
        Gap(36.dp)
        if (ses.complete) {
            Eyebrow(if (ses.makeUp) t("Make-up complete") else t("Session complete"), color = C.Volt)
            Text(t("DONE."), style = T.hero(104, 100), color = C.Hi)
        } else {
            Eyebrow(t("Logged as partial · doesn't count"), color = C.Red)
            Text(t("PARTIAL."), style = T.hero(96, 92), color = C.Hi)
        }
        Text(day?.title?.uppercase() ?: "", style = T.H2, color = C.Mid)
        Gap(28.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat(t("XP earned"), "+$xpShown", Modifier.weight(1f), color = C.Volt)
            Stat(t("Sets"), "${sets.count { it.done }}/${sets.size}", Modifier.weight(1f))
        }
        Gap(20.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat(t("Volume"), Fmt.num(ses.volumeKg.roundToInt()), Modifier.weight(1f), unit = t("kg"))
            Stat(t("Time"), "${((ses.finishedAt ?: ses.startedAt) - ses.startedAt) / 60000}", Modifier.weight(1f), unit = t("min"))
        }
        Gap(20.dp)
        Row(Modifier.fillMaxWidth()) {
            Stat(t("Streak"), "$streak", Modifier.weight(1f), unit = if (streak == 1) "session" else "sessions", color = if (streak > 0) C.Volt else C.Hi)
            Stat(t("Records"), "${prs.size}", Modifier.weight(1f), color = if (prs.isNotEmpty()) C.Volt else C.Hi)
        }
        if (prs.isNotEmpty()) {
            Gap(24.dp)
            Panel(border = C.Volt.copy(alpha = 0.4f)) {
                Eyebrow(t("Personal records"), color = C.Volt)
                Gap(10.dp)
                prs.forEach {
                    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Tag(t("PR"), color = C.OnVolt, bg = C.Volt)
                        HGap(10.dp)
                        Text(it, style = T.BodyM, color = C.Hi)
                    }
                }
            }
        }
        Gap(28.dp)
        Text(
            if (ses.complete) Lines.done(ses.id)
            else t("Half a session is still a missed session. Start again and finish it before the deadline."),
            style = T.H2, color = C.Hi,
        )
        Gap(36.dp)
        var sharing by remember { mutableStateOf(false) }
        if (ses.finishedAt != null) {
            SecondaryButton(t("Share this workout"), { sharing = true }, Modifier.fillMaxWidth(), icon = Icons.Rounded.Share)
            Gap(10.dp)
        }
        PrimaryButton(t("Back to today"), { nav.tab("today") })
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
            Tag(t("Rest %ss", ex.restSec))
        }
    }
    Gap(14.dp)
    if (!ex.hasGuide) {
        Text(t("Your own exercise. There's no form guide for it."), style = T.Body, color = C.Mid)
        ex.homeTip?.let { Gap(8.dp); Text(it, style = T.Body, color = C.Hi) }
        Gap(12.dp)
    }
    if (ex.hasGuide) GuideSection(t("How to do it"), ex.steps, numbered = true, initiallyOpen = true)
    if (ex.setup.isNotEmpty()) GuideSection(t("Setup"), ex.setup, numbered = true)
    if (ex.cues.isNotEmpty()) GuideBullets(t("Cues"), ex.cues, C.Volt, C.Hi)
    if (ex.mistakes.isNotEmpty()) GuideBullets(t("Common mistakes"), ex.mistakes, C.Red, C.Mid)
    ex.homeTip?.takeIf { ex.hasGuide }?.let { tip ->
        Hairline()
        Details(t("Note")) { Text(tip, style = T.Body, color = C.Hi) }
    }
    Hairline()
    Details(t("Effort and progression")) {
        Text(Exercises.EFFORT_RULE, style = T.Small, color = C.Hi)
        Gap(6.dp)
        Text(Exercises.PROGRESSION_RULE, style = T.Small, color = C.Mid)
    }
    Gap(18.dp)
    SecondaryButton(
        t("Watch a demo"), {
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
