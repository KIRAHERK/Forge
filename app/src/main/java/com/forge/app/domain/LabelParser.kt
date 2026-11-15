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
    /**
     * Whether 4 × protein + 4 × carbs + 9 × fat comes close to the calories: true, false,
     * or null when a value is missing and it can't be checked.
     */
    val addsUp: Boolean? = null,
    /** Digits the parser corrected because the values didn't add up, e.g. "protein 88 → 8.8". */
    val fixes: List<String> = emptyList(),
) {
    val found get() = listOf(kcal, protein, fat, carbs).count { it != null }
}

data class LabelResult(
    val columns: List<LabelColumn>,
    val servingG: Double?,
    /** The table is per 100 ml or a serving in ml: a drink. */
    val liquid: Boolean = false,
) {
    val best: LabelColumn? get() = columns.firstOrNull()
    val empty get() = columns.all { it.found == 0 }
}

/**
 * Reads calories, protein, fat and carbohydrate from the text of a nutrition table
 * (English, plus common French/German/Spanish/Italian words). It only reads what is
 * printed: anything it cannot find stays null for the user to type in.
 *
 * How it works:
 * 1. Each OCR line is cut into pieces: words, and numbers with their unit, each with its own x position.
 * 2. The table's tilt is measured from row names and the numbers beside them, and taken out.
 * 3. Rows: every row name ("Energy", "of which sugars", "Per 100 g") owns the numbers level with it.
 * 4. Columns: numbers in nutrient rows are grouped by position. A column's heading decides what it is:
 *    per 100 g/ml, per serving, % daily intake (dropped) or "with milk" (dropped when there are others).
 * 5. Each column's four values are checked against each other (4 kcal per gram of protein and carbs,
 *    9 per gram of fat); a value that only adds up with a lost decimal point or a "g" read as "9"
 *    is corrected and reported.
 */
object LabelParser {
    private const val KJ_PER_KCAL = 4.184

    /** Tests set this to see how a label was taken apart. */
    var debug: ((String) -> Unit)? = null

    private val energyWords = listOf("energy", "ener", "energie", "énergie", "energía", "energia", "calorie", "kcal", "kj", "brennwert", "valeur énergétique", "(cal)")
    private val proteinWords = listOf("protein", "protéine", "proteine", "eiweiß", "eiweiss", "proteína", "proteina")
    private val fatWords = listOf("fat", "lipid", "matières grasses", "matieres grasses", "fett", "grasas", "grassi")
    private val energyExclude = listOf("from fat", "diet", "daily", "a day", "advice", "based on")
    private val fatExclude = listOf("satur", "trans", "mono", "poly", "of which", "dont", "davon", "di cui", "de las cuales", "sat.", "calorie", "omega", "acids")
    private val carbWords = listOf("carbohydrate", "carbs", "glucide", "kohlenhydrat", "hidratos", "carboidrati", "carbohidrato")
    private val carbExclude = listOf("sugar", "sucre", "zucker", "zucchero", "azúcar", "of which", "dont", "davon", "fibre", "fiber", "polyol", "starch")
    /** Rows that belong to a nutrition table, even when they aren't one of the four values. */
    private val tableWords = energyWords + proteinWords + fatWords + carbWords +
        listOf("sugar", "sodium", "salt", "fibre", "fiber", "satur", "trans", "cholesterol", "calcium", "potassium", "iron", "gluten", "sel", "sucre", "zucker", "salz", "ballast")
    private val servingWords = listOf("serving", "serve", "portion", "per pack", "porción", "porzione")
    private val percentWords = listOf("%", "daily", "intake", "rdi", "gda", "% di", "%di", "di*", "value*", "ri*", "dv")
    private val preparedWords = listOf("milk", "with", "prepared", "zubereitet", "préparé", "prepare", "reconstitut")

    private val per100 = Regex("(?:per|pour|pro|por|/)?\\s*100\\s*(?:g|ml|gr|grams?|gms|m1)\\b", RegexOption.IGNORE_CASE)
    private val mlAmount = Regex("(?<![\\d.,])\\d{1,4}\\s*ml\\b", RegexOption.IGNORE_CASE)
    private val amount = Regex("(?<![\\d.,])(\\d{1,4}(?:[.,]\\d{1,2})?)\\s*(?:g|ml|gr|grams?|gms|gm)\\b", RegexOption.IGNORE_CASE)
    private val parenAmount = Regex("\\((\\d{1,4}(?:[.,]\\d)?)")

