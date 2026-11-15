package com.forge.app

import com.forge.app.data.Measure
import com.forge.app.data.OpenFoodFacts
import com.forge.app.data.SavedBarcode
import com.forge.app.data.SharedProducts
import com.forge.app.data.UsdaBranded
import com.forge.app.data.chip
import com.forge.app.domain.LabelParser
import com.forge.app.domain.OcrLine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DrinkUnitTest {
    private fun obj(s: String) = Json.parseToJsonElement(s).jsonObject
    private val nutr = """"nutriments":{"energy-kcal_100g":42,"proteins_100g":0,"fat_100g":0,"carbohydrates_100g":10.6}"""

    @Test fun offDrinkIsMeasuredInMl() {
        val byPer = OpenFoodFacts.parseProduct(obj("""{"code":"1","product_name":"Cola","nutrition_data_per":"100ml",$nutr}"""))!!
        assertEquals(Measure.ML, byPer.unit)
        val byQuantity = OpenFoodFacts.parseProduct(obj("""{"code":"2","product_name":"Juice","quantity":"1 L",$nutr}"""))!!
        assertEquals(Measure.ML, byQuantity.unit)
        val byCategory = OpenFoodFacts.parseProduct(obj("""{"code":"3","product_name":"Ayran","categories_tags":["en:dairies","en:beverages"],$nutr}"""))!!
        assertEquals(Measure.ML, byCategory.unit)
        val pick = OpenFoodFacts.parseProduct(obj("""{"code":"4","product_name":"Cola","quantity":"330 ml","serving_quantity":330,"serving_quantity_unit":"ml",$nutr}"""))!!.pick()
        assertEquals(Measure.ML, pick.unit)
        assertEquals("100 ml", pick.portions.last().label)
        assertTrue(pick.portions.first().chip(pick.unit).endsWith("330 ml"))
    }

    @Test fun solidStaysInGrams() {
        val p = OpenFoodFacts.parseProduct(obj("""{"code":"5","product_name":"Biscuits","quantity":"200 g","categories_tags":["en:snacks"],$nutr}"""))!!
        assertEquals(Measure.G, p.unit)
        val powder = OpenFoodFacts.parseProduct(obj("""{"code":"6","product_name":"Milk powder","categories_tags":["en:milks","en:milk-powders"],$nutr}"""))!!
        assertEquals(Measure.G, powder.unit)
    }

    @Test fun usdaBrandedDrink() {
        val f = obj(
            """{"description":"ORANGE JUICE","brandName":"Sunny","gtinUpc":"012345678905","servingSize":240,"servingSizeUnit":"ml",
            "foodCategory":"Juices","foodNutrients":[{"nutrientNumber":"208","value":45},{"nutrientNumber":"203","value":0.7},
            {"nutrientNumber":"204","value":0.2},{"nutrientNumber":"205","value":10.4}]}""",
        )
        val p = UsdaBranded.parse(f, "0012345678905")!!
        assertEquals(Measure.ML, p.unit)
        assertEquals(45, p.per100.kcal)
        assertEquals(240.0, p.servingG!!, 0.0)
        assertTrue(p.pick().sourceNote.startsWith("USDA FoodData Central"))
    }

    @Test fun labelPer100mlIsADrink() {
        fun line(text: String, x: Int, y: Int) = OcrLine(text, x, y, x + 200, y + 30)
        val r = LabelParser.parse(
            listOf(
                line("Nutrition facts", 0, 0), line("Per 100 ml", 250, 50),
                line("Energy", 0, 100), line("46 kcal", 250, 100),
                line("Fat", 0, 150), line("0 g", 250, 150),
                line("Carbohydrate", 0, 200), line("11 g", 250, 200),
                line("Protein", 0, 250), line("0.5 g", 250, 250),
            ),
        )
        assertTrue(r.liquid)
    }

    @Test fun savedBarcodeRoundTrip() {
        val s = SavedBarcode("Ayran", 36, 1.7, 1.9, 2.8, per100 = true, servingG = 250.0, unit = "ml")
        val pick = s.pick("6210000000001")
        assertEquals(Measure.ML, pick.unit)
        assertEquals("barcode:6210000000001", pick.source)
        val again = SavedBarcode.of(pick, pick.per100!!)
        assertEquals(s, again)
    }

    @Test fun bundledProductListParses() {
        val text = File("src/main/assets/products.json").readText()
        val book = SharedProducts.parse(text)
        assertNotNull(book)
        book.products.keys.forEach { assertTrue("bad barcode $it", OpenFoodFacts.validBarcode(it)) }
    }
}
