package com.forge.app

import com.forge.app.data.ProgramStore
import com.forge.app.data.SetEntity
import com.forge.app.domain.Exercises
import com.forge.app.domain.Program
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.RepUnit
import com.forge.app.domain.Templates
import com.forge.app.domain.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgramsTest {

    @Test
    fun libraryHasAtLeastEightyUniqueExercisesWithGuides() {
        val all = Exercises.builtIn
        assertTrue("${all.size} exercises", all.size >= 80)
        assertEquals(all.size, all.map { it.id }.toSet().size)
        all.forEach { e ->
            assertTrue("${e.id} has steps", e.steps.isNotEmpty())
            assertTrue("${e.id} has cues", e.cues.isNotEmpty())
            assertTrue("${e.id} has mistakes", e.mistakes.isNotEmpty())
            assertFalse("${e.id} is not custom", e.custom)
        }
    }

    @Test
    fun everyTemplateUsesRealExercisesAndAValidWeek() {
        Templates.all.forEach { t ->
            assertEquals(t.id, 7, t.week.size)
            assertTrue(t.id, t.daysPerWeek in 2..6)
            t.week.filterNotNull().forEach { assertTrue("${t.id} day $it", it in t.days.indices) }
            // Every day is used at least once in the week.
            t.days.indices.forEach { assertTrue("${t.id} day $it scheduled", it in t.week) }
            t.days.forEach { d ->
                assertTrue("${t.id}/${d.name} not empty", d.exercises.isNotEmpty())
                d.exercises.forEach { pe ->
                    assertTrue("${t.id}: ${pe.exerciseId}", Exercises.isBuiltIn(pe.exerciseId))
                    assertTrue(pe.repMin <= pe.repMax && pe.sets > 0)
                }
            }
        }
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
    }

    @Test
    fun originalTemplateMatchesTheOriginalProgram() {
        val t = Templates.forge
        assertEquals(Program.days.map { it.exercises }, t.days.map { it.exercises })
        assertEquals("by Hussein Al Herk", t.credit)
    }

    @Test
    fun unknownExerciseStillResolves() {
        val e = Exercises.byId("c-gone-long-ago")
        assertEquals("Removed exercise", e.name)
        assertTrue(e.custom)
    }

    @Test
    fun scheduleParsingAlwaysGivesSevenDays() {
        assertEquals(List(7) { Program.REST }, ProgramStore.parseSchedule(""))
        assertEquals(listOf("D1", "R", "D2", "R", "R", "R", "R"), ProgramStore.parseSchedule("D1,R,D2"))
    }

    private fun set(i: Int, ex: String, reps: Int) = SetEntity(sessionId = 1, exerciseId = ex, exerciseIndex = i, setIndex = 0, reps = reps, weightKg = 0.0)

    @Test
    fun sessionPlanFollowsTheSetsNotTheEditedDay() {
        val day = WorkoutDay("D9", 1, "Push", listOf(ProgramExercise("pushup", 3, 8, 15), ProgramExercise("dips", 3, 6, 10, RepUnit.NONE)))
        // The session was started before "dips" was swapped for "bench_dip" and a set was added.
        val sets = listOf(set(0, "pushup", 10), set(0, "pushup", 9), set(0, "pushup", 8), set(0, "pushup", 8), set(1, "bench_dip", 12))
        val plan = ProgramStore.sessionPlan(day, sets)
        assertEquals(listOf("pushup", "bench_dip"), plan.map { it.exerciseId })
        assertEquals(4, plan[0].sets)          // follows the sets actually in the session
        assertEquals(8, plan[0].repMin)         // keeps the day's prescription where it matches
        assertEquals(12, plan[1].repMin)        // rebuilt from the sets where it doesn't
        assertTrue(ProgramStore.sessionPlan(null, sets).size == 2)
    }

    @Test
    fun timedPrescriptionsReadAsSeconds() {
        assertEquals("30–45 s", ProgramExercise("plank", 3, 30, 45, RepUnit.SEC).repsText)
        assertEquals("20–40 s / side", ProgramExercise("side_plank", 3, 20, 40, RepUnit.SEC_SIDE).repsText)
        assertEquals("10", ProgramExercise("burpee", 3, 10, 10).repsText)
    }
}
