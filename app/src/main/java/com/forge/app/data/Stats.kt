package com.forge.app.data

import com.forge.app.domain.Macros
import com.forge.app.domain.Penalties
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

enum class DayStatus { BEFORE, REST, DONE, PENDING, MISSED, MAKEUP_OPEN, FUTURE, SICK }

/** Whether a sick day can be marked for [date], and if not, why. */
sealed interface SickCheck {
    data object Allowed : SickCheck
    data class LimitReached(val nextFree: LocalDate) : SickCheck
    data object NotAllowed : SickCheck
}

data class CheckIn(val rate: Double, val recentAvg: Double, val priorAvg: Double, val verdict: Verdict) {
    enum class Verdict { NOT_RISING, ON_TRACK, TOO_FAST }
}

/** Pure functions over stored data. Used by the UI, notifications and the widget. */
object Stats {

    fun doneDates(sessions: List<SessionEntity>): Set<Long> =
        sessions.filter { it.finishedAt != null && it.complete }.map { it.date }.toSet()

    /** Sets needed for a session to count as done. */
    fun requiredSets(total: Int): Int = kotlin.math.ceil(total * 0.75).toInt()

    fun dayStatus(s: Settings, date: LocalDate, now: LocalDateTime, done: Set<Long>): DayStatus {
        val today = now.toLocalDate()
        if (date.toEpochDay() < s.trackingStart) return if (date.toEpochDay() in done) DayStatus.DONE else DayStatus.BEFORE
        if (date.toEpochDay() in done) return DayStatus.DONE
        if (!s.isTraining(date)) return DayStatus.REST
        if (s.isSick(date)) return DayStatus.SICK
        if (date.isAfter(today)) return DayStatus.FUTURE
        val deadline = s.deadlineAt(date)
        if (now.isBefore(deadline)) return DayStatus.PENDING
        return if (now.isBefore(deadline.plusHours(Penalties.MAKE_UP_HOURS))) DayStatus.MAKEUP_OPEN else DayStatus.MISSED
    }

    /** Consecutive scheduled sessions completed, counting back from today. Rest days don't break it. */
    fun workoutStreak(s: Settings, sessions: List<SessionEntity>, now: LocalDateTime): Int {
        val done = doneDates(sessions)
        var d = now.toLocalDate()
        var n = 0
        val stop = LocalDate.ofEpochDay(s.trackingStart).minusDays(400)
        while (!d.isBefore(stop)) {
            if (s.isTraining(d) || d.toEpochDay() in done) {
                when (dayStatus(s, d, now, done)) {
                    DayStatus.DONE -> n++
                    // A sick day pauses the streak: it neither counts nor breaks it.
                    DayStatus.PENDING, DayStatus.FUTURE, DayStatus.SICK -> {}
                    DayStatus.BEFORE -> return n
                    else -> return n
                }
            }
            d = d.minusDays(1)
        }
        return n
    }

    fun bestWorkoutStreak(s: Settings, sessions: List<SessionEntity>, now: LocalDateTime): Int {
        val done = doneDates(sessions)
        var best = 0
        var cur = 0
        var d = LocalDate.ofEpochDay(s.trackingStart)
        val today = now.toLocalDate()
        while (!d.isAfter(today)) {
            if (s.isTraining(d) || d.toEpochDay() in done) {
                when (dayStatus(s, d, now, done)) {
                    DayStatus.DONE -> { cur++; best = maxOf(best, cur) }
                    DayStatus.MISSED, DayStatus.MAKEUP_OPEN -> cur = 0
                    else -> {}
                }
            }
            d = d.plusDays(1)
        }
        return maxOf(best, cur)
    }

