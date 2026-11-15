package com.forge.app

import com.forge.app.domain.Basis
import com.forge.app.domain.LabelParser
import com.forge.app.domain.LabelResult
import com.forge.app.domain.OcrLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

/**
 * Real nutrition-label photos from public GitHub projects (UK, US, Australian, Indian and Middle Eastern
 * packs, some tilted or curved), read with Tesseract and grouped into lines the way ML Kit returns them.
 * Two readings per photo (sparse and page layout). Scores how many of the four values come out right.
 */
class LabelCorpusTest {
    private val dir = File("src/test/resources/labels")
    private val expected = Json.parseToJsonElement(File(dir, "expected.json").readText()).jsonObject

    private fun lines(f: File) = Json.parseToJsonElement(f.readText()).jsonArray.map { e ->
        val a = e.jsonArray
        OcrLine(a[0].jsonPrimitive.content, a[1].jsonPrimitive.int, a[2].jsonPrimitive.int, a[3].jsonPrimitive.int, a[4].jsonPrimitive.int)
    }

    private fun values(o: JsonObject, key: String): List<Double?>? = (o[key] as? JsonArray)?.map { if (it is JsonNull) null else it.jsonPrimitive.double }

    private fun close(got: Double?, want: Double?, kcal: Boolean): Boolean = when {
        want == null -> true
        got == null -> false
        kcal -> abs(got - want) <= maxOf(2.0, want * 0.02)
        else -> abs(got - want) <= 0.051
    }

    data class Score(val right: Int, val total: Int, val wrong: Int, val notes: String)

    /** Compares one basis (per 100 or per serving) with the column the parser labelled that way, else any column. */
    private fun score(r: LabelResult, want: List<Double?>, basis: Basis): Score {
        val cols = r.columns.filter { it.basis == basis }.ifEmpty { r.columns }
        val best = cols.maxByOrNull { c -> listOf(c.kcal, c.protein, c.fat, c.carbs).zip(want).withIndex().count { (i, p) -> close(p.first, p.second, i == 0) && p.second != null } }
        val got = best?.let { listOf(it.kcal, it.protein, it.fat, it.carbs) } ?: listOf(null, null, null, null)
        var right = 0; var total = 0; var wrong = 0
        val notes = StringBuilder()
        got.zip(want).forEachIndexed { i, (g, w) ->
            if (w == null) return@forEachIndexed
            total++
            if (close(g, w, i == 0)) right++ else {
                if (g != null) wrong++
                notes.append(" ${listOf("kcal", "P", "F", "C")[i]}=${g ?: "-"}(want $w)")
            }
        }
        return Score(right, total, wrong, notes.toString())
    }

    fun run(): Triple<Int, Int, Int> {
        var right = 0; var total = 0; var wrong = 0
        dir.listFiles { f -> f.name.endsWith(".json") && f.name != "expected.json" }!!.sortedBy { it.name }.forEach { f ->
            val name = f.name.substringBefore(".psm")
            val e = expected[name]!!.jsonObject
            val r = LabelParser.parse(lines(f))
            val parts = listOfNotNull(
                values(e, "per100")?.let { "100" to score(r, it, Basis.PER_100) },
                values(e, "serving")?.let { "srv" to score(r, it, Basis.SERVING) },
            )
            parts.forEach { (_, s) -> right += s.right; total += s.total; wrong += s.wrong }
            println(String.format("%-28s %s", f.name.removeSuffix(".json"), parts.joinToString("  ") { (k, s) -> "$k ${s.right}/${s.total}${s.notes}" }))
        }
        println("TOTAL $right/$total right, $wrong wrong (not just missing)")
        return Triple(right, total, wrong)
    }

    @Test fun corpus() {
        val (right, total, wrong) = run()
        // Measured when the corpus was added: the old parser got 114 right and 111 wrong.
        // Raise these when the parser gets better; never lower them.
        assertTrue("right $right of $total", right >= 192)
        assertTrue("wrong $wrong", wrong <= 29)
    }

    @Test fun labelPrintedAsASentence() {
        fun l(t: String, y: Int) = OcrLine(t, 0, y, 900, y + 30)
        val r = LabelParser.parse(
            listOf(
                l("Nutrition information per 100g: Energy 1850kJ/442kcal, Fat 22g, of which", 0),
                l("saturates 10g, Carbohydrate 52g, of which sugars 30g, Fibre 2g,", 34),
                l("Protein 6.5g, Salt 0.3g", 68),
            ),
        )
        val c = r.best!!
        assertEquals(Basis.PER_100, c.basis)
        assertEquals(listOf(442.0, 6.5, 22.0, 52.0), listOf(c.kcal, c.protein, c.fat, c.carbs))
        assertEquals(true, c.addsUp)
    }

    @Test fun lostDecimalPointIsFixedWhenTheValuesDontAddUp() {
        fun l(t: String, x: Int, y: Int) = OcrLine(t, x, y, x + 200, y + 30)
        val c = LabelParser.parse(
            listOf(
                l("Per 100 g", 300, 0),
                l("Energy", 0, 50), l("406 kcal", 300, 50),
                l("Fat", 0, 100), l("1.5 g", 300, 100),
                l("Carbohydrate", 0, 150), l("89.1 g", 300, 150),
                l("Protein", 0, 200), l("76", 300, 200),
            ),
        ).best!!
        assertEquals(7.6, c.protein)
        assertEquals(listOf("protein 76 → 7.6"), c.fixes)
        assertEquals(true, c.addsUp)
    }
}

