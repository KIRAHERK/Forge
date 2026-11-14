package com.forge.app.i18n

import android.content.Context
import android.content.res.Configuration
import java.util.Locale

/**
 * The app's language. English text in the code is the key; Arabic lives in
 * assets/i18n/ar.json as { "English": "عربي" }. Anything without an entry shows in English.
 *
 * The choice is read synchronously at startup (a plain SharedPreferences file) because the
 * activity's layout direction and fonts have to be known before the first frame.
 */
object Lang {
    const val SYSTEM = "system"
    const val EN = "en"
    const val AR = "ar"

    private const val PREFS = "forge_lang"
    private const val KEY = "lang"

    /** Arabic for Syria, but with Western digits (117, not ١١٧). */
    val ARABIC: Locale = Locale.forLanguageTag("ar-SY-u-nu-latn")

    @Volatile var arabic: Boolean = false
        private set

    @Volatile private var table: Map<String, String> = emptyMap()

    val locale: Locale get() = if (arabic) ARABIC else Locale.ENGLISH

    fun pref(ctx: Context): String = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM) ?: SYSTEM

    fun setPref(ctx: Context, value: String) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).commit()
        init(ctx, force = true)
    }

    fun resolve(pref: String, systemLanguage: String = Locale.getDefault().language): Boolean = when (pref) {
        AR -> true
        EN -> false
        else -> systemLanguage == "ar"
    }

    private var loaded = false

    fun init(ctx: Context, force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        val system = ctx.applicationContext.resources.configuration.locales.get(0)?.language ?: Locale.getDefault().language
        arabic = resolve(pref(ctx), system)
        if (arabic && table.isEmpty()) {
            table = runCatching {
                ctx.assets.open("i18n/ar.json").use { parse(it.readBytes().decodeToString()) }
            }.getOrDefault(emptyMap())
        }
    }

    /** Context whose configuration matches the chosen language, so layout goes right-to-left. */
    fun wrap(base: Context): Context {
        init(base)
        val cfg = Configuration(base.resources.configuration)
        cfg.setLocale(if (arabic) ARABIC else Locale.ENGLISH)
        cfg.setLayoutDirection(if (arabic) ARABIC else Locale.ENGLISH)
        return base.createConfigurationContext(cfg)
    }

    /**
     * "context|Text" keys tell apart words that are the same in English but not in Arabic
     * ("muscle|Back" vs the Back button). English shows only the part after the bar.
     */
    fun lookup(en: String): String {
        val plain = if (CONTEXT.matches(en)) en.substringAfter('|') else en
        return if (arabic) table[en] ?: plain else plain
    }

    private val CONTEXT = Regex("^[a-z]+\\|.+", RegexOption.DOT_MATCHES_ALL)

    /** For tests and tools. */
    fun useTable(arabic: Boolean, table: Map<String, String>) {
        this.arabic = arabic; this.table = table; loaded = true
    }

    fun parse(json: String): Map<String, String> {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject
        return obj.mapValues { (it.value as kotlinx.serialization.json.JsonPrimitive).content }
    }
}

/** Translate. The English text is the key. */
fun t(en: String): String = Lang.lookup(en)

/** Translate, then fill %s / %1$s placeholders. Numbers stay Western in both languages. */
fun t(en: String, vararg args: Any?): String = String.format(Locale.US, Lang.lookup(en), *args)

/** Save the language and restart the app so every screen, list and cached name picks it up. */
fun switchLanguage(activity: android.app.Activity, pref: String) {
    if (Lang.pref(activity) == pref) return
    Lang.setPref(activity, pref)
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName) ?: return
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    activity.startActivity(intent)
    Runtime.getRuntime().exit(0)
}