    // A number, optionally with a unit. Not part of a word (so "B12" or "E330" are skipped).
    private val number = Regex(
        "(?<![\\p{L}\\d.,])(<\\s*)?(\\d{1,4}(?:[.,]\\d{1,3})?)(?:\\s*\\(?(kcal|kj|cal|mg|µg|mcg|grams|gram|gms|gm|gr|g|ml|%)\\)?(?![\\p{L}]))?",
        RegexOption.IGNORE_CASE,
    )
    private val unitOnly = Regex("^[(\\[]?(kcal|kj|cal|g|mg|ml|gms|gr)[)\\]]?[/.,:]?$", RegexOption.IGNORE_CASE)

    // ------------------------------------------------------------------ cleaning

    /** Fixes the usual OCR slips inside numbers: O for 0, l or I for 1. */
    private fun fixDigits(s: String): String = s.split(' ').joinToString(" ") { tok ->
        if (tok.matches(Regex("[Oo](g|mg|%)[,.)]?"))) "0" + tok.substring(1)
        else if (tok.any { it.isDigit() } && tok.matches(Regex("[<\\dOoIl.,]+(kcal|kj|g|mg|ml|%)?", RegexOption.IGNORE_CASE))) {
            val unitStart = tok.indexOfFirst { it.isLetter() && it !in "OoIl" }.let { if (it < 0) tok.length else it }
            tok.substring(0, unitStart).replace('O', '0').replace('o', '0').replace('I', '1').replace('l', '1') + tok.substring(unitStart)
        } else tok
    }

    // OCR reads a "g" unit as "9" ("3.3 9", "(30 9)") or "q", and "kJ" as "k)".
    private val gAsNine = Regex("(\\d) 9(\\)?)(?=\\s*$)")
    private val gAsQ = Regex("(\\d)\\s?[qQ]\\b")
    private val kjSlip = Regex("(\\d)\\s?[kK][)\\]|1lI](?![\\p{L}])")
    private val spacedHundred = Regex("\\b1 ?0 ?0 ?(g|ml|gr)\\b", RegexOption.IGNORE_CASE)
    private val nil = Regex("\\b(nil|trace|not detected|nd)\\b", RegexOption.IGNORE_CASE)

    private fun clean(s: String) = fixDigits(s)
        .replace(kjSlip, "$1 kJ")
        .replace(spacedHundred, "100 $1")
        .replace(gAsNine, "$1 g$2")
        .replace(gAsQ, "$1 g")
        .replace(nil, "0")

    // ------------------------------------------------------------------ pieces

    private data class Num(val v: Double, val unit: String?, val raw: String)

    /** A word run or one number, with its share of the line's box. */
    private class Piece(val text: String, var num: Num?, val x0: Double, val x1: Double, val top: Int, val bottom: Int, val line: Int) {
        val h get() = (bottom - top).coerceAtLeast(1)
        val cx get() = (x0 + x1) / 2
        val cy get() = (top + bottom) / 2.0
        var y = 0.0 // centre with the table's tilt taken out
        var unit: String? = num?.unit
        val lower = text.lowercase()
        val letters = text.count { it.isLetter() }
    }

    private fun toDouble(raw: String, unit: String?): Double? {
        // "1,650 kJ" is a thousands separator; "10,5 g" is a decimal comma.
        val thousands = Regex("^\\d{1,3},\\d{3}$").matches(raw) && unit?.lowercase() in listOf("kj", "kcal", null)
        return (if (thousands) raw.replace(",", "") else raw.replace(',', '.')).toDoubleOrNull()
    }

    private fun normUnit(u: String?): String? = when (val l = u?.lowercase()) {
        null -> null
        "gram", "grams", "gms", "gm", "gr" -> "g"
        "cal" -> "kcal"
        else -> l
    }

