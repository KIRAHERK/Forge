package com.forge.app

import com.forge.app.data.DayStatus
import com.forge.app.data.Pause
import com.forge.app.data.SessionEntity
import com.forge.app.data.Settings
import com.forge.app.data.SickCheck
import com.forge.app.data.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class InjuryPauseTest {

    // Week starts Saturday 7 Nov 2026: Sat D1, Sun D2, Mon rest, Tue D4, Wed D5, Thu rest, Fri rest.
    private val sat = LocalDate.of(2026, 11, 7)
    private val sun = sat.plusDays(1)
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

    @Test fun openPauseCoversEveryDayFromItsStartWithNoLimit() {
        val s = base.copy(injuredSince = sun.toEpochDay())
        assertTrue(s.injured)
        assertFalse(s.isInjured(sat))
        listOf(sun, tue, wed, wed.plusDays(40)).forEach { assertTrue(it.toString(), s.isInjured(it)) }
        // Long past the sick-day limit, and still no miss.
        assertEquals(DayStatus.SICK, Stats.dayStatus(s, tue, wed.plusDays(30).atTime(23, 0), emptySet()))
        // Sick days aren't used up by the pause.
        assertTrue(s.sickDays.isEmpty())
    }

    @Test fun pausedDaysFreezeTheStreak() {
        val sessions = listOf(done(sat))
        val now = wed.plusDays(14).atTime(23, 30)
        assertEquals(0, Stats.workoutStreak(base, sessions, now))
        assertEquals(1, Stats.workoutStreak(base.copy(injuredSince = sun.toEpochDay()), sessions, now))
    }

    @Test fun aWorkoutLoggedWhilePausedStillCounts() {
        val s = base.copy(injuredSince = sun.toEpochDay())
        assertEquals(DayStatus.DONE, Stats.dayStatus(s, tue, wed.atTime(9, 0), setOf(tue.toEpochDay())))
    }

    @Test fun anEndedPauseKeepsItsDaysAndTheNextDayCountsAgain() {
        val s = base.copy(injuries = listOf(Pause(sun.toEpochDay(), tue.toEpochDay())))
        assertFalse(s.injured)
        assertTrue(s.isInjured(tue))
        assertFalse(s.isInjured(wed))
        assertEquals(DayStatus.SICK, Stats.dayStatus(s, tue, wed.plusDays(2).atTime(9, 0), emptySet()))
        assertEquals(DayStatus.MISSED, Stats.dayStatus(s, wed, wed.plusDays(5).atTime(9, 0), emptySet()))
    }

    @Test fun noSickDayOnAPausedDay() {
        val s = base.copy(injuredSince = sun.toEpochDay())
        assertEquals(SickCheck.NotAllowed, Stats.canMarkSick(s, wed, wed, emptySet()))
    }
}
