package com.forge.app.data

import android.content.Context
import com.forge.app.BuildConfig
import com.forge.app.domain.Macros
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
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

/** One way to measure a food: "½ cup" = 100 g. grams == null means one serving of [FoodPick.base]. */
data class Portion(val label: String, val grams: Double?)

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
)

private fun gramsText(g: Double) = if (g % 1.0 == 0.0) g.toInt().toString() else String.format(java.util.Locale.US, "%.1f", g)

/** Portions for a per-100 g food: the given ones first, then 100 g if missing. Grams entry is added by the UI. */
fun portionsFor(given: List<Portion>): List<Portion> =
    if (given.any { it.grams == 100.0 }) given else given + Portion("100 g", 100.0)

fun Portion.chip(): String = when {
    grams == null -> label
    label == "${gramsText(grams)} g" -> label
    else -> "$label · ${gramsText(grams)} g"
}

fun Macros.per(grams: Double): Macros = this * (grams / 100.0)

fun gramsLabel(g: Double) = "${gramsText(g)} g"

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
) {
    fun pick(): FoodPick {
        val portions = buildList {
            servingG?.let { add(Portion("1 serving", it)) }
            add(Portion("100 g", 100.0))
        }
        return FoodPick(
            name = name,
            detail = brand,
            per100 = per100,
            portions = portions,
            source = "off:$code",
            sourceNote = "Open Food Facts · barcode $code" + if (fromKj) " · calories converted from kJ" else "",
            fromOutside = true,
        )
    }
}

/** Open Food Facts: a free, open database of packaged foods, built from their labels. No key needed. */
object OpenFoodFacts {
    private val json = Json { ignoreUnknownKeys = true }
    private const val FIELDS = "code,product_name,product_name_en,product_name_ar,generic_name,brands,nutriments,serving_quantity,serving_quantity_unit"

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
        return OffProduct(
            code = code ?: str(p["code"]) ?: return null,
            name = name,
            brand = brand,
            per100 = Macros(kcal.roundToInt(), protein, fat, carbs),
            servingG = serving,
            fromKj = kcalDirect == null,
        )
    }
}
