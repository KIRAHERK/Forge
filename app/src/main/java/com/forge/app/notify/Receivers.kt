package com.forge.app.notify

import com.forge.app.i18n.t

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.forge.app.Graph
import com.forge.app.data.EvalResult
import com.forge.app.data.Fmt
import com.forge.app.data.Stats
import com.forge.app.domain.Lines
import com.forge.app.widget.ForgeWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch {
        try { block() } catch (_: Throwable) { } finally { pending.finish() }
    }
}

private suspend fun appInForeground(): Boolean = withContext(Dispatchers.Main) {
    ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val r = intent.getStringExtra(Scheduler.EXTRA_REM)?.let { runCatching { Rem.valueOf(it) }.getOrNull() } ?: return
        val label = intent.getStringExtra(Scheduler.EXTRA_LABEL)
        Graph.init(context)
        launchAsync { handle(context.applicationContext, r, label) }
    }

    private suspend fun handle(ctx: Context, r: Rem, label: String?) {
        if (r == Rem.REST) {
            RestTimer.clearIfExpired()
            if (!appInForeground()) {
                Notifier.post(
                    ctx, Notifier.ID_TIMER, Notifier.CH_TIMER, t("Rest over"),
                    label ?: t("Next set. Go."), route = "workout", timeoutMs = 5 * 60_000L,
                )
            }
            return
        }

        val repo = Graph.repo
        Graph.programsReady()
        val s = Graph.settings.current()
        if (!s.onboarded) return
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val eval = repo.evaluate(now)
        val sessions = Graph.db.sessions().allNow()
        val doneToday = Stats.doneDates(sessions).contains(today.toEpochDay())
        val day = s.dayFor(today)

        when (r) {
            Rem.WEIGH_IN -> if (Graph.db.weights().get(today.toEpochDay()) == null) {
                postWeighIn(ctx, t("Step on the scale"), t("Bathroom first, then the scale, before you eat or drink. Type the number below."))
            }

            Rem.WEIGH_LATE -> if (Graph.db.weights().get(today.toEpochDay()) == null) {
                postWeighIn(ctx, t("Still no weigh-in today"), t("Haven't eaten yet? Weigh in now. Already eaten? Skip today and do it first thing tomorrow."))
            }

            Rem.WORKOUT, Rem.NUDGE, Rem.FINAL -> if (s.isTraining(today) && !s.isSick(today) && !doneToday && day != null) {
                val left = Fmt.left(now, s.deadlineAt(today))
                val (title, text) = when (r) {
                    Rem.WORKOUT -> day.title to Lines.reminder(t("%1${'$'}s exercises, %2${'$'}s sets, about %3${'$'}s min.", day.exercises.size, day.totalSets, day.estMinutes), Fmt.time(s.deadline))
                    Rem.NUDGE -> t("Not started: %s", day.title) to Lines.nudge(left)
                    else -> t("Last call — %s left", left) to Lines.final(left)
                }
                Notifier.post(
                    ctx, Notifier.ID_WORKOUT, Notifier.CH_WORKOUT, title, text, route = "start",
                    actions = listOf(Notifier.Action("Start workout", Notifier.openIntent(ctx, "start", 501))),
                )
            }

            Rem.DEADLINE -> { /* handled by evaluation below */ }

            Rem.PROTEIN1, Rem.PROTEIN2 -> {
                val m = Stats.sum(Graph.db.food().forDayNow(today.toEpochDay()))
                val pace = if (r == Rem.PROTEIN1) 0.45 else 0.75
                val pLeft = (s.protein - m.protein).roundToInt()
                val kLeft = s.kcal - m.kcal
                if (m.protein < s.protein * pace || m.kcal < s.kcal * pace) {
                    val text = if (m.protein < s.protein * pace) Lines.proteinLow(today.toEpochDay() + r.code, pLeft)
                    else Lines.kcalLow(today.toEpochDay() + r.code, kLeft)
                    Notifier.post(
                        ctx, Notifier.ID_PROTEIN, Notifier.CH_NUTRITION,
                        t("%1${'$'}s / %2${'$'}s g protein · %3${'$'}s / %4${'$'}s kcal", Fmt.g(m.protein), s.protein, Fmt.num(m.kcal), Fmt.num(s.kcal)),
                        text, route = "fuel",
                    )
                }
            }

            Rem.NIGHT -> {
                val d = today.toEpochDay()
                val logged = Graph.db.habits().logsFor(d).filter { it.value != null }.map { it.habitId }.toSet()
                val open = Graph.db.habits().defsNow().filter { it.startDate <= d && it.id !in logged }
                val foods = Graph.db.food().forDayNow(d)
                if (open.isNotEmpty() || foods.isEmpty()) {
                    val parts = buildList {
                        if (foods.isEmpty()) add("food")
                        if (open.isNotEmpty()) add("habits")
                    }
                    // "Clean day" only makes sense when every open habit is one you're quitting.
                    val actions = buildList {
                        if (open.isNotEmpty() && open.all { it.isQuit }) {
                            add(Notifier.Action("Clean day", Notifier.actionIntent(ctx, ActionReceiver.CLEAN_TODAY, 601)))
                        }
                        if (open.isNotEmpty()) add(Notifier.Action("Open", Notifier.openIntent(ctx, "habits", 602)))
                    }
                    Notifier.post(
                        ctx, Notifier.ID_NIGHT, Notifier.CH_HABITS, t("Close out today"),
                        "Log your ${parts.joinToString(" and ")} before bed. Be honest — it only works if you are.",
                        route = "habits", actions = actions, publicText = t("Daily check-in"),
                    )
                }
            }

            Rem.WEEKLY -> {
                val ws = s.weekStartOf(today)
                val score = Stats.weekScore(
                    s, ws, now, sessions,
                    Graph.db.food().betweenNow(ws.toEpochDay(), today.toEpochDay()),
                    Graph.db.weights().allNow(), Graph.db.habits().defsNow(), Graph.db.habits().logsNow(),
                )
                val verdict = when {
                    score.score >= 85 -> t("Strong week.")
                    score.score >= 65 -> t("Decent. Not good enough to coast.")
                    else -> t("Weak week. Next one starts tomorrow.")
                }
                Notifier.post(
                    ctx, Notifier.ID_WEEKLY, Notifier.CH_WEEKLY, t("Week score: %s", score.score),
                    t("%1${'$'}s Workouts %2${'$'}s/%3${'$'}s · protein %4${'$'}s/%5${'$'}s · calories %6${'$'}s/%7${'$'}s · weigh-ins %8${'$'}s/%9${'$'}s.", verdict, score.workoutsDone, score.workoutsDue, score.proteinDays, score.proteinDen, score.kcalDays, score.kcalDen, score.weighIns, score.weighDen),
                    route = "progress",
                )
            }

            Rem.PENALTY -> {
                val nowMs = System.currentTimeMillis()
                Graph.db.penalties().activeNow().filter { it.dueAt - nowMs in 1..(3 * 3_600_000L + 120_000L) }.firstOrNull()?.let { p ->
                    Notifier.post(
                        ctx, Notifier.ID_PENALTY, Notifier.CH_PENALTY, t("Penalty due in %s", Fmt.leftMillis(p.dueAt - nowMs)),
                        t("%s. Miss it and it doubles.", p.taskText), route = "today",
                    )
                }
            }

            Rem.REST -> {}
        }

        notifyEval(ctx, eval)
        Scheduler.reschedule(ctx, r)
        Scheduler.reschedule(ctx, Rem.PENALTY)
        runCatching { ForgeWidget.refresh(ctx) }
    }
}

