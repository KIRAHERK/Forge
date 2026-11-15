package com.forge.app

import com.forge.app.domain.Basis
import com.forge.app.domain.LabelColumn
import com.forge.app.domain.LabelResult
import com.forge.app.domain.LabelVotes
import com.forge.app.domain.LabelVotes.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelVotesTest {
    private fun frame(k: Double?, p: Double?, f: Double?, c: Double?, basis: Basis = Basis.PER_100, servingG: Double? = null) =
        LabelResult(listOf(LabelColumn(k, p, f, c, basis)), servingG)

    @Test fun aValueLocksOnceReadConsistently() {
        val v = LabelVotes(needed = 3)
        v.add(frame(488.0, 5.6, 30.0, 53.3))
        v.add(frame(488.0, 56.0, 30.0, null)) // one frame misreads protein
        assertNull(v.locked(Basis.PER_100, Field.KCAL))
        v.add(frame(488.0, 5.6, 30.0, 53.3))
        assertEquals(488.0, v.locked(Basis.PER_100, Field.KCAL))
        assertFalse(v.complete)
        v.add(frame(488.0, 5.6, 30.0, 53.3))
        assertEquals(5.6, v.locked(Basis.PER_100, Field.PROTEIN))
        assertTrue(v.complete)
        assertEquals(listOf(488.0, 5.6, 30.0, 53.3), v.result().best!!.let { listOf(it.kcal, it.protein, it.fat, it.carbs) })
    }

    @Test fun aValueThatKeepsChangingDoesNotLock() {
        val v = LabelVotes(needed = 2)
        listOf(5.6, 56.0, 5.6, 56.0, 5.0).forEach { v.add(frame(null, it, null, null)) }
        assertNull(v.locked(Basis.PER_100, Field.PROTEIN))
        assertEquals(5.6, v.seen(Basis.PER_100, Field.PROTEIN))
    }

    @Test fun framesWithoutABasisCountTowardsTheKnownOne() {
        val v = LabelVotes(needed = 3)
        v.add(frame(null, 8.0, null, null, Basis.PER_100))
        v.add(frame(null, 8.0, null, null, Basis.UNKNOWN))
        v.add(frame(null, 8.0, null, null, Basis.UNKNOWN))
        assertEquals(8.0, v.locked(Basis.PER_100, Field.PROTEIN))
        assertEquals(Basis.PER_100, v.primary)
    }

    @Test fun valuesThatDontAddUpDontVote() {
        val v = LabelVotes(needed = 1)
        v.add(LabelResult(listOf(LabelColumn(500.0, 1.0, 1.0, 1.0, Basis.PER_100, addsUp = false)), null))
        assertNull(v.seen(Basis.PER_100, Field.KCAL))
    }

    @Test fun twoColumnsAreKeptApart() {
        val v = LabelVotes(needed = 2)
        repeat(2) {
            v.add(LabelResult(listOf(LabelColumn(471.0, 7.9, 20.0, 62.6, Basis.PER_100), LabelColumn(118.0, 2.0, 5.0, 15.6, Basis.SERVING)), 25.0))
        }
        val r = v.result()
        assertEquals(listOf(Basis.PER_100, Basis.SERVING), r.columns.map { it.basis })
        assertEquals(25.0, r.servingG)
        assertEquals(2.0, v.lockedAny(Field.PROTEIN, Basis.SERVING))
    }
}
