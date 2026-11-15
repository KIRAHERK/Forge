package com.forge.app.domain

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Combines the readings of many camera frames. One frame can misread a digit; the same number
 * read again and again is the printed one. A value is locked once it has been read [needed] times
 * and makes up most of the readings of that value.
 */
class LabelVotes(private val needed: Int = 3) {
    enum class Field { KCAL, PROTEIN, FAT, CARBS }

    private val votes = HashMap<Pair<Basis, Field>, HashMap<Double, Int>>()
    private val servings = HashMap<Double, Int>()
    private var liquid = 0

    /** Frames that gave at least one value. */
    var readable = 0
        private set

    private fun key(v: Double, f: Field) = if (f == Field.KCAL) v.roundToInt().toDouble() else (v * 10).roundToInt() / 10.0

    private fun LabelColumn.value(f: Field) = when (f) {
        Field.KCAL -> kcal
        Field.PROTEIN -> protein
        Field.FAT -> fat
        Field.CARBS -> carbs
    }

    fun add(r: LabelResult, weight: Int = 1) {
        if (r.empty) return
        readable += weight
        if (r.liquid) liquid += weight
        r.servingG?.let { servings.merge(it, weight, Int::plus) }
        r.columns.forEach { c ->
            // A column whose values clearly don't add up is a misread frame: it doesn't vote.
            if (c.addsUp == false) return@forEach
            Field.entries.forEach { f ->
                c.value(f)?.let { v -> votes.getOrPut(c.basis to f) { HashMap() }.merge(key(v, f), weight, Int::plus) }
            }
        }
    }

    /** Votes for one value; readings without a known basis count towards the only known basis. */
    private fun tally(basis: Basis, f: Field): Map<Double, Int> {
        val own = votes[basis to f].orEmpty()
        val known = votes.keys.map { it.first }.filter { it != Basis.UNKNOWN }.toSet()
        if (basis == Basis.UNKNOWN || known.size != 1 || basis !in known) return own
        return (own.keys + votes[Basis.UNKNOWN to f].orEmpty().keys).associateWith { v ->
            own[v].orZero() + votes[Basis.UNKNOWN to f]?.get(v).orZero()
        }
    }

    private fun Int?.orZero() = this ?: 0

    fun locked(basis: Basis, f: Field): Double? {
        val t = tally(basis, f)
        val (v, n) = t.maxByOrNull { it.value } ?: return null
        return v.takeIf { n >= needed && n * 10 >= t.values.sum() * 6 }
    }

    /** The most-read value so far, locked or not. */
    fun seen(basis: Basis, f: Field): Double? = tally(basis, f).maxByOrNull { it.value }?.key

    /** Every value of [f] read at least [needed] times, in any column: for re-scanning a single value. */
    fun candidates(f: Field): List<Double> {
        val all = HashMap<Double, Int>()
        Basis.entries.forEach { b -> votes[b to f]?.forEach { (v, n) -> all.merge(v, n, Int::plus) } }
        return all.filter { it.value >= needed }.entries.sortedByDescending { it.value }.map { it.key }
    }

    /** Locked in any column: for re-scanning a single value. */
    fun lockedAny(f: Field, prefer: Basis): Double? =
        locked(prefer, f) ?: Basis.entries.firstNotNullOfOrNull { locked(it, f) }

    private val bases get() = Basis.entries.filter { b -> Field.entries.any { votes[b to it] != null } }

    /** The column shown while scanning: the one with the most locked values, per 100 first. */
    val primary: Basis
        get() = bases.filter { it != Basis.UNKNOWN || bases.size == 1 }
            .maxByOrNull { b -> Field.entries.count { locked(b, it) != null } * 10 + (2 - b.ordinal) } ?: Basis.UNKNOWN

    val complete get() = Field.entries.all { locked(primary, it) != null }

    /** Everything read so far: locked values, else the most-read ones, so the user can check them. */
    fun result(): LabelResult {
        val known = bases.filter { it != Basis.UNKNOWN }
        val shown = if (known.size == 1) known else bases
        val columns = shown.map { b ->
            fun v(f: Field) = locked(b, f) ?: seen(b, f)
            val k = v(Field.KCAL); val p = v(Field.PROTEIN); val fat = v(Field.FAT); val c = v(Field.CARBS)
            val addsUp = if (k != null && p != null && fat != null && c != null && k > 0) abs(4 * p + 4 * c + 9 * fat - k) / maxOf(k, 40.0) < 0.2 else null
            LabelColumn(k, p, fat, c, b, addsUp = addsUp)
        }.filter { it.found > 0 }.sortedBy { it.basis.ordinal }
        val serving = servings.maxByOrNull { it.value }?.key
        return LabelResult(columns.ifEmpty { listOf(LabelColumn(null, null, null, null, Basis.UNKNOWN)) }, serving, liquid * 2 > readable)
    }
}