suspend fun notifyEval(ctx: Context, eval: EvalResult) {
    Graph.programsReady()
    val s = Graph.settings.current()
    eval.newMisses.lastOrNull()?.let { p ->
        Notifier.post(
            ctx, Notifier.ID_MISSED, Notifier.CH_PENALTY, t("Missed. Streak reset."),
            t("%1${'$'}s. Penalty: %2${'$'}s within 24 h. Make the session up within 48 h to restore the streak.", p.reason, p.taskText) +
                if (s.jarAmount > 0) " Jar: +${s.jarAmount}${s.jarCurrency.let { if (it.isBlank()) "" else " $it" }}." else "",
            route = "today",
        )
    }
    eval.escalated.lastOrNull()?.let { p ->
        Notifier.post(
            ctx, Notifier.ID_PENALTY, Notifier.CH_PENALTY, t("Penalty overdue — doubled"),
            t("Now %s. You have 24 h. Miss this and it's recorded as failed.", p.taskText), route = "today",
        )
    }
    eval.failed.lastOrNull()?.let { p ->
        Notifier.post(
            ctx, Notifier.ID_PENALTY, Notifier.CH_PENALTY, t("Penalty failed"),
            t("%s: never paid. It stays on your record.", p.reason), route = "today",
        )
    }
}

