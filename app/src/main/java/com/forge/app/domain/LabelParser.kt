package com.forge.app.domain

import kotlin.math.abs

/** One line of text read from a photo, with its box in image pixels. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = (bottom - top).coerceAtLeast(1)
    val centerY get() = (top + bottom) / 2
}

enum class Basis { PER_100, SERVING, UNKNOWN }

/** One column of a nutrition table. A null value was not found on the label. */
data class LabelColumn(
    val kcal: Double?,
    val protein: Double?,
    val fat: Double?,
    val carbs: Double?,
    val basis: Basis,
    /** Calories were printed only in kJ and converted (÷ 4.184). */
    val kcalFromKj: Boolean = false,
) {
    val found get() = listOf(kcal, protein, fat, carbs).count { it != null }
}

data class LabelResult(val columns: List<LabelColumn>, val servingG: Double?) {
    val best: LabelColumn? get() = columns.firstOrNull()
    val empty get() = columns.all { it.found == 0 }
}

/**
 * Reads calories, protein, fat and carbohydrate from the text of a nutrition table
 * (English, plus common French/German/Spanish/Italian words). It only reads what is
 * printed: anything it cannot find stays null for the user to type in.
 */
object LabelParser {
    private const val KJ_PER_KCAL = 4.184

    private val energyWords = listOf("energy", "energie", "énergie", "energía", "energia", "calorie", "kcal", "kj", "brennwert", "valeur énergétique")
    private val proteinWords = listOf("protein", "protéine", "proteine", "eiweiß", "eiweiss", "proteína", "proteina")
    private val fatWords = listOf("fat", "lipid", "matières grasses", "matieres grasses", "fett", "grasas", "grassi")
    private val energyExclude = listOf("from fat", "diet", "daily", "a day", "advice", "based on")
    private val fatExclude = listOf("satur", "trans", "mono", "poly", "of which", "dont", "davon", "di cui", "de las cuales", "sat.", "calorie")
    private val carbWords = listOf("carbohydrate", "carbs", "glucide", "kohlenhydrat", "hidratos", "carboidrati", "carbohidrato")
    private val carbExclude = listOf("sugar", "sucre", "zucker", "zucchero", "azúcar", "of which", "dont", "davon", "fibre", "fiber", "polyol", "starch")
    private val servingWords = listOf("serving", "portion", "per pack", "porción", "porzione")
    private val headerAmount = Regex("(?<![\\d.,])(\\d{1,4})\\s*(?:g|ml|gr)\\b", RegexOption.IGNORE_CASE)
    private val per100 = Regex("(?:per|pour|pro|por|per)?\\s*100\\s*(?:g|ml|gr)\\b", RegexOption.IGNORE_CASE)

    // A number, optionally with a unit. Not part of a word (so "B12" or "E330" are skipped).
    private val number = Regex("(?<![\\p{L}\\d.,])(<\\s*)?(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*(kcal|kj|cal|mg|µg|mcg|g|ml|%)?", RegexOption.IGNORE_CASE)

    private data class Num(val v: Double, val unit: String?)

    /** Joins value lines column by column (lines that overlap horizontally), each column top to bottom. */
    private fun joinColumns(values: List<OcrLine>): String {
        val columns = mutableListOf<MutableList<OcrLine>>()
        values.sortedBy { it.left }.forEach { v ->
            val col = columns.firstOrNull { c -> v.left <= c.maxOf { it.right } && v.right >= c.minOf { it.left } }
            if (col != null) col += v else columns += mutableListOf(v)
        }
        return columns.sortedBy { c -> c.minOf { it.left } }.joinToString("  ") { c -> c.sortedBy { it.top }.joinToString(" ") { it.text } }
    }

