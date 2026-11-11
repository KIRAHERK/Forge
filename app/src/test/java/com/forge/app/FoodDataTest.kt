package com.forge.app

import com.forge.app.data.Dishes
import com.forge.app.data.OpenFoodFacts
import com.forge.app.data.Portion
import com.forge.app.data.TextMatch
import com.forge.app.data.chip
import com.forge.app.data.portionsFor
import com.forge.app.domain.Basis
import com.forge.app.domain.LabelParser
import com.forge.app.domain.OcrLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs

class LabelParserTest {
    private fun line(text: String, x: Int, y: Int, w: Int = 200, h: Int = 30) = OcrLine(text, x, y, x + w, y + h)

    @Test fun europeanTableWithTwoColumns() {
        val r = LabelParser.parse(
            listOf(
                line("Nutrition information", 0, 0, 400),
                line("Typical values", 0, 50), line("Per 100g", 250, 52, 120), line("Per serving (30g)", 400, 50, 160),
                line("Energy", 0, 100), line("1650 kJ / 395 kcal", 250, 101, 140), line("495 kJ / 119 kcal", 400, 99, 140),
                line("Fat", 0, 150), line("10.5 g", 250, 150, 80), line("3,2 g", 400, 151, 80),
                line("of which saturates", 0, 200), line("3 g", 250, 200, 80), line("0.9 g", 400, 200, 80),
                line("Carbohydrate", 0, 250), line("60 g", 250, 250, 80), line("18 g", 400, 250, 80),
                line("of which sugars", 0, 300), line("12 g", 250, 300, 80), line("3.6 g", 400, 300, 80),
                line("Protein", 0, 350), line("12g", 250, 350, 80), line("3.6g", 400, 350, 80),
            ),
        )
        assertEquals(2, r.columns.size)
        val a = r.columns[0]
        assertEquals(Basis.PER_100, a.basis)
        assertEquals(395.0, a.kcal)
        assertEquals(10.5, a.fat)
        assertEquals(60.0, a.carbs)
        assertEquals(12.0, a.protein)
        val b = r.columns[1]
        assertEquals(Basis.SERVING, b.basis)
        assertEquals(119.0, b.kcal)
        assertEquals(3.2, b.fat)
        assertEquals(30.0, r.servingG)
    }

    @Test fun americanNutritionFacts() {
        val r = LabelParser.parse(
            listOf(
                line("Nutrition Facts", 0, 0, 400),
                line("8 servings per container", 0, 40, 300),
                line("Serving size 2/3 cup (55g)", 0, 80, 400),
                line("Amount per serving", 0, 120, 300),
                line("Calories", 0, 160), line("230", 330, 158, 70),
                line("% Daily Value*", 250, 200, 150),
                line("Total Fat 8g", 0, 240), line("10%", 330, 240, 60),
                line("Saturated Fat 1g", 20, 280), line("5%", 330, 280, 60),
                line("Total Carbohydrate 37g", 0, 320, 280), line("13%", 330, 320, 60),
                line("Protein 3g", 0, 360),
                line("2,000 calories a day is used for general nutrition advice.", 0, 420, 600),
            ),
        )
        assertEquals(1, r.columns.size)
        val c = r.best!!
        assertEquals(Basis.SERVING, c.basis)
        assertEquals(230.0, c.kcal)
        assertEquals(8.0, c.fat)
        assertEquals(37.0, c.carbs)
        assertEquals(3.0, c.protein)
        assertEquals(55.0, r.servingG)
    }

    @Test fun separateEnergyRowsAndOcrSlips() {
        val c = LabelParser.parse(
            listOf(
                line("Nutrition / 100 g", 0, 0, 300),
                line("Energy (kJ)", 0, 50), line("2O90", 300, 50, 80),
                line("Energy (kcal)", 0, 100), line("5OO", 300, 100, 80),
                line("Fat", 0, 150), line("3l g", 300, 150, 80),
                line("Carbohydrates", 0, 200), line("5O,5 g", 300, 200, 80),
                line("Proteins", 0, 250), line("<0.5 g", 300, 250, 80),
            ),
        ).best!!
        assertEquals(Basis.PER_100, c.basis)
        assertEquals(500.0, c.kcal)
        assertEquals(31.0, c.fat)
        assertEquals(50.5, c.carbs)
        assertEquals(0.5, c.protein)
        assertFalse(c.kcalFromKj)
    }

    @Test fun kilojoulesOnlyAreConvertedAndMissingValuesStayEmpty() {
        val c = LabelParser.parse(
            listOf(
                line("per 100 g", 0, 0),
                line("Energy", 0, 50), line("1046 kJ", 300, 50, 100),
                line("Protein", 0, 100), line("9 g", 300, 100, 60),
            ),
        ).best!!
        assertEquals(250.0, c.kcal!!, 0.5)
        assertTrue(c.kcalFromKj)
        assertNull(c.fat)
        assertNull(c.carbs)
    }

