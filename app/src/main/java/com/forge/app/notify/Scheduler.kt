package com.forge.app.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.forge.app.Graph
import com.forge.app.data.PenaltyEntity
import com.forge.app.data.Settings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class Rem(val code: Int) {
    WEIGH_IN(11), WORKOUT(12), NUDGE(13), FINAL(14), DEADLINE(15),
    PROTEIN1(16), PROTEIN2(17), NIGHT(18), WEEKLY(19), PENALTY(20), REST(21), WEIGH_LATE(22)
}

object Scheduler {
    const val EXTRA_REM = "rem"
    const val EXTRA_LABEL = "label"

    private fun am(ctx: Context) = ctx.getSystemService(AlarmManager::class.java)

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || am(ctx).canScheduleExactAlarms()

    private fun pi(ctx: Context, r: Rem, label: String? = null): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction("com.forge.app.alarm.${r.name}").putExtra(EXTRA_REM, r.name)
        if (label != null) i.putExtra(EXTRA_LABEL, label)
        return PendingIntent.getBroadcast(ctx, r.code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun setAt(ctx: Context, r: Rem, atMillis: Long, label: String? = null) {
        val p = pi(ctx, r, label)
        val m = am(ctx)
        if (canExact(ctx)) m.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
        else m.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, p)
    }

    private fun cancel(ctx: Context, r: Rem) = am(ctx).cancel(pi(ctx, r))

    private fun LocalDateTime.ms() = atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun atMinutes(d: LocalDate, minutes: Int) = d.atTime(LocalTime.of((minutes / 60).coerceIn(0, 23), minutes % 60))

    private fun nextDaily(now: LocalDateTime, minutes: Int): LocalDateTime {
        val t = atMinutes(now.toLocalDate(), minutes)
        return if (t.isAfter(now)) t else atMinutes(now.toLocalDate().plusDays(1), minutes)
    }

    private fun nextTraining(now: LocalDateTime, s: Settings, minutes: Int): LocalDateTime? {
        for (i in 0..8) {
            val d = now.toLocalDate().plusDays(i.toLong())
            if (d.toEpochDay() < s.trackingStart) continue
            val t = atMinutes(d, minutes)
            if (t.isAfter(now) && s.isTraining(d) && !s.isSick(d)) return t
        }
        return null
    }

    fun weighLateMinutes(s: Settings) = (s.weighInTime + 150).coerceAtMost(23 * 60)
    fun nudgeMinutes(s: Settings) = s.deadline - 120
    fun finalMinutes(s: Settings) = s.deadline - 30

    fun nextTime(r: Rem, s: Settings, now: LocalDateTime, penalties: List<PenaltyEntity>): LocalDateTime? = when (r) {
        Rem.WEIGH_IN -> if (s.remindWeighIn) nextDaily(now, s.weighInTime) else null
        Rem.WEIGH_LATE -> if (s.remindWeighIn) nextDaily(now, weighLateMinutes(s)) else null
        Rem.WORKOUT -> if (s.remindWorkout) nextTraining(now, s, s.workoutTime) else null
        Rem.NUDGE -> if (s.remindWorkout && nudgeMinutes(s) > s.workoutTime) nextTraining(now, s, nudgeMinutes(s)) else null
        Rem.FINAL -> if (s.remindWorkout && finalMinutes(s) > s.workoutTime) nextTraining(now, s, finalMinutes(s)) else null
        Rem.DEADLINE -> nextTraining(now, s, s.deadline)?.plusMinutes(1)
        Rem.PROTEIN1 -> if (s.remindNutrition) nextDaily(now, s.proteinCheck1) else null
        Rem.PROTEIN2 -> if (s.remindNutrition) nextDaily(now, s.proteinCheck2) else null
        Rem.NIGHT -> if (s.remindNight) nextDaily(now, s.nightTime) else null
        Rem.WEEKLY -> if (s.remindWeekly) {
            val lastDay = DayOfWeek.of(s.weekStart).minus(1)
            var d = now.toLocalDate()
            var t: LocalDateTime? = null
            for (i in 0..7) {
                val c = d.plusDays(i.toLong())
                val ct = c.atTime(20, 0)
                if (c.dayOfWeek == lastDay && ct.isAfter(now)) { t = ct; break }
            }
            t
        } else null
        Rem.PENALTY -> {
            val zone = ZoneId.systemDefault()
            penalties.filter { it.status == "ACTIVE" }.flatMap {
                listOf(it.dueAt - 3 * 3_600_000L, it.dueAt + 60_000L)
            }.filter { it > now.ms() }.minOrNull()?.let {
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(it), zone)
            }
        }
        Rem.REST -> null
    }

    suspend fun rescheduleAll(ctx: Context) {
        Graph.init(ctx)
        val s = Graph.settings.current()
        if (!s.onboarded) {
            Rem.entries.forEach { cancel(ctx, it) }
            return
        }
        val penalties = Graph.db.penalties().activeNow()
        val now = LocalDateTime.now()
        Rem.entries.filter { it != Rem.REST }.forEach { r ->
            val t = nextTime(r, s, now, penalties)
            if (t == null) cancel(ctx, r) else setAt(ctx, r, t.ms())
        }
    }

    suspend fun reschedule(ctx: Context, r: Rem) {
        val s = Graph.settings.current()
        val penalties = Graph.db.penalties().activeNow()
        // Nudge past "now" by a second so we never re-fire the alarm that is running.
        val t = nextTime(r, s, LocalDateTime.now().plusSeconds(1), penalties)
        if (t == null) cancel(ctx, r) else setAt(ctx, r, t.ms())
    }

    fun scheduleRest(ctx: Context, atMillis: Long, label: String) = setAt(ctx, Rem.REST, atMillis, label)
    fun cancelRest(ctx: Context) {
        cancel(ctx, Rem.REST)
        Notifier.cancel(ctx, Notifier.ID_TIMER)
    }
}
