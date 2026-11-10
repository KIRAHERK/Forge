package com.forge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.forge.app.data.FoodEntity
import com.forge.app.data.HabitDef
import com.forge.app.data.HabitLog
import com.forge.app.data.SessionEntity
import com.forge.app.data.SetEntity
import com.forge.app.data.Settings
import com.forge.app.data.WeightEntity
import com.forge.app.data.XpEntity
import com.forge.app.domain.Program
import com.forge.app.domain.Recipes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/**
 * Debug builds only. Fills the app with two weeks of plausible history for UI testing:
 *   adb shell am broadcast -a com.forge.app.SEED -n com.forge.app/.DebugSeedReceiver
 */
class DebugSeedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Graph.init(context)
        // adb shell am broadcast -a com.forge.app.FIRE --es rem WEIGH_IN -n com.forge.app/.DebugSeedReceiver
        if (intent.action == "com.forge.app.FIRE") {
            context.sendBroadcast(
                Intent(context, com.forge.app.notify.AlarmReceiver::class.java)
                    .putExtra(com.forge.app.notify.Scheduler.EXTRA_REM, intent.getStringExtra("rem"))
                    .putExtra(com.forge.app.notify.Scheduler.EXTRA_LABEL, "Next: test set")
            )
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { seed() } finally { pending.finish() }
        }
    }

    private suspend fun seed() {
        val db = Graph.db
        db.clearAllTables()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusDays(16)
        val rnd = Random(7)
        val weekStart = DayOfWeek.SATURDAY
        Graph.settings.update {
            val base = Settings(
                onboarded = true,
                weekStart = weekStart.value,
                schedule = Settings.cycleFrom(weekStart),
                trackingStart = start.toEpochDay(),
            )
            base.copy(programStart = base.weekStartOf(start).toEpochDay(), lastSeenLevel = 4)
        }
        val s = Graph.settings.current()

        // Weights: slow climb with noise.
        for (i in 0..16) {
            val d = start.plusDays(i.toLong())
            if (i == 5 || i == 11) continue
            val kg = 81.0 + i * 0.045 + (rnd.nextDouble() - 0.5) * 0.5
            db.weights().upsert(WeightEntity(d.toEpochDay(), Math.round(kg * 10) / 10.0, d.atTime(7, 40).atZone(zone).toInstant().toEpochMilli()))
            db.xp().insert(XpEntity(key = "weigh:${d.toEpochDay()}", at = 0, amount = 10, reason = "Morning weigh-in"))
        }

        // Sessions on training days, one deliberately missed.
        var missed = false
        for (i in 0 until 16) {
            val d = start.plusDays(i.toLong())
            val day = s.dayFor(d) ?: continue
            if (!missed && i in 6..9) { missed = true; continue }
            val startMs = d.atTime(18, 5).atZone(zone).toInstant().toEpochMilli()
            val sid = db.sessions().insert(SessionEntity(date = d.toEpochDay(), dayId = day.id, startedAt = startMs, finishedAt = startMs + 52 * 60_000))
            var volume = 0.0
            var sets = 0
            day.exercises.forEachIndexed { ei, pe ->
                val baseW = when (pe.exercise.equip) {
                    com.forge.app.domain.Equip.BARBELL -> 40.0 + i * 0.6
                    com.forge.app.domain.Equip.DUMBBELL -> 10.0 + (i / 4) * 1.0
                    else -> 0.0
                }
                repeat(pe.sets) { si ->
                    val reps = (pe.repMin + (i / 2) + rnd.nextInt(0, 2) - si).coerceIn(pe.repMin - 1, pe.repMax)
                    db.sets().insert(SetEntity(sessionId = sid, exerciseId = pe.exerciseId, exerciseIndex = ei, setIndex = si, reps = reps, weightKg = baseW, done = true, doneAt = startMs))
                    volume += reps * baseW
                    sets++
                }
            }
            val xp = 100 + sets * 5
            db.sessions().update(SessionEntity(id = sid, date = d.toEpochDay(), dayId = day.id, startedAt = startMs, finishedAt = startMs + 52 * 60_000, xp = xp, volumeKg = volume, prs = if (i > 8) 1 else 0, prNames = if (i > 8) day.exercises.first().exercise.name else ""))
            db.xp().insert(XpEntity(key = "session:$sid", at = startMs, amount = xp, reason = day.title))
        }

        // Food for past days and part of today.
        val meals = listOf("pb_oats", "dal_rice", "yogurt_bowl", "tuna_rice", "chicken_rice_pot", "egg_fried_rice", "pb_toast_milk")
        for (i in 0..16) {
            val d = start.plusDays(i.toLong())
            val count = if (d == today) 2 else 3 + rnd.nextInt(0, 2)
            repeat(count) { k ->
                val r = Recipes.byId(meals[(i + k * 2) % meals.size])!!
                val at = d.atTime(8 + k * 4, 15).atZone(zone).toInstant().toEpochMilli()
                db.food().insert(FoodEntity(date = d.toEpochDay(), at = at, name = r.name, kcal = r.perServing.kcal, protein = r.perServing.protein, fat = r.perServing.fat, carbs = r.perServing.carbs, source = "recipe:${r.id}"))
            }
        }

        // Habits: one to build, one to quit with a couple of slips.
        val read = db.habits().insertDef(HabitDef(name = "Read 20 pages", kind = HabitDef.KIND_BUILD, startDate = start.toEpochDay(), sort = 0))
        val junk = db.habits().insertDef(HabitDef(name = "No junk food", kind = HabitDef.KIND_QUIT, counted = true, startDate = start.toEpochDay(), sort = 1))
        for (i in 0 until 16) {
            val d = start.plusDays(i.toLong()).toEpochDay()
            db.habits().upsertLog(HabitLog(read, d, if (i == 5 || i == 11) 0 else 1))
            db.habits().upsertLog(HabitLog(junk, d, if (i == 4) 2 else if (i == 9) 1 else 0, if (i % 3 == 0) 1 else 0))
        }
        Graph.repo.evaluate()
        Graph.scheduleRefresh()
    }
}
