package com.forge.app.data

import com.forge.app.domain.Equip
import com.forge.app.domain.Exercise
import com.forge.app.domain.Exercises
import com.forge.app.domain.MuscleGroup
import com.forge.app.domain.Program
import com.forge.app.domain.ProgramExercise
import com.forge.app.domain.RepUnit
import com.forge.app.domain.Templates
import com.forge.app.domain.WorkoutDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A user program as the app uses it. */
data class UserProgram(
    val id: String,
    val name: String,
    val note: String,
    val templateId: String,
    /** Monday..Sunday: a day id or [Program.REST]. */
    val schedule: List<String>,
    val days: List<WorkoutDay>,
    val createdAt: Long,
) {
    val daysPerWeek: Int get() = schedule.count { it != Program.REST }
}

/**
 * In-memory copy of the user's programs, days and custom exercises, so lookups like
 * [Program.day] stay synchronous. The database is the source of truth; every write goes
 * through [com.forge.app.data.Repo], which reloads this afterwards.
 */
object ProgramStore {
    data class Snapshot(
        val programs: List<UserProgram>,
        /** Every day ever made, deleted ones included, so old sessions keep their names. */
        val allDays: Map<String, WorkoutDay>,
        val activeId: String,
    ) {
        val active: UserProgram? get() = programs.firstOrNull { it.id == activeId } ?: programs.firstOrNull()
    }

    private val _state = MutableStateFlow<Snapshot?>(null)
    val state: StateFlow<Snapshot?> = _state.asStateFlow()
    private val lock = Mutex()

    val loaded: Boolean get() = _state.value != null
    fun snapshot(): Snapshot? = _state.value

    fun day(id: String?): WorkoutDay? = id?.let { _state.value?.allDays?.get(it) }
    fun active(): UserProgram? = _state.value?.active
    fun program(id: String): UserProgram? = _state.value?.programs?.firstOrNull { it.id == id }

    const val ORIGINAL_ID = "forge-4day"

    /** Loads once. Seeds the original program the first time (an upgrade or a fresh install). */
    suspend fun ensure(db: ForgeDb, settings: SettingsStore) {
        if (_state.value == null) reload(db, settings)
    }

    suspend fun reload(db: ForgeDb, settings: SettingsStore) = lock.withLock {
        val dao = db.programs()
        if (dao.programsNow().isEmpty()) seedOriginal(db, settings)
        var s = settings.current()
        val programs = dao.programsNow()
        if (programs.none { it.id == s.activeProgramId && !it.deleted }) {
            val fallback = programs.firstOrNull { !it.deleted } ?: programs.first()
            settings.update { it.copy(activeProgramId = fallback.id) }
            s = settings.current()
        }
        val customRows = dao.customNow()
        Exercises.custom = customRows.associate { it.id to it.toExercise() }
        Exercises.retired = customRows.filter { it.deleted }.map { it.id }.toSet()
        val exByDay = dao.exercisesNow().groupBy { it.dayId }
        val days = dao.daysNow()
        val allDays = days.associate { d ->
            d.id to WorkoutDay(d.id, d.number, d.name, exByDay[d.id].orEmpty().sortedBy { it.position }.map { it.toDomain() })
        }
        val live = programs.filter { !it.deleted }.map { p ->
            UserProgram(
                id = p.id, name = p.name, note = p.note, templateId = p.templateId,
                schedule = parseSchedule(p.schedule),
                days = days.filter { it.programId == p.id && !it.deleted }.sortedBy { it.position }.mapNotNull { allDays[it.id] },
                createdAt = p.createdAt,
            )
        }
        _state.value = Snapshot(live, allDays, s.activeProgramId)
    }

    /** The program everyone had before the builder, with the same day ids so history lines up. */
    private suspend fun seedOriginal(db: ForgeDb, settings: SettingsStore) {
        val s = settings.current()
        val t = Templates.forge
        val ids = listOf("D1", "D2", "D4", "D5")
        val numbers = listOf(1, 2, 4, 5)
        val schedule = s.schedule.takeIf { it.size == 7 } ?: Program.defaultCycle
        db.programs().upsertProgram(
            ProgramEntity(ORIGINAL_ID, t.name, t.note, t.id, schedule.joinToString(","), createdAt = 1),
        )
        db.programs().upsertDays(t.days.mapIndexed { i, d -> ProgramDayEntity(ids[i], ORIGINAL_ID, i, numbers[i], d.name) })
        t.days.forEachIndexed { i, d -> db.programs().insertExercises(d.exercises.toEntities(ids[i])) }
        settings.update { it.copy(activeProgramId = ORIGINAL_ID) }
    }

    /**
     * What a session actually holds, rebuilt from its sets: one entry per exercise slot.
     * Uses the day's prescription where it still matches, so editing a program never
     * scrambles a workout in progress or one already logged.
     */
    fun sessionPlan(day: WorkoutDay?, sets: List<SetEntity>): List<ProgramExercise> =
        sets.groupBy { it.exerciseIndex }.toSortedMap().map { (idx, list) ->
            val exId = list.first().exerciseId
            val pe = day?.exercises?.getOrNull(idx)
            if (pe != null && pe.exerciseId == exId) pe.copy(sets = list.size)
            else ProgramExercise(exId, list.size, list.minOf { it.reps }, list.maxOf { it.reps })
        }

    fun parseSchedule(raw: String): List<String> =
        raw.split(',').map { it.trim().ifEmpty { Program.REST } }.let { l -> List(7) { l.getOrElse(it) { Program.REST } } }

    fun List<ProgramExercise>.toEntities(dayId: String) = mapIndexed { i, pe ->
        ProgramExerciseEntity(dayId = dayId, position = i, exerciseId = pe.exerciseId, sets = pe.sets, repMin = pe.repMin, repMax = pe.repMax, unit = pe.unit.name, tag = pe.tag)
    }

    private fun ProgramExerciseEntity.toDomain() = ProgramExercise(
        exerciseId = exerciseId, sets = sets, repMin = repMin, repMax = repMax,
        unit = runCatching { RepUnit.valueOf(unit) }.getOrDefault(RepUnit.NONE), tag = tag,
    )

    fun CustomExerciseEntity.toExercise() = Exercise(
        id = id,
        name = name,
        equip = runCatching { Equip.valueOf(equip) }.getOrDefault(Equip.BODYWEIGHT),
        restSec = restSec,
        muscles = muscles.ifBlank { runCatching { MuscleGroup.valueOf(muscleGroup).label }.getOrDefault("") },
        setup = emptyList(), steps = emptyList(), cues = emptyList(), mistakes = emptyList(),
        homeTip = notes.ifBlank { null },
        group = runCatching { MuscleGroup.valueOf(muscleGroup) }.getOrDefault(MuscleGroup.FULL_BODY),
        custom = true,
    )
}