/** Weigh-in reminder with a text field right in the notification. */
fun postWeighIn(ctx: Context, title: String, text: String) {
    val input = androidx.core.app.RemoteInput.Builder(ActionReceiver.KEY_KG).setLabel(t("Weight in kg, e.g. 81.4")).build()
    Notifier.post(
        ctx, Notifier.ID_WEIGH, Notifier.CH_NUTRITION, title, text, route = "weigh",
        actions = listOf(
            Notifier.Action("Log weight", Notifier.inputIntent(ctx, ActionReceiver.LOG_WEIGHT, 701), input),
            Notifier.Action("Open", Notifier.openIntent(ctx, "weigh", 702)),
        ),
    )
}

class ActionReceiver : BroadcastReceiver() {
    companion object {
        const val CLEAN_TODAY = "com.forge.app.CLEAN_TODAY"
        const val LOG_WEIGHT = "com.forge.app.LOG_WEIGHT"
        const val KEY_KG = "kg"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        when (intent.action) {
            CLEAN_TODAY -> launchAsync {
                Graph.repo.logQuitHabitsClean(LocalDate.now())
                Notifier.cancel(context, Notifier.ID_NIGHT)
            }
            LOG_WEIGHT -> launchAsync {
                val raw = androidx.core.app.RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_KG)?.toString()
                val kg = raw?.trim()?.replace(',', '.')?.filter { it.isDigit() || it == '.' }?.toDoubleOrNull()
                if (kg == null || kg !in 30.0..250.0) {
                    postWeighIn(context, t("Couldn't read that"), t("Type just the number, like 81.4."))
                } else {
                    Graph.repo.logWeight(LocalDate.now(), kg)
                    val avg = Stats.movingAverage(Graph.db.weights().allNow()).lastOrNull()?.second
                    Notifier.post(
                        context, Notifier.ID_WEIGH, Notifier.CH_NUTRITION, t("Logged %s kg", Fmt.kg(kg)),
                        avg?.let { t("7-day average: %s kg. +10 XP.", Fmt.kg(it)) } ?: t("+10 XP."),
                        route = "weigh", timeoutMs = 8_000L,
                    )
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        launchAsync {
            Notifier.createChannels(context)
            val eval = Graph.repo.evaluate()
            notifyEval(context, eval)
            Scheduler.rescheduleAll(context)
            runCatching { ForgeWidget.refresh(context) }
        }
    }
}

/** App-wide rest timer, survives navigation. The alarm covers the case where the app is closed. */
object RestTimer {
    data class State(val endsAt: Long, val totalSec: Int, val label: String)

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    fun start(ctx: Context, seconds: Int, label: String) {
        val ends = System.currentTimeMillis() + seconds * 1000L
        _state.value = State(ends, seconds, label)
        Scheduler.scheduleRest(ctx, ends, label)
    }

    fun add(ctx: Context, seconds: Int) {
        val cur = _state.value ?: return
        val ends = (cur.endsAt + seconds * 1000L).coerceAtLeast(System.currentTimeMillis() + 1000)
        _state.value = cur.copy(endsAt = ends, totalSec = (cur.totalSec + seconds).coerceAtLeast(1))
        Scheduler.scheduleRest(ctx, ends, cur.label)
    }

    fun stop(ctx: Context) {
        _state.value = null
        Scheduler.cancelRest(ctx)
    }

    fun clearIfExpired() {
        val cur = _state.value ?: return
        if (cur.endsAt <= System.currentTimeMillis()) _state.value = null
    }
}