    @Test fun wrappedCellsStayWithTheirRow() {
        // Like a real table: values right-aligned, some cells wrapped onto two or three lines.
        val c = LabelParser.parse(
            listOf(
                line("Labneh", 70, 60, 140, 40),
                line("Strained yoghurt · 500 g", 70, 112, 240, 24),
                line("Nutrition information", 87, 220, 240, 26),
                line("Per 100 g", 383, 220, 106, 26),
                line("Per", 543, 193, 40, 26), line("serving (30", 543, 220, 127, 26), line("g)", 543, 252, 23, 26),
                line("Energy", 87, 318, 82, 28),
                line("837 kJ /", 422, 302, 91, 28), line("201 kcal", 417, 334, 96, 28),
                line("251 kJ / 60", 547, 302, 127, 28), line("kcal", 627, 334, 47, 28),
                line("Fat", 87, 391, 38, 28), line("17 g", 465, 391, 48, 28), line("5.1 g", 615, 391, 59, 28),
                line("of which saturates", 112, 448, 209, 28), line("11 g", 466, 448, 47, 28), line("3.3 g", 614, 448, 60, 28),
                line("Carbohydrate", 87, 505, 156, 28), line("5.0 g", 456, 505, 57, 28), line("1.5 g", 616, 505, 58, 28),
                line("of which sugars", 112, 562, 181, 28), line("4.2 g", 455, 562, 58, 28), line("1.3 g", 615, 562, 59, 28),
                line("Protein", 87, 619, 81, 28), line("7.1 g", 456, 619, 57, 28), line("2.1 g", 616, 619, 58, 28),
                line("Salt", 87, 676, 45, 28), line("1.2 g", 457, 676, 56, 28), line("0.36 g", 602, 676, 72, 28),
            ),
        )
        assertEquals(2, c.columns.size)
        val a = c.columns[0]
        assertEquals(Basis.PER_100, a.basis)
        assertEquals(201.0, a.kcal)
        assertEquals(17.0, a.fat)
        assertEquals(5.0, a.carbs)
        assertEquals(7.1, a.protein)
        assertEquals(Basis.SERVING, c.columns[1].basis)
        assertEquals(60.0, c.columns[1].kcal)
        assertEquals(30.0, c.servingG)
    }

    @Test fun realOcrOutputWithGReadAsNine() {
        // Exactly what ML Kit returned for a photo of a printed label on the test emulator.
        fun l(s: String): OcrLine {
            val (box, text) = s.split(" | ", limit = 2)
            val (a, b, c, d) = box.split(',').map { it.trim().toInt() }
            return OcrLine(text, a, b, c, d)
        }
        val r = LabelParser.parse(
            """
            73,67,210,96 | Labneh
            71,115,305,135 | Strained yoghurt 500 g
            88,224,323,241 | Nutrition information
            87,320,167,345 | Energy
            88,394,123,413 | Fat
            112,449,320,472 | of which saturates
            86,506,242,531 | Carbohydrate
            112,561,291,589 | of which sugars
            88,622,167,641 | Protein
            86,678,130,697 | Salt
            383,220,488,244 | Per 100 g
            423,306,510,325 | 837 kJ /
            417,337,512,356 | 201 kcal
            466,394,512,418 | 17g
            468,451,512,473 | 11 g
            457,508,512,532 | 5.0 g
            454,561,510,589 | 4.2 g
            467,621,512,647 | 7.1 g
            457,676,511,702 | 1.2 g
            545,196,582,213 | Per
            544,221,667,245 | serving (30
            553,253,559,275 | 9
            548,306,672,325 | 251 kJ / 60
            630,337,672,356 | kcal
            617,394,671,418 | 5.1 g
            616,446,673,475 | 3.3 9
            619,508,672,532 | 1.5 g
            619,565,672,589 | 1.3 g
            617,622,672,646 | 2.1 g
            601,675,672,702 | 0.36 g
            """.trimIndent().lines().map(::l),
        )
        assertEquals(30.0, r.servingG)
        assertEquals(listOf(Basis.PER_100, Basis.SERVING), r.columns.map { it.basis })
        val (a, b) = r.columns
        assertEquals(listOf(201.0, 7.1, 17.0, 5.0), listOf(a.kcal, a.protein, a.fat, a.carbs))
        assertEquals(listOf(60.0, 2.1, 5.1, 1.5), listOf(b.kcal, b.protein, b.fat, b.carbs))
    }