    private fun pieces(lines: List<OcrLine>): List<Piece> {
        val out = mutableListOf<Piece>()
        lines.forEachIndexed { li, line ->
            val text = clean(line.text)
            if (text.isBlank()) return@forEachIndexed
            val width = (line.right - line.left).toDouble()
            fun x(i: Int) = line.left + width * i / text.length.coerceAtLeast(1)
            var last = 0
            fun word(a: Int, b: Int) {
                val w = text.substring(a, b)
                val t = w.trim().trim('|', '-', ':', '·', '.', ',', '/', '_', '—')
                if (t.isNotEmpty()) {
                    val s = a + w.indexOf(t.first())
                    out += Piece(t, null, x(s), x(s + t.length), line.top, line.bottom, li)
                }
            }
            number.findAll(text).forEach { m ->
                word(last, m.range.first)
                val unit = normUnit(m.groupValues[3].ifEmpty { null })
                toDouble(m.groupValues[2], unit)?.let { v ->
                    out += Piece(m.value.trim(), Num(v, unit, m.groupValues[2]), x(m.range.first), x(m.range.last + 1), line.top, line.bottom, li)
                }
                last = m.range.last + 1
            }
            word(last, text.length)
        }
        return out
    }

    private fun has(text: String, words: List<String>) = words.any { text.contains(it) }

    // ------------------------------------------------------------------ table

    private class Row(val label: Piece) {
        val nums = mutableListOf<Piece>()
        val lower get() = label.lower
    }

    private class Column(val cells: MutableList<Piece>) {
        val x0 get() = cells.minOf { it.x0 }
        val x1 get() = cells.maxOf { it.x1 }
        var basis = Basis.UNKNOWN
        var prepared = false
        var inline = false
    }

    private enum class Target { KCAL, PROTEIN, FAT, CARBS }

    /** A value and the printed number it came from. */
    private class Pick(val v: Double, val piece: Piece) {
        val reading get() = Reading(v, piece.num?.raw ?: v.toString(), piece.unit != null)
    }

    private data class Reading(val v: Double, val raw: String, val hasUnit: Boolean)

    private fun Row.isTarget(t: Target): Boolean = when (t) {
        Target.KCAL -> has(lower, energyWords) && !has(lower, energyExclude)
        Target.PROTEIN -> has(lower, proteinWords)
        Target.FAT -> has(lower, fatWords) && !has(lower, fatExclude)
        Target.CARBS -> has(lower, carbWords) && !has(lower, carbExclude)
    }