    /**
     * Rebuilds the table's rows, top to bottom. Each label in the left part of the table
     * ("Energy", "Fat") owns the band from halfway to the label above it to halfway to the
     * one below, and every value inside that band belongs to it. This keeps wrapped cells
     * ("837 kJ /" over "201 kcal") with their row.
     */
    private fun rows(lines: List<OcrLine>): List<String> {
        val all = lines.filter { it.text.isNotBlank() }
        if (all.isEmpty()) return emptyList()
        val minLeft = all.minOf { it.left }
        val cut = minLeft + (all.maxOf { it.right } - minLeft) * 0.35
        val isLabel = all.map { it.left <= cut && it.text.count { ch -> ch.isLetter() } >= 3 }
        val labels = all.indices.filter { isLabel[it] }.sortedBy { all[it].centerY }
        if (labels.isEmpty()) return listOf(joinColumns(all))
        val bounds = labels.zipWithNext { a, b -> (all[a].centerY + all[b].centerY) / 2 }
        val bands = labels.map { mutableListOf<OcrLine>() }
        all.indices.filter { !isLabel[it] }.forEach { i ->
            val band = bounds.indexOfFirst { all[i].centerY < it }.let { if (it < 0) labels.size - 1 else it }
            bands[band] += all[i]
        }
        return labels.mapIndexed { n, i -> (all[i].text + "  " + joinColumns(bands[n])).trim() }
    }

    /** Fixes the usual OCR slips inside numbers: O for 0, l or I for 1. */
    private fun fixDigits(s: String): String = s.split(' ').joinToString(" ") { tok ->
        if (tok.any { it.isDigit() } && tok.matches(Regex("[<\\dOoIl.,]+(kcal|kj|g|mg|ml|%)?", RegexOption.IGNORE_CASE))) {
            val unitStart = tok.indexOfFirst { it.isLetter() && it !in "OoIl" }.let { if (it < 0) tok.length else it }
            tok.substring(0, unitStart).replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1') + tok.substring(unitStart)
        } else tok
    }

    private fun toDouble(raw: String, unit: String?): Double? {
        // "1,650 kJ" is a thousands separator; "10,5 g" is a decimal comma.
        val thousands = Regex("^\\d{1,3},\\d{3}$").matches(raw) && unit?.lowercase() == "kj"
        return (if (thousands) raw.replace(",", "") else raw.replace(',', '.')).toDoubleOrNull()
    }

    private fun numbers(text: String): List<Num> = number.findAll(fixDigits(text)).mapNotNull { m ->
        val unit = m.groupValues[3].lowercase().ifEmpty { null }
        toDouble(m.groupValues[2], unit)?.let { Num(it, unit) }
    }.toList()

    private fun has(text: String, words: List<String>) = words.any { text.contains(it) }

    /** Macro values in a row: grams or bare numbers, skipping %RI, mg and µg. */
    private fun gramValues(text: String) = numbers(text).filter { it.unit == null || it.unit == "g" }.map { it.v }

    private fun kcalValues(rowTexts: List<String>): Pair<List<Double>, Boolean> {
        val kcal = mutableListOf<Double>()
        val kj = mutableListOf<Double>()
        rowTexts.forEach { t ->
            val label = t.lowercase()
            val nums = numbers(t).filter { it.unit != "%" && it.unit != "mg" && it.unit != "g" }
            val bare = mutableListOf<Double>()
            nums.forEach { n ->
                when (n.unit) {
                    "kcal", "cal" -> kcal += n.v
                    "kj" -> kj += n.v
                    else -> bare += n.v
                }
            }
            if (bare.isEmpty()) return@forEach
            // Bare numbers: pairs like "1650 / 395" are kJ then kcal.
            var i = 0
            while (i < bare.size) {
                val a = bare[i]
                val b = bare.getOrNull(i + 1)
                if (b != null && b > 0 && abs(a / KJ_PER_KCAL - b) / b < 0.08) {
                    kj += a; kcal += b; i += 2; continue
                }
                when {
                    label.contains("kcal") || label.contains("calorie") -> kcal += a
                    label.contains("kj") -> kj += a
                    else -> kcal += a
                }
                i++
            }
        }
        return if (kcal.isNotEmpty()) kcal to false else kj.map { it / KJ_PER_KCAL } to kj.isNotEmpty()
    }