    /**
     * A sick day can be marked for today or yesterday, on a training day not already done,
     * and only [Penalties.SICK_LIMIT] times in any [Penalties.SICK_WINDOW_DAYS] days.
     */
    fun canMarkSick(s: Settings, date: LocalDate, today: LocalDate, done: Set<Long>): SickCheck {
        if (date != today && date != today.minusDays(1)) return SickCheck.NotAllowed
        if (!s.isTraining(date) || date.toEpochDay() in done || s.isSick(date)) return SickCheck.NotAllowed
        if (date.toEpochDay() < s.trackingStart) return SickCheck.NotAllowed
        val window = Penalties.SICK_WINDOW_DAYS
        val recent = s.sickDays.filter { it > date.toEpochDay() - window && it <= today.toEpochDay() }.sorted()
        if (recent.size < Penalties.SICK_LIMIT) return SickCheck.Allowed
        // The oldest one in the window frees up a slot once it ages out.
        return SickCheck.LimitReached(LocalDate.ofEpochDay(recent.first() + window))
    }

    /** Missed sessions that can still be made up. */
    fun makeUpOpen(s: Settings, sessions: List<SessionEntity>, now: LocalDateTime): List<LocalDate> {
        val done = doneDates(sessions)
        val today = now.toLocalDate()
        return (1L..3L).map { today.minusDays(it) }.plus(today)
            .filter { s.isTraining(it) && dayStatus(s, it, now, done) == DayStatus.MAKEUP_OPEN }
            .sorted()
    }

    // ------------------------------------------------------------- habits

    /** true = kept that day, false = not, null = not logged. */
    fun kept(def: HabitDef, log: HabitLog?): Boolean? {
        val v = log?.value ?: return null
        return if (def.isQuit) v == 0 else v > 0
    }

    private fun isSlip(def: HabitDef, l: HabitLog) = def.isQuit && (l.value ?: 0) > 0

    /**
     * QUIT: days since the last slip (unlogged days count as clean; today counts unless slipped).
     * BUILD: consecutive days done, ending today, or yesterday while today isn't logged yet.
     */
    fun habitStreak(def: HabitDef, logs: List<HabitLog>, today: LocalDate): Int {
        val t = today.toEpochDay()
        val mine = logs.filter { it.habitId == def.id && it.date <= t }
        if (def.isQuit) {
            val lastSlip = mine.filter { isSlip(def, it) }.maxOfOrNull { it.date }
            val base = maxOf(lastSlip ?: (def.startDate - 1), def.startDate - 1)
            return (t - base).toInt().coerceAtLeast(0)
        }
        val done = mine.filter { (it.value ?: 0) > 0 }.map { it.date }.toSet()
        var d = if (t in done) t else t - 1
        var n = 0
        while (d in done) { n++; d-- }
        return n
    }

    fun habitBest(def: HabitDef, logs: List<HabitLog>, today: LocalDate): Int {
        val t = today.toEpochDay()
        val mine = logs.filter { it.habitId == def.id && it.date <= t }
        if (def.isQuit) {
            var best = 0
            var prev = def.startDate - 1
            for (r in mine.filter { isSlip(def, it) }.map { it.date }.sorted()) {
                best = maxOf(best, (r - prev - 1).toInt())
                prev = r
            }
            return maxOf(best, (t - prev).toInt())
        }
        var best = 0
        var cur = 0
        var last = Long.MIN_VALUE
        for (d in mine.filter { (it.value ?: 0) > 0 }.map { it.date }.sorted()) {
            cur = if (d == last + 1) cur + 1 else 1
            best = maxOf(best, cur)
            last = d
        }
        return best
    }

    // ------------------------------------------------------------- nutrition

    fun sum(foods: List<FoodEntity>): Macros =
        foods.fold(Macros.ZERO) { acc, f -> acc + Macros(f.kcal, f.protein, f.fat, f.carbs) }