    fun parse(lines: List<OcrLine>): LabelResult {
        // A line naming two or more of the four values is a sentence ("Energy 1850 kJ, fat 22 g, ..."),
        // read by the text pass below rather than as table rows.
        fun named(t: String) = listOf(energyWords, proteinWords, fatWords, carbWords).count { ws -> ws.any { w -> t.lowercase().contains(w) } }
        val all = pieces(lines.filter { it.text.isNotBlank() && named(it.text) < 2 })
        val fullText = lines.joinToString("\n") { it.text }
        val fullLower = fullText.lowercase()
        val liquid = lines.any { mlAmount.containsMatchIn(it.text) }
        val us0 = fullLower.contains("daily value") || fullLower.contains("amount per serving")
        fun textOnly() = LabelResult(
            listOf(fillFromText(null, fullText, us0, per100.containsMatchIn(fullLower), Regex("serving\\s*size").containsMatchIn(fullLower)))
                .filter { it.found > 0 }.ifEmpty { listOf(LabelColumn(null, null, null, null, Basis.UNKNOWN)) },
            null, liquid,
        )
        if (all.isEmpty()) return textOnly()

        val nums = all.filter { it.num != null }
        val words = all.filter { it.num == null }

        // A unit printed under its number ("251 kJ / 60" over "kcal") belongs to that number.
        words.filter { unitOnly.matches(it.text) }.forEach { u ->
            val above = nums.filter { it.unit == null && it.cy < u.cy && u.cy - it.cy < 1.8 * u.h && it.x1 >= u.x0 - u.h && it.x0 <= u.x1 }
                .minByOrNull { u.cy - it.cy }
            if (above != null) above.unit = normUnit(unitOnly.find(u.text)!!.groupValues[1])
        }

        // Row names: words with letters, or a unit word that starts a row ("(Cal)  110  355").
        val labels = words.filter { w ->
            if (unitOnly.matches(w.text)) nums.any { abs(it.cy - w.cy) < 0.6 * w.h && it.x0 > w.x1 } && nums.none { abs(it.cy - w.cy) < 0.6 * w.h && it.x1 < w.x0 }
            else w.letters >= 3 || w.lower in listOf("fat", "fett")
        }

        // Tilt: a row name and the nearest number to its right are on the same printed row.
        val slopes = labels.mapNotNull { l ->
            nums.filter { it.x0 > l.x1 && abs(it.cy - l.cy) < 0.7 * l.h }.minByOrNull { it.x0 }
                ?.let { n -> (n.cy - l.cy) / (n.cx - l.cx).coerceAtLeast(1.0) }
        }.sorted()
        val slope = if (slopes.size >= 3) slopes[slopes.size / 2].coerceIn(-0.2, 0.2) else 0.0
        all.forEach { it.y = it.cy - slope * it.cx }

        // Rows: each number goes to the row name level with it (a nutrient name wins over noise
        // printed on the same row), else to the nearest row name above or below.
        val rows = labels.sortedBy { it.y }.map { Row(it) }
        if (rows.isEmpty()) return textOnly()
        nums.forEach { n ->
            // Row names sit to the left of their numbers; one on the right only counts when it's a
            // nutrient (a table printed both ways), never other text beside the table.
            val left = rows.filter { it.label.x1 <= n.x0 + it.label.h }
            val level = rows.filter { abs(it.label.y - n.y) < 0.6 * it.label.h && (it in left || has(it.lower, tableWords)) }
            val pick = level.filter { has(it.lower, tableWords) }.minByOrNull { abs(it.label.y - n.y) }
                ?: level.minByOrNull { abs(it.label.y - n.y) }
                ?: left.minByOrNull { abs(it.label.y - n.y) }?.takeIf { abs(it.label.y - n.y) < 1.6 * it.label.h }
            pick?.nums?.add(n)
        }

        val tableRows = rows.filter { has(it.lower, tableWords) && it.nums.isNotEmpty() }
        debug?.let { d ->
            d("slope=$slope")
            rows.forEach { r -> d("ROW ${r.label.text} @${r.label.y.toInt()} : " + r.nums.joinToString("  ") { "${it.num!!.v}${it.unit ?: ""}[${it.x0.toInt()}-${it.x1.toInt()}]" }) }
        }
        // Percentages (% daily value) are never one of the four values.
        val tableNums = tableRows.flatMap { it.nums }.filter { it.unit != "%" }
        val singleOnLine = tableRows.flatMap { r ->
            // "Total Fat 12g  15%": one value printed right after its name, US style.
            r.nums.filter { it.line == r.label.line && it.x0 >= r.label.x1 - 1 && it.unit != "%" }
                .takeIf { it.size == 1 }.orEmpty()
        }.toSet()

        // Columns: numbers that overlap horizontally, numbers printed inline after their name kept apart.
        val columns = mutableListOf<Column>()
        tableNums.filter { it !in singleOnLine }.sortedBy { it.x0 }.forEach { n ->
            val slack = 0.3 * n.h
            val col = columns.firstOrNull { c -> n.x0 <= c.x1 + slack && n.x1 >= c.x0 - slack }
            if (col != null) col.cells += n else columns += Column(mutableListOf(n))
        }
        if (singleOnLine.isNotEmpty()) columns.add(0, Column(singleOnLine.toMutableList()).also { it.inline = true })

        // "5.6g" read as "5.69": in a column where values carry a "g" or have one decimal,
        // a unitless value with two decimals ending in 9 is that value plus a misread "g".
        val nineSlip = Regex("^\\d{1,3}[.,]\\d9$")
        columns.forEach { c ->
            val slips = c.cells.filter { it.unit == null && nineSlip.matches(it.num!!.raw) }
            val clean = c.cells.count { it.unit == "g" && !nineSlip.matches(it.num!!.raw) }
            if (slips.isNotEmpty() && (clean >= 1 || slips.size >= 2)) slips.forEach { p ->
                val raw = p.num!!.raw.dropLast(1)
                p.num = Num(toDouble(raw, "g")!!, "g", raw)
                p.unit = "g"
            }
        }

        // What each column is, from its heading (anything printed above the first nutrient row).
        val tableTop = tableRows.minOfOrNull { it.label.y } ?: 0.0
        val lineH = tableRows.map { it.label.h }.sorted().let { if (it.isEmpty()) 30 else it[it.size / 2] }
        // Headings: "Per 100 g", "Per serving (30 g)", "%DI", each pointing at the column right under it
        // (the nearest column, as long as that column has no nearer heading of its own).
        val headingZone = all.filter { p -> p.y < tableTop - 0.4 * lineH && p.y > tableTop - 9 * lineH }
        data class Mark(val kind: Int, val cx: Double) // 0 per 100, 1 serving, 2 percent
        val marks = headingZone.mapNotNull { p ->
            when {
                p.num?.v == 100.0 && (p.unit == "g" || p.unit == "ml") || per100.containsMatchIn(p.lower) -> Mark(0, p.cx)
                percentWords.any { p.lower.contains(it) } -> Mark(2, p.cx)
                has(p.lower, servingWords) || (p.num != null && p.unit in listOf("g", "ml")) -> Mark(1, p.cx)
                else -> null
            }
        }
        val mids = columns.map { (it.x0 + it.x1) / 2 }
        columns.forEachIndexed { i, c ->
            val mark = marks.minByOrNull { abs(it.cx - mids[i]) }
            val mutual = mark != null && mids.indices.minByOrNull { abs(mark.cx - mids[it]) } == i
            val pct = c.cells.count { it.unit == "%" } * 2 > c.cells.size
            c.prepared = !c.inline && headingZone.filter { p -> has(p.lower, preparedWords) }.any { abs(it.cx - mids[i]) < (c.x1 - c.x0) / 2 + 2 * lineH }
            when {
                pct || (!c.inline && mutual && mark!!.kind == 2) -> c.cells.clear()
                mutual && mark!!.kind == 0 -> c.basis = Basis.PER_100
                mutual && mark!!.kind == 1 -> c.basis = Basis.SERVING
                else -> c.basis = Basis.UNKNOWN
            }
        }
        columns.removeAll { it.cells.isEmpty() }

        // Columns without a heading: the title decides ("Nutrition facts per 100 ml"), or the US layout.
        val us = fullLower.contains("daily value") || fullLower.contains("amount per serving") || Regex("\\bcalories\\b").containsMatchIn(fullLower) && !fullLower.contains("energy")
        val titleSaysPer100 = per100.containsMatchIn(fullLower)
        val servingSize = Regex("serving\\s*size|portion").containsMatchIn(fullLower)
        columns.filter { it.basis == Basis.UNKNOWN }.forEach { c ->
            c.basis = when {
                us -> Basis.SERVING
                columns.size == 1 && titleSaysPer100 -> Basis.PER_100
                columns.size == 1 && servingSize -> Basis.SERVING
                else -> Basis.UNKNOWN
            }
        }

        // Values for each column.
        fun macro(c: Column, t: Target): Pick? {
            val candidates = tableRows.filter { it.isTarget(t) }
            val ordered = if (t == Target.FAT) candidates.sortedByDescending { it.lower.contains("total") } else candidates
            return ordered.firstNotNullOfOrNull { r ->
                r.nums.filter { it in c.cells }.sortedBy { it.x0 }
                    .firstOrNull { it.unit == null || it.unit == "g" }?.let { Pick(it.num!!.v, it) }
            }
        }

        fun energy(c: Column): Pair<Pick?, Boolean> {
            val kcal = mutableListOf<Pick>()
            val kj = mutableListOf<Pick>()
            tableRows.filter { it.isTarget(Target.KCAL) }.forEach { r ->
                val cells = r.nums.filter { it in c.cells && it.unit != "%" && it.unit != "mg" && it.unit != "g" }.sortedWith(compareBy({ it.y }, { it.x0 }))
                var i = 0
                while (i < cells.size) {
                    val a = cells[i]
                    val b = cells.getOrNull(i + 1)
                    val av = a.num!!.v
                    when (a.unit) {
                        "kcal" -> kcal += Pick(av, a)
                        "kj" -> kj += Pick(av, a)
                        else -> {
                            // Bare numbers: pairs like "1650 / 395" are kJ then kcal.
                            val bv = b?.num?.v
                            if (b != null && b.unit == null && bv != null && bv > 0 && abs(av / KJ_PER_KCAL - bv) / bv < 0.08) {
                                kj += Pick(av, a); kcal += Pick(bv, b); i += 2; continue
                            }
                            val label = r.lower
                            when {
                                label.contains("kj") && !label.contains("kcal") -> kj += Pick(av, a)
                                label.contains("kcal") || label.contains("cal") -> kcal += Pick(av, a)
                                av > 950 -> kj += Pick(av, a)
                                else -> kcal += Pick(av, a)
                            }
                        }
                    }
                    i++
                }
            }
            kcal.firstOrNull()?.let { return it to false }
            return kj.firstOrNull()?.let { Pick(it.v / KJ_PER_KCAL, it.piece) to true } ?: (null to false)
        }

        debug?.let { d -> columns.forEach { c -> d("COL ${c.x0.toInt()}-${c.x1.toInt()} ${c.basis} prep=${c.prepared} inline=${c.inline}: " + c.cells.joinToString(" ") { "${it.num!!.v}${it.unit ?: ""}" }) } }
        var built = columns.map { c ->
            val (e, fromKj) = energy(c)
            Built(c, e?.v, e?.piece, macro(c, Target.PROTEIN), macro(c, Target.FAT), macro(c, Target.CARBS), fromKj)
        }.filter { it.found > 0 }

        // Pieces of one column split by OCR (or a US "Calories 220" printed far right): merge
        // columns that hold different values and don't disagree about what they are.
        var merged = true
        while (merged) {
            merged = false
            loop@ for (i in built.indices) for (j in built.indices) {
                if (i >= j) continue
                val a = built[i]; val b = built[j]
                val basisOk = a.col.basis == b.col.basis || a.col.basis == Basis.UNKNOWN || b.col.basis == Basis.UNKNOWN
                if (basisOk && a.col.prepared == b.col.prepared && a.filled.intersect(b.filled).isEmpty()) {
                    val m = a.merge(b)
                    built = built.toMutableList().also { it[i] = m; it.removeAt(j) }
                    merged = true
                    break@loop
                }
            }
        }

        // "With milk" columns only when there's nothing else.
        if (built.any { !it.col.prepared && it.found >= 2 }) built = built.filter { !it.col.prepared }
        // Per 100 first: it gives every portion size. Then per serving, then the rest.
        built = built.sortedBy { when (it.col.basis) { Basis.PER_100 -> 0; Basis.SERVING -> 1; Basis.UNKNOWN -> 2 } }

        // Serving size: "Serving size 1 cup (228g)", "Per serving (30 g)", "SERVING SIZE: 100g".
        val servingG = lines.firstNotNullOfOrNull { l ->
            val t = clean(l.text).lowercase()
            if (!has(t, servingWords) || t.contains("per pack") || t.contains("servings per")) return@firstNotNullOfOrNull null
            val sizeLine = t.contains("size")
            amount.findAll(t).mapNotNull { toDouble(it.groupValues[1], "g") }.firstOrNull { it > 0 && (it != 100.0 || sizeLine) }
                ?: parenAmount.find(t)?.groupValues?.get(1)?.let { toDouble(it, "g") }?.takeIf { it > 0 && (it != 100.0 || sizeLine) }
        } ?: columns.firstOrNull { it.basis == Basis.SERVING }?.let { c ->
            // "Per 35g" over the column.
            val heading = all.filter { p -> p.y < tableTop - 0.4 * lineH && p.y > tableTop - 9 * lineH && p.x1 >= c.x0 - lineH && p.x0 <= c.x1 + lineH && p.num != null }
            heading.firstOrNull { it.unit == "g" || it.unit == "ml" }?.num?.v?.takeIf { it != 100.0 && it > 0 }
        }

        var result = built.map { it.toColumn(servingG) }

        // Two columns and a known serving size: per-serving values are the per-100 values × size / 100.
        // That settles which is which better than a heading OCR may have misplaced.
        if (result.size == 2 && servingG != null && servingG != 100.0) {
            val (a, b) = result
            fun ratio(x: LabelColumn, y: LabelColumn) = listOf(x.kcal to y.kcal, x.protein to y.protein, x.fat to y.fat, x.carbs to y.carbs)
                .mapNotNull { (u, v) -> if (u != null && v != null && u > 0.5) v / u else null }
            fun fits(x: LabelColumn, y: LabelColumn) = ratio(x, y).let { r -> r.size >= 2 && r.count { abs(it - servingG / 100) < 0.12 * servingG / 100 + 0.02 } * 2 > r.size }
            result = when {
                fits(a, b) -> listOf(a.copy(basis = Basis.PER_100), b.copy(basis = Basis.SERVING))
                fits(b, a) -> listOf(b.copy(basis = Basis.PER_100), a.copy(basis = Basis.SERVING))
                else -> result
            }
        }
        if (result.isEmpty() || result.size == 1) result = listOf(fillFromText(result.firstOrNull(), fullText, us, titleSaysPer100, servingSize))
        result = result.filter { it.found > 0 }.ifEmpty { listOf(LabelColumn(null, null, null, null, Basis.UNKNOWN)) }
        if (lines.any { named(it.text) >= 2 }) {
            val sentence = textOnly()
            if (sentence.columns.first().found > result.maxOf { it.found }) return sentence.copy(servingG = servingG)
        }
        return LabelResult(result, servingG, liquid)
    }

