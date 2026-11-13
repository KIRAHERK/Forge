package com.forge.app.domain

/**
 * The app's main color. Green is the original; the rest are for anyone who'd rather not
 * live in lime. [miss] is the color used for misses and penalties: red everywhere,
 * except when red is the main color, where misses switch to amber so the two never blur.
 */
enum class Accent(val label: String, val argb: Long, val miss: Long = RED_MISS) {
    GREEN("Green", 0xFFCCFF4D),
    RED("Red", 0xFFFF4545, miss = 0xFFFFA43D),
    PURPLE("Purple", 0xFFB57BFF),
    PINK("Pink", 0xFFFF6FC8),
    BLUE("Blue", 0xFF4DA8FF);

    val argbInt: Int get() = argb.toInt()

    companion object {
        const val RED_MISS = 0xFFFF4D3D

        fun of(name: String?): Accent = entries.firstOrNull { it.name == name } ?: GREEN

        /**
         * Last accent read from settings, for code outside the UI (notifications, widget)
         * that has no theme to ask. Updated every time settings are loaded.
         */
        @Volatile var current: Accent = GREEN
    }
}
