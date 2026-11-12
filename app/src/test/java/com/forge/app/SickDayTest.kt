package com.forge.app

import com.forge.app.data.DayStatus
import com.forge.app.data.SessionEntity
import com.forge.app.data.Settings
import com.forge.app.data.SickCheck
import com.forge.app.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SickDayTest {

    // Week starts Saturday 7 Nov 2026: Sat D1, Sun D2, Mon rest, Tue D4, Wed D5, Thu rest, Fri rest.
    private val sat = LocalDate.of(2026, 11, 7)
    private val sun = sat.plusDays(1)
    private val mon = sat.plusDays(2)
    private val tue = sat.plusDays(3)
    private val wed = sat.plusDays(4)
    private val base = Settings(
        onboarded = true,
        weekStart = DayOfWeek.SATURDAY.value,
        schedule = Settings.cycleFrom(DayOfWeek.SATURDAY),
        trackingStart = sat.minusDays(60).toEpochDay(),
        programStart = sat.toEpochDay(),
    )

    private fun done(date: LocalDate) =
        SessionEntity(id = date.toEpochDay(), date = date.toEpochDay(), dayId = base.slotFor(date), startedAt = 0, finishedAt = 1)

    @Test
    fun sickTrainingDayIsNotAMiss() {
        val s = base.copy(sickDays = listOf(sun.toEpochDay()))
        val lateMonday = mon.atTime(23, 0)
        assertEquals(DayStatus.SICK, Stats.dayStatus(s, sun, lateMonday, emptySet()))
        assertEquals(DayStatus.MAKEUP_OPEN, Stats.dayStatus(base, sun, lateMonday, emptySet()))
    }

    @Test
    fun doneBeatsSick() {
        val s = base.copy(sickDays = listOf(sun.toEpochDay()))
        assertEquals(DayStatus.DONE, Stats.dayStatus(s, sun, mon.atTime(9, 0), setOf(sun.toEpochDay())))
    }

    @Test
    fun sickDayPausesTheStreakInsteadOfBreakingIt() {
        val sessions = listOf(done(sat), done(tue))
        val now = wed.atTime(9, 0)
        assertEquals(1, Stats.workoutStreak(base, sessions, now))
        val s = base.copy(sickDays = listOf(sun.toEpochDay()))
        assertEquals(2, Stats.workoutStreak(s, sessions, now))
    }

    @Test
    fun onlyTodayOrYesterday() {
        assertEquals(SickCheck.Allowed, Stats.canMarkSick(base, wed, wed, emptySet()))
        assertEquals(SickCheck.Allowed, Stats.canMarkSick(base, tue, wed, emptySet()))
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(base, sun, wed, emptySet()))
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(base, wed.plusDays(1), wed, emptySet()))
    }

    @Test
    fun notOnRestDaysDoneDaysOrTwice() {
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(base, mon, mon, emptySet()))
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(base, tue, tue, setOf(tue.toEpochDay())))
        val s = base.copy(sickDays = listOf(tue.toEpochDay()))
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(s, tue, tue, emptySet()))
    }

    @Test
    fun threeInThirtyDays() {
        val first = sat.minusDays(20)
        val s = base.copy(sickDays = listOf(first, sat.minusDays(10), sat).map { it.toEpochDay() })
        val r = Stats.canMarkSick(s, tue, tue, emptySet())
        assertTrue(r is SickCheck.LimitReached)
        assertEquals(first.plusDays(30), (r as SickCheck.LimitReached).nextFree)
        // Once the oldest one ages out of the window, a slot is free again.
        val later = first.plusDays(30)
        val laterTraining = generateSequence(later) { it.plusDays(1) }.first { s.isTraining(it) }
        assertEquals(SickCheck.Allowed, Stats.canMarkSick(s, laterTraining, laterTraining, emptySet()))
    }

    @Test
    fun sickDayIsNotDueInTheWeekScore() {
        val now = wed.atTime(9, 0)
        val sessions = listOf(done(sat), done(tue))
        val plain = Stats.weekScore(base, sat, now, sessions, emptyList(), emptyList(), emptyList(), emptyList())
        val s = base.copy(sickDays = listOf(sun.toEpochDay()))
        val sick = Stats.weekScore(s, sat, now, sessions, emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(3, plain.workoutsDue)
        assertEquals(2, sick.workoutsDue)
        assertEquals(2, sick.workoutsDone)
    }
}
