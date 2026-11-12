package com.forge.app

/** Who made Forge. Shown in Settings → About, the welcome screen and the Updates screen. */
object Credits {
    const val AUTHOR = "Hussein Al Herk"
    const val LINE = "Designed and developed by $AUTHOR"

    /** The public download page (GitHub Pages from /docs) and the always-newest APK. */
    const val DOWNLOAD_PAGE = "https://kiraherk.github.io/Forge/"
    const val DOWNLOAD_APK = "https://github.com/KIRAHERK/Forge/releases/latest/download/Forge.apk"

    /** Label to URL. Links left blank are not shown. */
    val links: List<Pair<String, String>> = listOf(
        "GitHub" to "https://github.com/KIRAHERK",
        "LinkedIn" to "https://www.linkedin.com/in/husseinalherk",
        "Portfolio" to "https://kiraherk.github.io",
    ).filter { it.second.isNotBlank() }
}
