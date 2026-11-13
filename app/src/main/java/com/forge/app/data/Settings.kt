package com.forge.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forge.app.domain.Plan
import com.forge.app.domain.Program
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

@Serializable
data class Settings(
    val onboarded: Boolean = false,
    /** First day of program week 1 (aligned to weekStart). */
    val programStart: Long = LocalDate.now().toEpochDay(),
    /** First day misses are counted from. */
    val trackingStart: Long = LocalDate.now().toEpochDay(),
    /** Monday..Sunday -> "D1","D2","D4","D5" or "R". */
    val schedule: List<String> = Program.defaultCycle,
    /** ISO day-of-week (1 = Monday). */
    val weekStart: Int = 1,

    // Times are minutes after midnight.
    val workoutTime: Int = 17 * 60,
    val deadline: Int = 21 * 60 + 30,
    val weighInTime: Int = 7 * 60 + 30,
    val proteinCheck1: Int = 14 * 60,
    val proteinCheck2: Int = 19 * 60,
    val nightTime: Int = 22 * 60 + 30,

    val remindWorkout: Boolean = true,
    val remindWeighIn: Boolean = true,
    val remindNutrition: Boolean = true,
    val remindNight: Boolean = true,
    val remindWeekly: Boolean = true,

    val penaltiesOn: Boolean = true,
    val jarAmount: Int = 0,
    val jarCurrency: String = "",

    val kcal: Int = Plan.KCAL,
    val protein: Int = Plan.PROTEIN,
    val fat: Int = Plan.FAT,
    val carbs: Int = Plan.CARBS,
    val startWeight: Double = Plan.START_WEIGHT,
    val goalWeight: Double = Plan.MILESTONE,

    val lastCheckInAck: Long = -1,
    val lastSeenLevel: Int = 1,

    /** The program in use; its weekly layout is mirrored in [schedule]. Empty until the first load. */
    val activeProgramId: String = "",

    /** Training days marked as sick (epoch days): no penalty, no XP lost, streak paused. */
    val sickDays: List<Long> = emptyList(),

    /** Free-text notes from your own plan (e.g. a doctor's limit). Kept on the phone, never in the code. */
    val planNote: String = "",

    /** Main color, an [com.forge.app.domain.Accent] name. */
    val accent: String = "GREEN",
    /** False until the person has picked a color (new installs pick during setup). */
    val accentChosen: Boolean = false,

    val autoUpdateCheck: Boolean = true,
    val lastNotifiedVersion: String = "",
    val autoBackup: Boolean = true,
    val lastBackupAt: Long = 0,
    val lastBackupReason: String = "",
) {
    fun slotFor(date: LocalDate): String = schedule.getOrElse(date.dayOfWeek.value - 1) { Program.REST }
    fun isTraining(date: LocalDate) = slotFor(date) != Program.REST
    fun isSick(date: LocalDate) = date.toEpochDay() in sickDays
    fun dayFor(date: LocalDate) = Program.day(slotFor(date))

    fun deadlineAt(date: LocalDate): LocalDateTime = date.atTime(LocalTime.of(deadline / 60, deadline % 60))

    fun weekStartOf(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.of(weekStart)))

    /** 1-based program week, may exceed 12. */
    fun programWeek(date: LocalDate): Int = ((date.toEpochDay() - programStart).coerceAtLeast(0) / 7 + 1).toInt()

    val trackingStartDate: LocalDate get() = LocalDate.ofEpochDay(trackingStart)

    companion object {
        /** Build a schedule from the PDF's 7-day cycle starting on [start]. */
        fun cycleFrom(start: DayOfWeek): List<String> {
            val out = MutableList(7) { Program.REST }
            Program.defaultCycle.forEachIndexed { i, slot ->
                val dow = start.plus(i.toLong())
                out[dow.value - 1] = slot
            }
            return out
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "forge_settings")

class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("settings")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        (prefs[key]?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings())
            .also { com.forge.app.domain.Accent.current = com.forge.app.domain.Accent.of(it.accent) }
    }

    suspend fun current(): Settings = flow.first()

    suspend fun update(transform: (Settings) -> Settings) {
        context.dataStore.edit { prefs ->
            val cur = prefs[key]?.let { runCatching { json.decodeFromString(Settings.serializer(), it) }.getOrNull() } ?: Settings()
            prefs[key] = json.encodeToString(Settings.serializer(), transform(cur))
        }
    }

    fun encode(s: Settings): String = json.encodeToString(Settings.serializer(), s)
    fun decode(s: String): Settings = json.decodeFromString(Settings.serializer(), s)
}
