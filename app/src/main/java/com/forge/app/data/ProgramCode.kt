package com.forge.app.data

import com.forge.app.domain.Equip
import com.forge.app.domain.Exercises
import com.forge.app.domain.MuscleGroup
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.RepUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * A program packed into a short text for a QR code: "FORGE1:" + base64url(deflate(json)).
 * Custom exercises the program uses travel with it, so the receiver gets them too.
 * Anything can be put in a QR, so decoding checks and clamps every field.
 */
object ProgramCode {
    const val PREFIX = "FORGE1:"

    @Serializable data class Ex(val e: String, val s: Int, val a: Int, val b: Int, val u: String = "NONE", val t: String? = null)
    @Serializable data class Day(val n: String, val x: List<Ex>)
    @Serializable data class Custom(val id: String, val n: String, val q: String, val g: String, val r: Int)
    @Serializable data class Shared(
        val v: Int = 1,
        val n: String,
        /** Monday..Sunday: index into [d], or -1 for rest. */
        val w: List<Int>,
        val d: List<Day>,
        val c: List<Custom> = emptyList(),
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

    // Limits keep a hostile or broken code from making a huge program.
    private const val MAX_DAYS = 14
    private const val MAX_EXERCISES = 30
    private const val MAX_NAME = 40
    private const val MAX_TEXT = 6000

    fun encode(p: UserProgram): String {
        val week = p.schedule.map { slot -> p.days.indexOfFirst { it.id == slot } }
        val used = p.days.flatMap { d -> d.exercises.map { it.exerciseId } }.toSet()
        val custom = used.filterNot { Exercises.isBuiltIn(it) }.mapNotNull { id ->
            Exercises.custom[id]?.let { Custom(id, it.name, it.equip.name, it.group.name, it.restSec) }
        }
        val shared = Shared(
            n = p.name,
            w = week,
            d = p.days.map { d -> Day(d.name, d.exercises.map { Ex(it.exerciseId, it.sets, it.repMin, it.repMax, it.unit.name, it.tag) }) },
            c = custom,
        )
        val bytes = json.encodeToString(Shared.serializer(), shared).toByteArray(Charsets.UTF_8)
        val deflater = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(bytes); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) out.write(buf, 0, deflater.deflate(buf))
        deflater.end()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }

    /** Null when the text isn't a Forge program or doesn't make sense. */
    fun decode(text: String): Shared? = runCatching {
        val t = text.trim()
        if (!t.startsWith(PREFIX) || t.length > MAX_TEXT) return null
        val packed = Base64.getUrlDecoder().decode(t.removePrefix(PREFIX))
        val inflater = Inflater().apply { setInput(packed) }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
            out.write(buf, 0, n)
            if (out.size() > 64_000) return null
        }
        inflater.end()
        clean(json.decodeFromString(Shared.serializer(), out.toString(Charsets.UTF_8.name())))
    }.getOrNull()

    private fun clean(s: Shared): Shared? {
        val days = s.d.take(MAX_DAYS).map { d ->
            Day(
                n = d.n.trim().take(MAX_NAME).ifBlank { "Day" },
                x = d.x.take(MAX_EXERCISES).map { e ->
                    val unit = runCatching { RepUnit.valueOf(e.u) }.getOrDefault(RepUnit.NONE)
                    val lo = e.a.coerceIn(1, 600)
                    val hi = e.b.coerceIn(1, 600)
                    Ex(e.e.take(60), e.s.coerceIn(1, 10), minOf(lo, hi), maxOf(lo, hi), unit.name, e.t?.take(MAX_NAME))
                },
            )
        }
        if (days.isEmpty()) return null
        val week = List(7) { i -> s.w.getOrNull(i)?.takeIf { it in days.indices } ?: -1 }
        val customIds = s.c.map { it.id }.toSet()
        // Every exercise must be built in or come with the code.
        if (days.any { d -> d.x.any { !Exercises.isBuiltIn(it.e) && it.e !in customIds } }) return null
        val custom = s.c.take(50).map {
            Custom(
                id = it.id.take(60),
                n = it.n.trim().take(MAX_NAME).ifBlank { "Exercise" },
                q = runCatching { Equip.valueOf(it.q) }.getOrDefault(Equip.BODYWEIGHT).name,
                g = runCatching { MuscleGroup.valueOf(it.g) }.getOrDefault(MuscleGroup.FULL_BODY).name,
                r = it.r.coerceIn(15, 600),
            )
        }
        return Shared(1, s.n.trim().take(MAX_NAME).ifBlank { "Shared program" }, week, days, custom)
    }

    /** The exercises of a decoded day, for previews. Custom ones use the name that came with the code. */
    fun names(s: Shared, d: Day): List<String> =
        d.x.map { e -> s.c.firstOrNull { it.id == e.e }?.n ?: Exercises.byId(e.e).name }

    fun daysPerWeek(s: Shared) = s.w.count { it >= 0 }

    fun toExercises(d: Day, idMap: Map<String, String>): List<ProgramExercise> = d.x.map { e ->
        ProgramExercise(idMap[e.e] ?: e.e, e.s, e.a, e.b, runCatching { RepUnit.valueOf(e.u) }.getOrDefault(RepUnit.NONE), e.t)
    }

}