    fun proteinHit(total: Double, target: Int) = total >= target * 0.95
    /** The calorie window: 90% to 115% of the target, in whole calories (2,700 → 2,430..3,105). */
    fun kcalLow(target: Int) = Math.round(target * 0.9).toInt()
    fun kcalHigh(target: Int) = Math.round(target * 1.15).toInt()
    fun kcalHit(total: Int, target: Int) = total in kcalLow(target)..kcalHigh(target)
    /** Above the calorie window: a lean bulk counts this as a miss, not a win. */
    fun kcalOver(total: Int, target: Int) = total > kcalHigh(target)
    /** Calories above the top of the window. */
    fun kcalOverBy(total: Int, target: Int) = (total - kcalHigh(target)).coerceAtLeast(0)

    // ------------------------------------------------------------- weight

    fun movingAverage(weights: List<WeightEntity>, window: Int = 7): List<Pair<Long, Double>> =
        weights.sortedBy { it.date }.map { w ->
            val inWindow = weights.filter { it.date in (w.date - window + 1)..w.date }
            w.date to inWindow.map { it.kg }.average()
        }

    fun avgBetween(weights: List<WeightEntity>, from: Long, to: Long): Double? =
        weights.filter { it.date in from..to }.takeIf { it.size >= 3 }?.map { it.kg }?.average()

    /** kg per week from the last 7 days vs the 7 days before. */
    fun weeklyRate(weights: List<WeightEntity>, today: LocalDate): Double? {
        val t = today.toEpochDay()
        val recent = avgBetween(weights, t - 6, t) ?: return null
        val prior = avgBetween(weights, t - 13, t - 7) ?: return null
        return recent - prior
    }

    fun checkIn(weights: List<WeightEntity>, today: LocalDate): CheckIn? {
        val t = today.toEpochDay()
        val recent = avgBetween(weights, t - 6, t) ?: return null
        val prior = avgBetween(weights, t - 13, t - 7) ?: return null
        val rate = recent - prior
        val v = when {
            rate < 0.1 -> CheckIn.Verdict.NOT_RISING
            rate > 0.55 -> CheckIn.Verdict.TOO_FAST
            else -> CheckIn.Verdict.ON_TRACK
        }
        return CheckIn(rate, recent, prior, v)
    }

    fun checkInDue(s: Settings, today: LocalDate): Boolean {
        val t = today.toEpochDay()
        val sinceStart = t - s.trackingStart
        val sinceAck = if (s.lastCheckInAck < 0) Long.MAX_VALUE else t - s.lastCheckInAck
        return sinceStart >= 14 && sinceAck >= 14
    }

    // ------------------------------------------------------------- weekly score

    data class WeekScore(
        val score: Int,
        val workoutsDone: Int,
        val workoutsDue: Int,
        val proteinDays: Int,
        val kcalDays: Int,
        val cleanDays: Int,
        val weighIns: Int,
        val daysCounted: Int,
        val proteinDen: Int = 0,
        val kcalDen: Int = 0,
        val cleanDen: Int = 0,
        val weighDen: Int = 0,
    )