    // OCR often reads a "g" unit as "9" ("3.3 9", "(30 9)") or "q". Only within one cell:
    // columns are joined with two spaces, so "12  9" (two values) is left alone.
    private val gAsNine = Regex("(\\d) 9(\\)?)(?=\\s{2}|\\s*$)")
    private val gAsQ = Regex("(\\d)\\s?q\\b")
    private fun cleanRow(s: String) = s.replace(gAsNine, "$1 g$2").replace(gAsQ, "$1 g")
    private val parenAmount = Regex("\\((\\d{1,4})")

    fun parse(lines: List<OcrLine>): LabelResult {
        val rows = rows(lines).map(::cleanRow)
        val lower = rows.map { it.lowercase() }

        val energyRows = rows.filterIndexed { i, _ -> has(lower[i], energyWords) && !has(lower[i], energyExclude) && numbers(rows[i]).isNotEmpty() }
        val (kcal, fromKj) = kcalValues(energyRows)

        fun firstRow(words: List<String>, exclude: List<String>): List<Double> {
            val i = lower.indices.firstOrNull { has(lower[it], words) && !has(lower[it], exclude) && gramValues(rows[it]).isNotEmpty() }
            return if (i == null) emptyList() else gramValues(rows[i])
        }
        // Words like "100 g" in a header row must not be read as a fat or carb value.
        val protein = firstRow(proteinWords, emptyList())
        val fat = firstRow(fatWords, fatExclude)
        val carbs = firstRow(carbWords, carbExclude)

        // Serving size: "Serving size 1 cup (228g)", "Per serving (30 g)", "Portion 30 g".
        var servingG = lower.indices.firstNotNullOfOrNull { i ->
            if (!has(lower[i], servingWords)) null
            else numbers(rows[i]).firstOrNull { (it.unit == "g" || it.unit == "ml") && it.v != 100.0 && it.v > 0 }?.v
                // "Per serving (30" with the unit lost: a number in brackets is the weight.
                ?: parenAmount.find(rows[i].substring(servingWords.mapNotNull { w -> lower[i].indexOf(w).takeIf { it >= 0 } }.min()))
                    ?.groupValues?.get(1)?.toDouble()?.takeIf { it > 0 && it != 100.0 }
        }

        // Column order from a header such as "Per 100 g | Per serving (30 g)" or "100 g | 30 g".
        val header = lower.firstOrNull { per100.containsMatchIn(it) }
        val bases: List<Basis> = when {
            header != null -> {
                val marks = mutableListOf<Pair<Int, Basis>>()
                headerAmount.findAll(header).forEach { m ->
                    val v = m.groupValues[1].toDouble()
                    marks += m.range.first to if (v == 100.0) Basis.PER_100 else Basis.SERVING
                    if (v != 100.0 && servingG == null) servingG = v
                }
                servingWords.forEach { w -> header.indexOf(w).takeIf { it >= 0 }?.let { marks += it to Basis.SERVING } }
                marks.sortedBy { it.first }.map { it.second }.fold(listOf<Basis>()) { acc, b -> if (acc.lastOrNull() == b) acc else acc + b }
            }
            servingG != null || lower.any { it.contains("calories") } -> listOf(Basis.SERVING)
            else -> listOf(Basis.UNKNOWN)
        }

        val n = listOf(kcal.size, protein.size, fat.size, carbs.size).maxOrNull()!!.coerceIn(1, 3)
        val columns = (0 until n).map { i ->
            LabelColumn(
                kcal = kcal.getOrNull(i),
                protein = protein.getOrNull(i),
                fat = fat.getOrNull(i),
                carbs = carbs.getOrNull(i),
                basis = bases.getOrElse(i) { Basis.UNKNOWN },
                kcalFromKj = fromKj,
            )
        }.filter { it.found > 0 }
        return LabelResult(columns.ifEmpty { listOf(LabelColumn(null, null, null, null, bases.first())) }, servingG)
    }
}