    private class Built(
        val col: Column,
        val kcal: Double?, val kcalPiece: Piece?,
        val protein: Pick?, val fat: Pick?, val carbs: Pick?,
        val fromKj: Boolean,
    ) {
        val filled get() = buildSet {
            if (kcal != null) add(0); if (protein != null) add(1); if (fat != null) add(2); if (carbs != null) add(3)
        }
        val found get() = filled.size

        fun merge(o: Built): Built {
            val c = Column((col.cells + o.col.cells).toMutableList()).also {
                it.basis = if (col.basis != Basis.UNKNOWN) col.basis else o.col.basis
                it.prepared = col.prepared
                it.inline = col.inline || o.col.inline
            }
            return Built(
                c, kcal ?: o.kcal, kcalPiece ?: o.kcalPiece,
                protein ?: o.protein, fat ?: o.fat, carbs ?: o.carbs,
                if (kcal != null) fromKj else o.fromKj,
            )
        }

        fun toColumn(servingG: Double?): LabelColumn =
            check(kcal, protein?.reading, fat?.reading, carbs?.reading, col.basis, fromKj, servingG)
    }

    // ------------------------------------------------------------------ cross-check

    /** Other ways to read a printed number: a "g" read as a trailing 9, a lost decimal point. */
    private fun readings(r: Reading): List<Pair<Double, Double>> {
        val out = mutableListOf(r.v to 0.0)
        val plain = r.raw.replace(',', '.')
        if (!r.hasUnit && plain.length >= 2 && plain.endsWith("9")) {
            plain.dropLast(1).trimEnd('.').toDoubleOrNull()?.let { s ->
                out += s to 0.03
                if (!plain.contains('.') && s >= 10) out += s / 10 to 0.06
            }
        }
        if (!plain.contains('.') && r.v >= 10) {
            out += r.v / 10 to 0.06
            if (r.v >= 100) out += r.v / 100 to 0.09
        }
        return out.distinctBy { it.first }
    }