    fun weekScore(
        s: Settings, weekStart: LocalDate, now: LocalDateTime,
        sessions: List<SessionEntity>, foods: List<FoodEntity>, weights: List<WeightEntity>,
        habitDefs: List<HabitDef>, habitLogs: List<HabitLog>,
    ): WeekScore {
        val done = doneDates(sessions)
        val today = now.toLocalDate()
        val days = (0..6).map { weekStart.plusDays(it.toLong()) }
            .filter { !it.isAfter(today) && it.toEpochDay() >= s.trackingStart }
        var wDone = 0; var wDue = 0
        var pDays = 0; var kDays = 0; var cDays = 0; var wIns = 0; var counted = 0
        var pDen = 0; var kDen = 0; var cDen = 0; var wDen = 0
        val logMap = habitLogs.associateBy { it.habitId to it.date }
        val weightDays = weights.map { it.date }.toSet()
        for (d in days) {
            val isToday = d == today
            val st = dayStatus(s, d, now, done)
            if (st == DayStatus.DONE) { wDone++; wDue++ }
            else if (st == DayStatus.MISSED || st == DayStatus.MAKEUP_OPEN) wDue++

            val dayFoods = foods.filter { it.date == d.toEpochDay() }
            val m = sum(dayFoods)
            val pHit = proteinHit(m.protein, s.protein)
            val kHit = kcalHit(m.kcal, s.kcal)
            // A day counts for habits when every habit active that day was kept.
            val active = habitDefs.filter { !it.archived && it.startDate <= d.toEpochDay() }
            val habitsTracked = active.isNotEmpty()
            val clean = habitsTracked && active.all { kept(it, logMap[it.id to d.toEpochDay()]) == true }
            val weighed = d.toEpochDay() in weightDays
            // Today only counts once it is achieved — an unfinished day is not a failure yet.
            if (!isToday) counted++
            if (pHit) pDays++
            if (kHit) kDays++
            if (clean) cDays++
            if (weighed) wIns++
            if (!isToday || pHit) pDen++
            if (!isToday || kHit) kDen++
            if (habitsTracked && (!isToday || clean)) cDen++
            if (!isToday || weighed) wDen++
        }
        // Weighted average over the parts that have something to judge yet.
        val parts = listOf(
            Triple(40.0, wDone, wDue),
            Triple(20.0, pDays, pDen),
            Triple(15.0, kDays, kDen),
            Triple(15.0, cDays, cDen),
            Triple(10.0, wIns, wDen),
        ).filter { it.third > 0 }
        val weight = parts.sumOf { it.first }
        val score = if (weight == 0.0) 0 else
            (parts.sumOf { it.first * it.second / it.third } / weight * 100).roundToInt().coerceIn(0, 100)
        return WeekScore(score, wDone, wDue, pDays, kDays, cDays, wIns, counted, pDen, kDen, cDen, wDen)
    }
}

object Fmt {
    private val dayFmt get() = DateTimeFormatter.ofPattern("EEE d MMM", com.forge.app.i18n.Lang.locale)
    private val longDay get() = DateTimeFormatter.ofPattern("EEEE d MMMM", com.forge.app.i18n.Lang.locale)
    private val shortDow get() = DateTimeFormatter.ofPattern("EEE", com.forge.app.i18n.Lang.locale)

    fun time(minutes: Int) = String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
    fun day(d: LocalDate): String = d.format(dayFmt)
    fun longDay(d: LocalDate): String = d.format(longDay)
    fun dow(d: LocalDate): String = d.format(shortDow)
    fun dowLetter(d: LocalDate): String =
        if (com.forge.app.i18n.Lang.arabic) "نثرخجسح"[d.dayOfWeek.value - 1].toString() else d.format(shortDow).take(1)

    fun left(from: LocalDateTime, to: LocalDateTime): String {
        val mins = ChronoUnit.MINUTES.between(from, to).coerceAtLeast(0)
        val h = mins / 60
        val m = mins % 60
        return when {
            h >= 24 -> com.forge.app.i18n.t("%1${'$'}sd %2${'$'}sh", h / 24, h % 24)
            h > 0 -> com.forge.app.i18n.t("%1${'$'}sh %2${'$'}sm", h, m)
            else -> com.forge.app.i18n.t("%sm", m)
        }
    }

    fun leftMillis(ms: Long): String {
        val mins = (ms / 60000).coerceAtLeast(0)
        val h = mins / 60
        return if (h > 0) com.forge.app.i18n.t("%1${'$'}sh %2${'$'}sm", h, mins % 60) else com.forge.app.i18n.t("%sm", mins)
    }

    fun kg(v: Double): String = String.format(Locale.US, "%.1f", v)
    fun num(v: Int): String = String.format(Locale.US, "%,d", v)
    fun g(v: Double): String = v.roundToInt().toString()
    fun clock(sec: Int): String = String.format(Locale.US, "%d:%02d", sec / 60, sec % 60)
}
