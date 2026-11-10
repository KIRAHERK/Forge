package com.forge.app

import com.forge.app.data.DayStatus
import com.forge.app.data.FoodEntity
import com.forge.app.data.HabitDef
import com.forge.app.data.HabitLog
import com.forge.app.data.LegacyHabitRow
import com.forge.app.data.migrateLegacyHabitRows
import com.forge.app.data.SessionEntity
import com.forge.app.data.Settings
import com.forge.app.data.Stats
import com.forge.app.data.WeightEntity
import com.forge.app.domain.DayPlans
import com.forge.app.domain.Program
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.Progression
import com.forge.app.domain.SetRecord
import com.forge.app.domain.Xp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class RulesTest {

    // Saturday 7 Nov 2026 is the week start used below.
    private val sat = LocalDate.of(2026, 11, 7)
    private val settings = Settings(
        onboarded = true,
        weekStart = DayOfWeek.SATURDAY.value,
        schedule = Settings.cycleFrom(DayOfWeek.SATURDAY),
        trackingStart = sat.toEpochDay(),
        programStart = sat.toEpochDay(),
    )

    private fun done(date: LocalDate, id: Long = date.toEpochDay()) =
        SessionEntity(id = id, date = date.toEpochDay(), dayId = settings.slotFor(date), startedAt = 0, finishedAt = 1)

    @Test fun cycleMatchesPdfOrder() {
        // Sat D1, Sun D2, Mon rest, Tue D4, Wed D5, Thu rest, Fri rest
        val expected = listOf("R", "D4", "D5", "R", "R", "D1", "D2") // Mon..Sun
        assertEquals(expected, Settings.cycleFrom(DayOfWeek.SATURDAY))
        assertEquals(4, settings.schedule.count { it != Program.REST })
    }

    @Test fun programMatchesPdf() {
        assertEquals(listOf(14, 16, 22, 19), Program.days.map { it.totalSets })
        val d4 = Program.day("D4")!!
        assertEquals("1-arm Overhead Triceps Extension", d4.exercises.last().exercise.name)
        assertEquals("3 × 10–15 / side", d4.exercises.last().prescription)
        assertEquals("2 × 8–15", Program.D1.exercises.first().prescription)
    }

    @Test fun dayStatusAroundDeadline() {
        val beforeDeadline = sat.atTime(21, 0)
        val afterDeadline = sat.atTime(22, 0)
        assertEquals(DayStatus.PENDING, Stats.dayStatus(settings, sat, beforeDeadline, emptySet()))
        assertEquals(DayStatus.MAKEUP_OPEN, Stats.dayStatus(settings, sat, afterDeadline, emptySet()))
        assertEquals(DayStatus.MISSED, Stats.dayStatus(settings, sat, sat.plusDays(3).atTime(9, 0), emptySet()))
        assertEquals(DayStatus.REST, Stats.dayStatus(settings, sat.plusDays(2), afterDeadline.plusDays(2), emptySet()))
        assertEquals(DayStatus.BEFORE, Stats.dayStatus(settings, sat.minusDays(1), afterDeadline, emptySet()))
    }

    @Test fun streakSkipsRestDaysAndBreaksOnMiss() {
        // Sat, Sun done; Mon rest; Tue done; Wed pending (today).
        val now = sat.plusDays(4).atTime(12, 0)
        val sessions = listOf(done(sat), done(sat.plusDays(1)), done(sat.plusDays(3)))
        assertEquals(3, Stats.workoutStreak(settings, sessions, now))

        // Wed missed -> streak 0 the next day.
        val thu = sat.plusDays(5).atTime(12, 0)
        assertEquals(0, Stats.workoutStreak(settings, sessions, thu))
        assertEquals(listOf(sat.plusDays(4)), Stats.makeUpOpen(settings, sessions, thu))

        // Make-up credited to Wed restores it.
        val withMakeUp = sessions + done(sat.plusDays(4), id = 99)
        assertEquals(4, Stats.workoutStreak(settings, withMakeUp, thu))
    }

    private val quit = HabitDef(id = 1, name = "Quit thing", kind = HabitDef.KIND_QUIT, counted = true, startDate = sat.toEpochDay())
    private val build = HabitDef(id = 2, name = "Read", kind = HabitDef.KIND_BUILD, startDate = sat.toEpochDay())

    @Test fun quitStreakCountsFromLastSlip() {
        val today = sat.plusDays(10)
        val logs = listOf(HabitLog(1, sat.plusDays(3).toEpochDay(), 2))
        assertEquals(7, Stats.habitStreak(quit, logs, today))
        assertEquals(1, Stats.habitStreak(quit.copy(startDate = today.toEpochDay()), emptyList(), today))
        val slipToday = listOf(HabitLog(1, today.toEpochDay(), 1))
        assertEquals(0, Stats.habitStreak(quit, slipToday, today))
        assertEquals(10, Stats.habitBest(quit, slipToday, today))
    }

    @Test fun buildStreakCountsDaysDone() {
        val today = sat.plusDays(5)
        val logs = (1..4).map { HabitLog(2, sat.plusDays(it.toLong()).toEpochDay(), 1) } + HabitLog(2, sat.toEpochDay(), 0)
        // Done Sun–Wed, today (Thu) not logged yet: streak still counts through yesterday.
        assertEquals(4, Stats.habitStreak(build, logs, today))
        assertEquals(5, Stats.habitStreak(build, logs + HabitLog(2, today.toEpochDay(), 1), today))
        val yesterday = today.minusDays(1).toEpochDay()
        val missedYesterday = logs.filter { it.date != yesterday } + HabitLog(2, yesterday, 0) + HabitLog(2, today.toEpochDay(), 0)
        assertEquals(0, Stats.habitStreak(build, missedYesterday, today))
        assertEquals(4, Stats.habitBest(build, logs, today))
        assertEquals(true, Stats.kept(build, HabitLog(2, 0, 1)))
        assertEquals(false, Stats.kept(quit, HabitLog(1, 0, 3)))
        assertEquals(null, Stats.kept(quit, HabitLog(1, 0, null)))
    }

    @Test fun weekScorePerfectAndEmpty() {
        val now = sat.plusDays(6).atTime(23, 0)
        val days = (0..6).map { sat.plusDays(it.toLong()) }
        val sessions = days.filter { settings.isTraining(it) }.map { done(it) }
        val foods = days.map { FoodEntity(date = it.toEpochDay(), at = 0, name = "x", kcal = 2700, protein = 130.0, fat = 75.0, carbs = 376.0) }
        val weights = days.map { WeightEntity(it.toEpochDay(), 81.0, 0) }
        val logs = days.flatMap { listOf(HabitLog(1, it.toEpochDay(), 0), HabitLog(2, it.toEpochDay(), 1)) }
        assertEquals(100, Stats.weekScore(settings, sat, now, sessions, foods, weights, listOf(quit, build), logs).score)
        // No habits at all: the habit part is left out instead of scoring zero.
        assertEquals(100, Stats.weekScore(settings, sat, now, sessions, foods, weights, emptyList(), emptyList()).score)
        assertEquals(0, Stats.weekScore(settings, sat, sat.atTime(8, 0), emptyList(), emptyList(), emptyList(), emptyList(), emptyList()).score)
    }

    @Test fun legacyHabitRowsBecomeTwoQuitHabits() {
        val rows = listOf(
            LegacyHabitRow(100, 0, 0, 1),
            LegacyHabitRow(101, 3, null, 0),
            LegacyHabitRow(102, null, 1, 2),
        )
        val (defs, logs) = migrateLegacyHabitRows(rows)
        assertEquals(listOf("Habit 1", "Habit 2"), defs.map { it.name })
        assertTrue(defs.all { it.isQuit })
        assertEquals(listOf(100L, 100L), defs.map { it.startDate })
        assertEquals(HabitLog(1, 101, 3, 0), logs.first { it.habitId == 1L && it.date == 101L })
        assertEquals(HabitLog(2, 102, 1, 0), logs.first { it.habitId == 2L && it.date == 102L })
        assertEquals(1, logs.first { it.habitId == 1L && it.date == 100L }.urges)
        assertEquals(Pair(emptyList<HabitDef>(), emptyList<HabitLog>()), migrateLegacyHabitRows(emptyList()))
    }

    @Test fun checkInVerdicts() {
        val today = sat.plusDays(14)
        fun series(rate: Double) = (0..13).map { i ->
            val d = today.minusDays(13L - i)
            WeightEntity(d.toEpochDay(), 81.0 + rate / 7 * i, 0)
        }
        assertEquals(com.forge.app.data.CheckIn.Verdict.NOT_RISING, Stats.checkIn(series(0.0), today)!!.verdict)
        assertEquals(com.forge.app.data.CheckIn.Verdict.ON_TRACK, Stats.checkIn(series(0.3), today)!!.verdict)
        assertEquals(com.forge.app.data.CheckIn.Verdict.TOO_FAST, Stats.checkIn(series(0.9), today)!!.verdict)
    }

    @Test fun levels() {
        assertEquals(1, Xp.level(0).level)
        assertEquals(2, Xp.level(300).level)
        assertEquals(3, Xp.level(700).level)
        assertEquals(1, Xp.level(-500).level)
    }

    @Test fun doubleProgression() {
        val pe = ProgramExercise("barbell_squat", 4, 8, 15)
        val top = List(4) { SetRecord(15, 40.0) }
        assertTrue(Progression.advice(pe, top).contains("42.5"))
        val mid = listOf(SetRecord(12, 40.0), SetRecord(11, 40.0), SetRecord(10, 40.0), SetRecord(9, 40.0))
        assertTrue(Progression.advice(pe, mid).contains("Beat 42"))
    }

    @Test fun dayPlansLandNearTarget() {
        DayPlans.all.forEach { p ->
            val k = p.total.kcal
            assertTrue("${p.name} = $k kcal", k in 2550..2900)
            assertTrue("${p.name} protein ${p.total.protein}", p.total.protein >= 125)
        }
    }

    @Test fun deadlineTimes() {
        assertEquals(LocalDateTime.of(2026, 11, 7, 21, 30), settings.deadlineAt(sat))
        assertEquals(sat, settings.weekStartOf(sat.plusDays(3)))
        assertEquals(2, settings.programWeek(sat.plusDays(7)))
    }
}

class UpdaterTest {
    @Test fun versionOrder() {
        val u = com.forge.app.update.Updater
        assertTrue(u.compare("1.10.0", "1.9.9") > 0)
        assertTrue(u.compare("v1.2.0", "1.2.0") == 0)
        assertTrue(u.compare("1.2", "1.2.1") < 0)
        assertTrue(u.compare("2.0.0", "1.99.99") > 0)
    }
}
