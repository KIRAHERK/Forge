package com.forge.app

import com.forge.app.i18n.Lang
import com.forge.app.i18n.t
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ArabicTest {
    private val table = Lang.parse(File("src/main/assets/i18n/ar.json").readText())

    @After fun reset() = Lang.useTable(false, emptyMap())

    /** Every literal passed to t("...") in the app has an Arabic line. */
    @Test fun everyKeyIsTranslated() {
        val lit = Regex("""(?<![\w.])t\("((?:[^"\\]|\\.)*)"""")
        val missing = File("src/main/java").walk().filter { it.extension == "kt" }.flatMap { f ->
            lit.findAll(f.readText()).map { m ->
                m.groupValues[1].replace("\${'$'}", "$").replace("\\\"", "\"").replace("\\n", "\n").replace("\\$", "$")
            }.filter { it !in table && it !in IGNORE }.map { "${f.name}: $it" }
        }.toList()
        assertTrue("Missing Arabic:\n" + missing.joinToString("\n"), missing.isEmpty())
    }

    /** Arabic keeps the same %s / %1$s / %d placeholders, or String.format would crash. */
    @Test fun placeholdersMatch() {
        val spec = Regex("""%(?:\d+\$)?[-+ 0,(#]*\d*(?:\.\d+)?[sdf%]""")
        fun norm(s: String) = spec.findAll(s).map { it.value.replace(Regex("""\d+\$"""), "") }.sorted().toList()
        val bad = table.filter { (k, v) -> norm(k) != norm(v) }.keys
        assertTrue("Placeholder mismatch: $bad", bad.isEmpty())
    }

    @Test fun lookupFormatsAndFallsBack() {
        Lang.useTable(true, table)
        assertEquals("السلسلة 9", t("Streak %s", 9))
        assertEquals("Some new text", t("Some new text"))
        assertEquals("الظهر", t("muscle|Back"))
        Lang.useTable(false, table)
        assertEquals("Back", t("muscle|Back"))
        assertEquals("Streak 9", t("Streak %s", 9))
    }

    @Test fun pickingFollowsThePhoneUnlessSet() {
        assertTrue(Lang.resolve(Lang.SYSTEM, "ar"))
        assertTrue(!Lang.resolve(Lang.SYSTEM, "en"))
        assertTrue(Lang.resolve(Lang.AR, "en"))
        assertTrue(!Lang.resolve(Lang.EN, "ar"))
    }

    private companion object {
        val IGNORE = setOf("guideStep", "chkScale")
    }
}