    private fun check(kcal: Double?, p: Reading?, f: Reading?, c: Reading?, basis: Basis, fromKj: Boolean, servingG: Double?): LabelColumn {
        val limit = when (basis) { Basis.SERVING -> (servingG ?: 1000.0).coerceAtLeast(1.0); else -> 100.0 }
        val options = listOf(p, f, c).map { it?.let { r -> readings(r) } ?: listOf<Pair<Double, Double>?>(null) }
        var kcalValue = kcal
        if (kcalValue != null && basis != Basis.SERVING && kcalValue > 950 && !fromKj) kcalValue /= KJ_PER_KCAL

        fun error(pv: Double?, fv: Double?, cv: Double?): Double? {
            if (kcalValue == null || pv == null || fv == null || cv == null) return null
            val est = 4 * pv + 4 * cv + 9 * fv
            return abs(est - kcalValue) / maxOf(kcalValue, 40.0)
        }
        fun impossible(vararg vs: Double?) = vs.filterNotNull().let { l -> l.any { it > limit } || (basis != Basis.SERVING && l.sum() > 105) }

        data class Choice(val pv: Double?, val fv: Double?, val cv: Double?, val cost: Double, val err: Double?)
        val choices = options[0].flatMap { po -> options[1].flatMap { fo -> options[2].map { co ->
            val err = error(po?.first, fo?.first, co?.first)
            val cost = (po?.second ?: 0.0) + (fo?.second ?: 0.0) + (co?.second ?: 0.0) +
                if (impossible(po?.first, fo?.first, co?.first)) 1.0 else 0.0
            Choice(po?.first, fo?.first, co?.first, cost, err)
        } } }
        val raw = choices.first()
        val score = { ch: Choice -> (ch.err ?: 0.0) + ch.cost }
        val best = choices.minByOrNull(score)!!
        val use = if (best !== raw && score(best) + 0.05 < score(raw) && (best.err == null || best.err < 0.15)) best else raw
        val fixes = buildList {
            if (use.pv != raw.pv) add("protein ${fmt(raw.pv)} → ${fmt(use.pv)}")
            if (use.fv != raw.fv) add("fat ${fmt(raw.fv)} → ${fmt(use.fv)}")
            if (use.cv != raw.cv) add("carbs ${fmt(raw.cv)} → ${fmt(use.cv)}")
        }
        val addsUp = use.err?.let { it < 0.2 }
        return LabelColumn(kcalValue, use.pv, use.fv, use.cv, basis, fromKj || (kcal != null && kcalValue != kcal), addsUp, fixes)
    }

