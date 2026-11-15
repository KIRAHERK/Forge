package com.forge.app.data

import android.content.Context
import com.forge.app.BuildConfig
import com.forge.app.domain.Macros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import kotlin.math.roundToInt

// ------------------------------------------------------------------ picked food

/** One way to measure a food: "½ cup" = 100 g. grams == null means one serving of [FoodPick.base]. For drinks, grams is millilitres. */
data class Portion(val label: String, val grams: Double?)

/** Solids are weighed in grams, drinks measured in millilitres. Values are per 100 of the unit. */
enum class Measure(val sym: String) { G("g"), ML("ml") }

/**
 * A food the user tapped, from any source, before it is logged.
 * Gram-based foods carry [per100]; everything else carries [base] (one serving).
 */
data class FoodPick(
    val name: String,
    val detail: String = "",
    val per100: Macros? = null,
    val base: Macros? = null,
    val portions: List<Portion> = emptyList(),
    val source: String,
    val sourceNote: String = "",
    /** Online and scanned values can be corrected before logging, and saved to My foods. */
    val fromOutside: Boolean = false,
    val servings: Double = 1.0,
    /** g or ml: what [per100] and the portions' amounts are measured in. */
    val unit: Measure = Measure.G,
    /** Scanned barcode, so a label scanned for it can be remembered. */
    val barcode: String? = null,
)

private fun gramsText(g: Double) = if (g % 1.0 == 0.0) g.toInt().toString() else String.format(java.util.Locale.US, "%.1f", g)

/** Portions for a per-100 g food: the given ones first, then 100 g if missing. Grams entry is added by the UI. */
fun portionsFor(given: List<Portion>, unit: Measure = Measure.G): List<Portion> =
    if (given.any { it.grams == 100.0 }) given else given + Portion("100 ${unit.sym}", 100.0)

fun Portion.chip(unit: Measure = Measure.G): String = when {
    grams == null -> label
    label == amountLabel(grams, unit) -> label
    else -> "$label · ${amountLabel(grams, unit)}"
}

fun Macros.per(grams: Double): Macros = this * (grams / 100.0)

fun gramsLabel(g: Double) = amountLabel(g, Measure.G)
fun amountLabel(v: Double, unit: Measure) = "${gramsText(v)} ${unit.sym}"

// ------------------------------------------------------------------ search text

object TextMatch {
    private val marks = Regex("\\p{Mn}+")
    private val tashkeel = Regex("[\\u064B-\\u0652\\u0640]")
    private val punct = Regex("[^\\p{L}\\p{N}]+")

    fun norm(s: String): String {
        var t = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(marks, "")
        t = t.replace(tashkeel, "")
            .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
            .replace('ة', 'ه').replace('ى', 'ي')
        return t.replace(punct, " ").trim()
    }

    /** Every word of the query must appear somewhere in the fields. */
    fun matches(query: String, vararg fields: String): Boolean {
        val words = norm(query).split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return true
        val hay = fields.joinToString(" ") { norm(it) }
        return words.all { hay.contains(it) }
    }
}

// ------------------------------------------------------------------ dishes

@Serializable data class DishPortion(val label: String, val g: Double)

@Serializable
data class Dish(
    val id: String,
    val name: String,
    val ar: String = "",
    val aka: List<String> = emptyList(),
    val kcal: Double,
    val p: Double,
    val f: Double,
    val c: Double,
    val portions: List<DishPortion> = emptyList(),
    val src: String,
) {
    val per100 get() = Macros(kcal.roundToInt(), p, f, c)
    fun matches(q: String) = TextMatch.matches(q, name, ar, aka.joinToString(" "))
}

@Serializable data class DishSource(val name: String, val detail: String = "", val url: String = "")

@Serializable
data class DishBook(
    val version: Int,
    val sources: Map<String, DishSource> = emptyMap(),
    val dishes: List<Dish> = emptyList(),
) {
    fun pick(d: Dish): FoodPick {
        val s = sources[d.src]
        return FoodPick(
            name = d.name,
            detail = d.ar,
            per100 = d.per100,
            portions = portionsFor(d.portions.map { Portion(it.label, it.g) }),
            source = "dish:${d.id}",
            sourceNote = listOfNotNull(s?.name, s?.detail?.takeIf { it.isNotBlank() }).joinToString(" · "),
        )
    }
}