    @Test fun twoBareColumnsAreNotMistakenForAUnit() {
        val c = LabelParser.parse(
            listOf(
                line("per 100 g", 0, 0), line("per 30 g", 400, 0, 100),
                line("Protein (g)", 0, 50), line("12", 250, 50, 40), line("9", 400, 50, 20),
            ),
        ).columns
        assertEquals(listOf(12.0, 9.0), c.map { it.protein })
    }

    @Test fun textWithoutATableReadsAsEmpty() {
        assertTrue(LabelParser.parse(listOf(line("Ingredients: wheat flour, sugar, E330", 0, 0, 400))).empty)
        assertTrue(LabelParser.parse(emptyList()).empty)
    }
}

class FoodDataTest {
    private val book = Dishes.parse(File("src/main/assets/dishes.json").readText())

    @Test fun dishListIsSourcedAndConsistent() {
        assertTrue(book.dishes.size >= 30)
        assertEquals(book.dishes.size, book.dishes.map { it.id }.toSet().size)
        book.dishes.forEach { d ->
            assertTrue("${d.id} source", book.sources.containsKey(d.src))
            assertTrue("${d.id} arabic name", d.ar.isNotBlank())
            // Energy agrees with 4-4-9 within 8% (FAO used food-specific factors, so allow some room).
            val atwater = 4 * d.p + 4 * d.c + 9 * d.f
            assertTrue("${d.id}: ${d.kcal} vs $atwater", abs(atwater - d.kcal) / d.kcal < 0.08)
            d.portions.forEach { assertTrue(d.id, it.g > 0) }
        }
    }

    @Test fun dishSearchWorksInEnglishArabicAndCommonSpellings() {
        fun find(q: String) = book.dishes.filter { it.matches(q) }.map { it.id }
        assertTrue("kibbeh_sayniyeh" in find("kibbeh"))
        assertTrue("kibbeh_sayniyeh" in find("كبة"))
        assertTrue("yabraq" in find("ورق عنب"))
        assertTrue("yabraq" in find("Vine leaves"))
        assertTrue("mloukhieh" in find("molokhia"))
        assertTrue("shish_barak" in find("shish barak"))
        assertTrue("halawa" in find("حلاوه")) // ة typed as ه
        assertTrue("lahm_bi_ajeen" in find("صفيحة"))
        assertTrue(find("pizza").isEmpty())
        assertTrue(TextMatch.matches("", "anything"))
    }

    @Test fun dishPickUsesPortionsAndSource() {
        val p = book.pick(book.dishes.first { it.id == "kibbeh_sayniyeh" })
        assertEquals(266, p.per100!!.kcal)
        assertEquals(listOf("½ square · 76 g", "100 g"), p.portions.map { it.chip() })
        assertTrue(p.sourceNote.startsWith("Lab-measured"))
        assertEquals(listOf(Portion("½ cup", 100.0)), portionsFor(listOf(Portion("½ cup", 100.0))))
    }

    @Test fun openFoodFactsProducts() {
        fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
        val labneh = OpenFoodFacts.parseProduct(
            obj("""{"code":"7627536579409","product_name":"Labneh","brands":"Cuisine Orientale, Other","serving_quantity":"160","nutriments":{"energy-kcal_100g":201,"proteins_100g":"7.1","fat_100g":17,"carbohydrates_100g":5}}"""),
        )!!
        assertEquals("Labneh", labneh.name)
        assertEquals("Cuisine Orientale", labneh.brand)
        assertEquals(201, labneh.per100.kcal)
        assertEquals(7.1, labneh.per100.protein, 0.001)
        assertEquals(160.0, labneh.servingG)
        assertEquals(listOf("1 serving · 160 g", "100 g"), labneh.pick().portions.map { it.chip() })

        val kjOnly = OpenFoodFacts.parseProduct(
            obj("""{"product_name":"X","brands":["A"],"nutriments":{"energy_100g":836.8,"proteins_100g":1,"fat_100g":1,"carbohydrates_100g":1}}"""), "12345678",
        )!!
        assertEquals(200, kjOnly.per100.kcal)
        assertTrue(kjOnly.fromKj)
        assertEquals("A", kjOnly.brand)

        // Incomplete nutrition is rejected rather than filled in.
        assertNull(OpenFoodFacts.parseProduct(obj("""{"code":"1","product_name":"Y","nutriments":{"energy-kcal_100g":100,"fat_100g":1,"carbohydrates_100g":1}}""")))
        assertNull(OpenFoodFacts.parseProduct(obj("""{"code":"1","nutriments":{"energy-kcal_100g":100,"proteins_100g":1,"fat_100g":1,"carbohydrates_100g":1}}""")))
        assertTrue(OpenFoodFacts.validBarcode("6260918600585"))
        assertFalse(OpenFoodFacts.validBarcode("12ab"))
    }
}
