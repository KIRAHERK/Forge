package com.forge.app

/** Who made Forge. Shown in Settings → About, the welcome screen and the Updates screen. */
object Credits {
    const val AUTHOR = "Hussein Al Herk"
    const val LINE = "Designed and developed by $AUTHOR"

    /** Label to URL. Links left blank are not shown. */
    val links: List<Pair<String, String>> = listOf(
        "GitHub" to "https://github.com/KIRAHERK",
        "LinkedIn" to "https://www.linkedin.com/in/husseinalherk",
        "Portfolio" to "https://kiraherk.github.io",
    ).filter { it.second.isNotBlank() }
}