/**
 * Syrian and Levantine dishes. A copy ships in the APK (assets/dishes.json); the newest copy is
 * downloaded from the repo when you open Add food, so dishes can be added without a release.
 */
object Dishes {
    private const val FILE = "dishes.json"
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }
    private val _book = MutableStateFlow<DishBook?>(null)
    val book: StateFlow<DishBook?> = _book
    @Volatile private var lastRefresh = 0L

    val remoteUrl get() = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/app/src/main/assets/$FILE"

    fun parse(text: String): DishBook = json.decodeFromString(DishBook.serializer(), text)

    private fun cache(ctx: Context) = File(ctx.filesDir, FILE)

    suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
        if (_book.value != null) return@withContext
        val bundled = runCatching { parse(ctx.assets.open(FILE).bufferedReader().use { it.readText() }) }.getOrNull()
        val cached = runCatching { parse(cache(ctx).readText()) }.getOrNull()
        _book.value = listOfNotNull(bundled, cached).filter { it.dishes.isNotEmpty() }.maxByOrNull { it.version }
    }

    suspend fun refresh(ctx: Context) = withContext(Dispatchers.IO) {
        load(ctx)
        val now = System.currentTimeMillis()
        if (now - lastRefresh < REFRESH_MS) return@withContext
        lastRefresh = now
        val text = Net.get(remoteUrl) ?: return@withContext
        val book = runCatching { parse(text) }.getOrNull() ?: return@withContext
        if (book.dishes.isEmpty() || book.version < (_book.value?.version ?: 0)) return@withContext
        runCatching { cache(ctx).writeText(text) }
        _book.value = book
    }
}

// ------------------------------------------------------------------ network

object Net {
    val userAgent get() = "Forge-Android/${BuildConfig.VERSION_NAME} (https://github.com/${BuildConfig.UPDATE_REPO})"

    /** GET as text, or null on any failure. */
    fun get(url: String, timeoutMs: Int = 12_000): String? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("User-Agent", userAgent)
            setRequestProperty("Accept", "application/json")
        }
        try {
            if (conn.responseCode !in 200..299) null
            else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()
}

// ------------------------------------------------------------------ Open Food Facts

data class OffProduct(
    val code: String,
    val name: String,
    val brand: String,
    val per100: Macros,
    val servingG: Double?,
    /** Calories were given only in kJ and converted (÷ 4.184). */
    val fromKj: Boolean,
    /** A drink: values are per 100 ml and servings are in ml. */
    val unit: Measure = Measure.G,
    val sourceName: String = "Open Food Facts",
) {
    fun pick(): FoodPick {
        val portions = buildList {
            servingG?.let { add(Portion(com.forge.app.i18n.t("1 serving"), it)) }
            add(Portion("100 ${unit.sym}", 100.0))
        }
        return FoodPick(
            name = name,
            detail = brand,
            per100 = per100,
            portions = portions,
            source = "off:$code",
            sourceNote = "$sourceName · barcode $code" + if (fromKj) " · calories converted from kJ" else "",
            fromOutside = true,
            unit = unit,
            barcode = code,
        )
    }
}

/** Open Food Facts: a free, open database of packaged foods, built from their labels. No key needed. */
object OpenFoodFacts {
    private val json = Json { ignoreUnknownKeys = true }
    private const val FIELDS = "code,product_name,product_name_en,product_name_ar,generic_name,brands,nutriments,serving_quantity,serving_quantity_unit,nutrition_data_per,product_quantity_unit,quantity,categories_tags"

    sealed interface Search {
        data class Ok(val items: List<OffProduct>) : Search
        data object Offline : Search
    }

    sealed interface Lookup {
        data class Found(val product: OffProduct) : Lookup
        /** The product exists but its nutrition table is missing or incomplete. */
        data class NoNutrition(val name: String) : Lookup
        data object NotFound : Lookup
        data object Offline : Lookup
    }

    fun validBarcode(s: String) = s.length in 8..14 && s.all { it.isDigit() }

    suspend fun search(query: String): Search = withContext(Dispatchers.IO) {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val text = Net.get("https://search.openfoodfacts.org/search?q=$q&page_size=40&fields=$FIELDS") ?: return@withContext Search.Offline
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext Search.Offline
        val hits = (root["hits"] as? JsonArray).orEmpty()
        Search.Ok(hits.mapNotNull { (it as? JsonObject)?.let(::parseProduct) }.distinctBy { it.code }.take(25))
    }