    private fun fmt(v: Double?) = when {
        v == null -> "-"
        v % 1.0 == 0.0 -> v.toInt().toString()
        else -> v.toString()
    }

    // ------------------------------------------------------------------ text fallback

    /**
     * Labels printed as a sentence ("Energy 1850 kJ / 442 kcal, fat 22 g, carbohydrate 52 g ...") or
     * values the table pass missed: find each name followed closely by a number.
     */
    private fun fillFromText(col: LabelColumn?, text: String, us: Boolean, per100Title: Boolean, servingSize: Boolean): LabelColumn {
        val t = clean(text.replace('\n', ' ')).lowercase()
        fun find(words: List<String>, exclude: List<String>, units: String): Double? {
            words.forEach { w ->
                var i = t.indexOf(w)
                while (i >= 0) {
                    val before = t.substring((i - 18).coerceAtLeast(0), i)
                    val after = t.substring(i, (i + w.length + 30).coerceAtMost(t.length))
                    if (exclude.none { before.endsWith(it) || before.contains(it) || after.startsWith(it) }) {
                        // "Energy 1850 kJ / 442 kcal": a kJ figure may come before the kcal one.
                        val skipKj = if (units.contains("kcal")) "(?:\\d{1,4}(?:[.,]\\d{1,3})?\\s*kj\\s*[/|(,]?\\s*)?" else ""
                        val m = Regex("^[\\p{L} ,.()/:-]{0,24}?$skipKj(<\\s*)?(\\d{1,4}(?:[.,]\\d{1,3})?)\\s*($units)(?![\\p{L}])").find(after.substring(w.length))
                        if (m != null) return toDouble(m.groupValues[2], m.groupValues[3])
                    }
                    i = t.indexOf(w, i + w.length)
                }
            }
            return null
        }
        val kcal = col?.kcal ?: find(listOf("energy", "calories", "énergie", "energie", "energía"), energyExclude, "kcal|cal")
        val kj = if (kcal == null) find(listOf("energy", "énergie", "energie"), energyExclude, "kj") else null
        val p = col?.protein ?: find(proteinWords, emptyList(), "g")
        val f = col?.fat ?: find(fatWords, fatExclude.filter { it != "calorie" } + listOf("saturated ", "trans "), "g")
        val c = col?.carbs ?: find(carbWords, carbExclude, "g")
        val basis = col?.basis?.takeIf { it != Basis.UNKNOWN } ?: when { us -> Basis.SERVING; per100Title -> Basis.PER_100; servingSize -> Basis.SERVING; else -> Basis.UNKNOWN }
        val energy = kcal ?: kj?.div(KJ_PER_KCAL)
        if (col != null && energy == col.kcal && p == col.protein && f == col.fat && c == col.carbs && basis == col.basis) return col
        fun r(v: Double?) = v?.let { Reading(it, it.toString(), true) }
        val base = check(energy, r(p), r(f), r(c), basis, col?.kcalFromKj ?: (kcal == null && kj != null), null)
        return base.copy(fixes = (col?.fixes.orEmpty() + base.fixes).distinct())
    }
}
