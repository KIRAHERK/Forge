package com.forge.app

import com.forge.app.data.ProgramCode
import com.forge.app.data.UserProgram
import com.forge.app.domain.Equip
import com.forge.app.domain.Exercise
import com.forge.app.domain.Exercises
import com.forge.app.domain.MuscleGroup
import com.forge.app.domain.Program
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.ProgramTemplate
import com.forge.app.domain.RepUnit
import com.forge.app.domain.Templates
import com.forge.app.domain.WorkoutDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64
import java.util.zip.Deflater

class ProgramCodeTest {

    private fun asProgram(t: ProgramTemplate): UserProgram {
        val days = t.days.mapIndexed { i, d -> WorkoutDay("x-d$i", i + 1, d.name, d.exercises) }
        return UserProgram("x", t.name, t.note, t.id, t.week.map { i -> i?.let { days[it].id } ?: Program.REST }, days, 0)
    }

    @After fun clear() { Exercises.custom = emptyMap() }

    @Test
    fun everyTemplateRoundTripsAndFitsOneQr() {
        Templates.all.forEach { t ->
            val p = asProgram(t)
            val code = ProgramCode.encode(p)
            // A version 25 QR at medium error correction holds ~1,000 bytes; keep well under that.
            assertTrue("${t.id}: ${code.length} chars", code.length < 900)
            val back = ProgramCode.decode(code)
            assertNotNull(t.id, back)
            back!!
            assertEquals(t.name, back.n)
            assertEquals(t.week.map { it ?: -1 }, back.w)
            assertEquals(t.days.map { it.name }, back.d.map { it.n })
            assertEquals(t.days.map { it.exercises }, back.d.map { ProgramCode.toExercises(it, emptyMap()) })
        }
    }

    @Test
    fun customExercisesTravelWithTheProgram() {
        Exercises.custom = mapOf(
            "c-abc" to Exercise("c-abc", "Sandbag Carry", Equip.BODYWEIGHT, 120, "", emptyList(), emptyList(), emptyList(), emptyList(), group = MuscleGroup.FULL_BODY, custom = true),
        )
        val day = WorkoutDay("d1", 1, "Strongman", listOf(ProgramExercise("c-abc", 4, 30, 45, RepUnit.SEC), ProgramExercise("deadlift", 3, 5, 8)))
        val p = UserProgram("p", "Yard", "", "", listOf("d1", "R", "R", "d1", "R", "R", "R"), listOf(day), 0)
        val back = ProgramCode.decode(ProgramCode.encode(p))!!
        assertEquals(listOf("c-abc"), back.c.map { it.id })
        assertEquals("Sandbag Carry", back.c[0].n)
        assertEquals(listOf("Sandbag Carry", "Deadlift"), ProgramCode.names(back, back.d[0]))
        // The importer maps the sender's custom id to the receiver's own.
        assertEquals("c-mine", ProgramCode.toExercises(back.d[0], mapOf("c-abc" to "c-mine"))[0].exerciseId)
    }

    @Test
    fun rejectsThingsThatAreNotForgePrograms() {
        assertNull(ProgramCode.decode("https://example.com"))
        assertNull(ProgramCode.decode("FORGE1:not base64 at all!!"))
        assertNull(ProgramCode.decode("FORGE1:"))
        assertNull(ProgramCode.decode("FORGE1:" + "A".repeat(10_000)))
    }

    private fun pack(json: String): String {
        val d = Deflater().apply { setInput(json.toByteArray()); finish() }
        val buf = ByteArray(4096); val n = d.deflate(buf); d.end()
        return ProgramCode.PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(buf.copyOf(n))
    }

    @Test
    fun rejectsUnknownExercisesWithoutADefinition() {
        assertNull(ProgramCode.decode(pack("""{"n":"X","w":[0,-1,-1,-1,-1,-1,-1],"d":[{"n":"A","x":[{"e":"made_up","s":3,"a":8,"b":12}]}]}""")))
    }

    @Test
    fun clampsSillyNumbersAndBadSchedule() {
        val s = ProgramCode.decode(pack("""{"n":"","w":[0,9,-5],"d":[{"n":"A","x":[{"e":"pushup","s":99,"a":5000,"b":-3,"u":"WHAT"}]}]}"""))!!
        assertEquals("Shared program", s.n)
        assertEquals(listOf(0, -1, -1, -1, -1, -1, -1), s.w)
        val e = s.d[0].x[0]
        assertEquals(10, e.s)
        assertEquals(1, e.a)
        assertEquals(600, e.b)
        assertEquals("NONE", e.u)
    }
}