    suspend fun lookup(barcode: String): Lookup = withContext(Dispatchers.IO) {
        val text = Net.get("https://world.openfoodfacts.org/api/v2/product/$barcode?fields=$FIELDS")
            ?: return@withContext Lookup.Offline
        val root = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return@withContext Lookup.Offline
        val product = root["product"] as? JsonObject
        if (num(root["status"])?.toInt() != 1 || product == null) return@withContext Lookup.NotFound
        parseProduct(product, barcode)?.let { Lookup.Found(it) } ?: Lookup.NoNutrition(nameOf(product))
    }

    private fun str(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        is JsonArray -> e.firstNotNullOfOrNull { str(it) }
        else -> null
    }

    private fun num(e: JsonElement?): Double? = (e as? JsonPrimitive)?.let { p ->
        p.doubleOrNull ?: p.intOrNull?.toDouble() ?: p.contentOrNull?.replace(',', '.')?.trim()?.toDoubleOrNull()
    }

    private fun nameOf(p: JsonObject) =
        listOf("product_name", "product_name_en", "product_name_ar", "generic_name").firstNotNullOfOrNull { str(p[it]) } ?: ""

    /** A product with a name and all four values per 100 g, or null. Values are never filled in. */
    fun parseProduct(p: JsonObject, code: String? = null): OffProduct? {
        val n = p["nutriments"] as? JsonObject ?: return null
        val name = nameOf(p).ifBlank { return null }
        val kcalDirect = num(n["energy-kcal_100g"])
        val kj = num(n["energy-kj_100g"]) ?: num(n["energy_100g"])
        val kcal = kcalDirect ?: kj?.let { it / 4.184 } ?: return null
        val protein = num(n["proteins_100g"]) ?: return null
        val fat = num(n["fat_100g"]) ?: return null
        val carbs = num(n["carbohydrates_100g"]) ?: return null
        if (kcal < 0 || kcal > 950 || protein < 0 || fat < 0 || carbs < 0) return null
        val brand = str(p["brands"])?.split(',')?.firstOrNull()?.trim().orEmpty()
        val unit = str(p["serving_quantity_unit"])?.lowercase()
        val serving = num(p["serving_quantity"])?.takeIf { it > 0 && it < 2000 && (unit == null || unit == "g" || unit == "ml") }
        val liquid = isDrink(p, unit)
        return OffProduct(
            code = code ?: str(p["code"]) ?: return null,
            name = name,
            brand = brand,
            per100 = Macros(kcal.roundToInt(), protein, fat, carbs),
            servingG = serving,
            fromKj = kcalDirect == null,
            unit = if (liquid) Measure.ML else Measure.G,
        )
    }

    private val mlAmount = Regex("\\d\\s*(ml|cl|dl|l|lt|litre|liter)\\b", RegexOption.IGNORE_CASE)

    /**
     * Open Food Facts stores drinks "per 100 g" in its field names, but the numbers are per 100 ml.
     * A product is a drink when it says so: nutrition per 100 ml, a size in ml or litres, or a drinks category.
     */
    fun isDrink(p: JsonObject, servingUnit: String? = str(p["serving_quantity_unit"])?.lowercase()): Boolean {
        val per = str(p["nutrition_data_per"])?.lowercase()
        if (per == "100ml") return true
        // Entered per 100 g: still a drink if it is sold in ml or filed under drinks.
        if (servingUnit == "ml") return true
        if (str(p["product_quantity_unit"])?.lowercase() == "ml") return true
        if (str(p["quantity"])?.let { mlAmount.containsMatchIn(it) } == true) return true
        val cats = (p["categories_tags"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        return cats.any { it == "en:beverages" || it == "en:drinks" || it == "en:waters" || it == "en:milks" }
            && cats.none { it == "en:dehydrated-beverages" || it == "en:beverage-preparations" || it == "en:instant-beverages" || it == "en:milk-powders" || it == "en:powdered-milks" }
    }
}

// ------------------------------------------------------------------ remembered barcodes

/** A food the user scanned the label for, saved under its barcode. Values are per 100 of [unit], or per serving when per100 is false. */
@Serializable
data class SavedBarcode(
    val name: String,
    val kcal: Int,
    val p: Double,
    val f: Double,
    val c: Double,
    val per100: Boolean = true,
    val servingG: Double? = null,
    val unit: String = "g",
) {
    val macros get() = Macros(kcal, p, f, c)

    fun pick(code: String, shared: Boolean = false): FoodPick {
        val u = if (unit == "ml") Measure.ML else Measure.G
        val note = if (shared) com.forge.app.i18n.t("Forge product list · barcode %s", code)
            else com.forge.app.i18n.t("Saved from your label scan · barcode %s", code)
        return if (per100) FoodPick(
            name = name, per100 = macros, unit = u, barcode = code,
            portions = listOfNotNull(servingG?.let { Portion(com.forge.app.i18n.t("1 serving"), it) }, Portion("100 ${u.sym}", 100.0)),
            source = "barcode:$code", sourceNote = note, fromOutside = true,
        ) else FoodPick(name = name, base = macros, unit = u, barcode = code, source = "barcode:$code", sourceNote = note, fromOutside = true)
    }

    companion object {
        fun of(pick: FoodPick, m: Macros): SavedBarcode = SavedBarcode(
            name = pick.name, kcal = m.kcal, p = m.protein, f = m.fat, c = m.carbs,
            per100 = pick.per100 != null,
            servingG = pick.portions.firstOrNull { it.grams != null && it.grams != 100.0 }?.grams,
            unit = pick.unit.sym,
        )
    }
}

/**
 * Barcodes Open Food Facts doesn't know (most Syrian products), remembered on this phone once their
 * label has been scanned, so the next scan of the same product is instant. Kept in backups.
 */
object BarcodeMemory {
    private const val FILE = "barcodes.json"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val ser = MapSerializer(String.serializer(), SavedBarcode.serializer())
    @Volatile private var cache: Map<String, SavedBarcode>? = null

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    @Synchronized
    fun all(ctx: Context): Map<String, SavedBarcode> = cache ?: (runCatching { json.decodeFromString(ser, file(ctx).readText()) }.getOrNull() ?: emptyMap()).also { cache = it }

    fun get(ctx: Context, code: String): SavedBarcode? = all(ctx)[code]

    /** Readable JSON in the same shape as the "products" of assets/products.json. */
    fun share(items: Map<String, SavedBarcode>): String = Json { prettyPrint = true; encodeDefaults = true }.encodeToString(ser, items)

    @Synchronized
    fun put(ctx: Context, code: String, item: SavedBarcode) = replaceAll(ctx, all(ctx) + (code to item))

    @Synchronized
    fun replaceAll(ctx: Context, items: Map<String, SavedBarcode>) {
        runCatching { file(ctx).writeText(json.encodeToString(ser, items)) }
        cache = items
    }
}

// ------------------------------------------------------------------ shared product list

@Serializable
data class ProductBook(val version: Int, val products: Map<String, SavedBarcode> = emptyMap())

/**
 * Products that no online database has, mostly Syrian, with values copied from their labels.
 * A copy ships in the APK (assets/products.json); the newest copy is downloaded from the repo,
 * so products can be added without a release.
 */
object SharedProducts {
    private const val FILE = "products.json"
    private const val REFRESH_MS = 6 * 60 * 60 * 1000L
    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var book: ProductBook? = null
    @Volatile private var lastRefresh = 0L

    val remoteUrl get() = "https://raw.githubusercontent.com/${BuildConfig.UPDATE_REPO}/main/app/src/main/assets/$FILE"

    fun parse(text: String): ProductBook = json.decodeFromString(ProductBook.serializer(), text)

    private fun cache(ctx: Context) = File(ctx.filesDir, FILE)

    private fun load(ctx: Context): ProductBook? {
        book?.let { return it }
        val bundled = runCatching { parse(ctx.assets.open(FILE).bufferedReader().use { it.readText() }) }.getOrNull()
        val cached = runCatching { parse(cache(ctx).readText()) }.getOrNull()
        return listOfNotNull(bundled, cached).maxByOrNull { it.version }.also { book = it }
    }

    /** The newest list, refreshed from the repo at most every few hours. */
    suspend fun get(ctx: Context, code: String): SavedBarcode? = withContext(Dispatchers.IO) {
        load(ctx)
        val now = System.currentTimeMillis()
        if (now - lastRefresh > REFRESH_MS) {
            lastRefresh = now
            Net.get(remoteUrl, timeoutMs = 6_000)?.let { text ->
                runCatching { parse(text) }.getOrNull()?.takeIf { it.version >= (book?.version ?: 0) }?.let {
                    runCatching { cache(ctx).writeText(text) }
                    book = it
                }
            }
        }
        book?.products?.get(code)
    }
}

// ------------------------------------------------------------------ USDA branded foods

/**
 * USDA FoodData Central, branded foods: US label data, so it mostly helps with imported products.
 * Free; the key is a free data.gov key (DEMO_KEY works at a low rate).
 */
object UsdaBranded {
    private val json = Json { ignoreUnknownKeys = true }

    private fun digits(s: String) = s.trimStart('0')

    /** A product with all four values for this barcode, null if USDA doesn't have it, or Offline. */
    suspend fun lookup(barcode: String): Result<OffProduct?> = withContext(Dispatchers.IO) {
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search?dataType=Branded&pageSize=10" +
            "&query=${URLEncoder.encode(barcode, "UTF-8")}&api_key=${BuildConfig.FDC_KEY}"
        val text = Net.get(url, timeoutMs = 8_000) ?: return@withContext Result.failure(java.io.IOException("offline"))
        val foods = runCatching { json.parseToJsonElement(text).jsonObject["foods"]?.jsonArray }.getOrNull()
            ?: return@withContext Result.success(null)
        val food = foods.mapNotNull { it as? JsonObject }.firstOrNull { f ->
            (f["gtinUpc"] as? JsonPrimitive)?.contentOrNull?.let { digits(it) == digits(barcode) } == true
        } ?: return@withContext Result.success(null)
        Result.success(parse(food, barcode))
    }

    fun parse(f: JsonObject, barcode: String): OffProduct? {
        fun s(k: String) = (f[k] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        fun d(k: String) = (f[k] as? JsonPrimitive)?.doubleOrNull
        val nutrients = (f["foodNutrients"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        fun n(vararg numbers: String) = nutrients.firstOrNull { o -> (o["nutrientNumber"] as? JsonPrimitive)?.contentOrNull in numbers }
            ?.let { (it["value"] as? JsonPrimitive)?.doubleOrNull }
        val kcal = n("208") ?: n("268")?.let { it / 4.184 } ?: return null
        val protein = n("203") ?: return null
        val fat = n("204") ?: return null
        val carbs = n("205") ?: return null
        if (kcal < 0 || kcal > 950) return null
        val name = s("description")?.lowercase()?.replaceFirstChar { it.uppercase() } ?: return null
        val unit = s("servingSizeUnit")?.lowercase()
        val liquid = unit == "ml" || unit == "mlt" || s("foodCategory")?.lowercase()?.let { "beverage" in it || "drink" in it || "milk" in it || "juice" in it || "water" in it } == true
        val serving = d("servingSize")?.takeIf { it > 0 && it < 2000 && unit in listOf("g", "grm", "ml", "mlt") }
        return OffProduct(
            code = barcode,
            name = name,
            brand = s("brandName") ?: s("brandOwner").orEmpty(),
            per100 = Macros(kcal.roundToInt(), protein, fat, carbs),
            servingG = serving,
            fromKj = n("208") == null,
            unit = if (liquid) Measure.ML else Measure.G,
            sourceName = "USDA FoodData Central",
        )
    }
}

// ------------------------------------------------------------------ barcode lookup, all sources

/**
 * Where a scanned barcode is looked up, in order: products you scanned the label for (this phone),
 * the shared Syrian product list, Open Food Facts, then USDA branded foods.
 */
object BarcodeLookup {
    sealed interface Outcome {
        data class Found(val pick: FoodPick) : Outcome
        data class NoNutrition(val name: String) : Outcome
        data object NotFound : Outcome
        data object Offline : Outcome
    }

    suspend fun find(ctx: Context, code: String): Outcome {
        BarcodeMemory.get(ctx, code)?.let { return Outcome.Found(it.pick(code)) }
        SharedProducts.get(ctx, code)?.let { return Outcome.Found(it.pick(code, shared = true)) }
        val off = OpenFoodFacts.lookup(code)
        if (off is OpenFoodFacts.Lookup.Found) return Outcome.Found(off.product.pick())
        val usda = UsdaBranded.lookup(code)
        usda.getOrNull()?.let { return Outcome.Found(it.pick()) }
        return when {
            off is OpenFoodFacts.Lookup.NoNutrition -> Outcome.NoNutrition(off.name)
            off is OpenFoodFacts.Lookup.Offline && usda.isFailure -> Outcome.Offline
            else -> Outcome.NotFound
        }
    }
}
