package com.forge.app.data

import com.forge.app.i18n.t

import android.content.Context
import com.forge.app.domain.Macros
import com.forge.app.domain.PenaltyKind
import com.forge.app.domain.Penalties
import com.forge.app.domain.Program
import com.forge.app.domain.Progression
import com.forge.app.domain.SetRecord
import com.forge.app.domain.Xp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

data class FinishSummary(
    val sessionId: Long,
    val dayTitle: String,
    val minutes: Int,
    val setsDone: Int,
    val setsTotal: Int,
    val volumeKg: Double,
    val prs: List<String>,
    val xp: Int,
    val makeUp: Boolean,
)

data class EvalResult(
    val newMisses: List<PenaltyEntity> = emptyList(),
    val escalated: List<PenaltyEntity> = emptyList(),
    val failed: List<PenaltyEntity> = emptyList(),
)

@Serializable
data class Backup(
    val version: Int = 3,
    val settings: String,
    val sessions: List<SessionEntity>,
    val sets: List<SetEntity>,
    val food: List<FoodEntity>,
    val weights: List<WeightEntity>,
    val habitDefs: List<HabitDef> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    /** Version-1 backups only; read by position, see [migrateLegacyHabitRows]. */
    val habits: List<JsonObject> = emptyList(),
    val penalties: List<PenaltyEntity>,
    val xp: List<XpEntity>,
    val programs: List<ProgramEntity> = emptyList(),
    val programDays: List<ProgramDayEntity> = emptyList(),
    val programExercises: List<ProgramExerciseEntity> = emptyList(),
    val customExercises: List<CustomExerciseEntity> = emptyList(),
    val customFoods: List<CustomFoodEntity>,
    val measurements: List<MeasurementEntity> = emptyList(),
)

class Repo(private val context: Context, val db: ForgeDb, val settings: SettingsStore) {

    private val evalLock = Mutex()
    private val zone: ZoneId get() = ZoneId.systemDefault()

    fun millis(t: LocalDateTime): Long = t.atZone(zone).toInstant().toEpochMilli()
    private fun nowMs() = System.currentTimeMillis()

    /** Called after any write that changes what the widget or reminders show. */
    var onChanged: () -> Unit = {}

    // ------------------------------------------------------------- flows

    val settingsFlow: Flow<Settings> = settings.flow
    val sessions: Flow<List<SessionEntity>> = db.sessions().all()
    val activeSession: Flow<SessionEntity?> = db.sessions().active()
    val weights: Flow<List<WeightEntity>> = db.weights().all()
    val habitDefs: Flow<List<HabitDef>> = db.habits().defs()
    val habitLogs: Flow<List<HabitLog>> = db.habits().logs()
    val penalties: Flow<List<PenaltyEntity>> = db.penalties().all()
    val xpTotal: Flow<Int> = db.xp().total()
    val xpRecent: Flow<List<XpEntity>> = db.xp().recent()
    val customFoods: Flow<List<CustomFoodEntity>> = db.customFoods().all()
    val recentFoods: Flow<List<FoodEntity>> = db.food().recent()
    val allDoneSets: Flow<List<SetEntity>> = db.sets().allDone()

    fun foodForDay(date: LocalDate): Flow<List<FoodEntity>> = db.food().forDay(date.toEpochDay())
    fun foodBetween(from: LocalDate, to: LocalDate): Flow<List<FoodEntity>> = db.food().between(from.toEpochDay(), to.toEpochDay())
    fun session(id: Long): Flow<SessionEntity?> = db.sessions().observe(id)
    fun setsFor(sessionId: Long): Flow<List<SetEntity>> = db.sets().forSession(sessionId)

    private suspend fun award(key: String, amount: Int, reason: String) {
        db.xp().insert(XpEntity(key = key, at = nowMs(), amount = amount, reason = reason))
    }

    // ------------------------------------------------------------- onboarding

    suspend fun completeOnboarding(transform: (Settings) -> Settings, todayWeight: Double?, habits: List<Pair<String, String>> = emptyList()) {
        val today = LocalDate.now()
        settings.update {
            val t = transform(it)
            t.copy(
                onboarded = true,
                trackingStart = today.toEpochDay(),
                programStart = t.weekStartOf(today).toEpochDay(),
            )
        }
        if (todayWeight != null) logWeight(today, todayWeight)
        habits.forEach { (name, kind) -> addHabit(name, kind, counted = false) }
        onChanged()
    }

    // ------------------------------------------------------------- workouts

    /** Last finished session's done sets for an exercise, by set index. */
    suspend fun lastPerformance(exerciseId: String, excludeSession: Long = -1): List<SetRecord> {
        val hist = db.sets().historyFor(exerciseId).filter { it.sessionId != excludeSession }
        if (hist.isEmpty()) return emptyList()
        val lastSession = hist.last().sessionId
        return hist.filter { it.sessionId == lastSession }.sortedBy { it.setIndex }.map { SetRecord(it.reps, it.weightKg) }
    }

    suspend fun startSession(dayId: String, date: LocalDate, makeUp: Boolean): Long {
        ProgramStore.ensure(db, settings)
        db.sessions().activeNow()?.let { return it.id }
        val day = Program.day(dayId) ?: error(t("Unknown day %s", dayId))
        val id = db.sessions().insert(
            SessionEntity(date = date.toEpochDay(), dayId = dayId, startedAt = nowMs(), makeUp = makeUp)
        )
        val sets = mutableListOf<SetEntity>()
        day.exercises.forEachIndexed { exIndex, pe ->
            val last = lastPerformance(pe.exerciseId)
            val topW = last.maxOfOrNull { it.weight } ?: 0.0
            val hitTop = last.size >= pe.sets && last.filter { abs(it.weight - topW) < 0.01 }.let { at -> at.size >= pe.sets && at.all { it.reps >= pe.repMax } }
            val weight = if (hitTop && pe.exercise.loaded) topW + pe.exercise.weightStep else topW
            repeat(pe.sets) { i ->
                val reps = when {
                    hitTop && pe.exercise.loaded -> pe.repMin
                    hitTop -> last.getOrNull(i)?.reps ?: pe.repMax
                    i < last.size -> last[i].reps.coerceIn(pe.repMin, pe.repMax)
                    else -> pe.repMin
                }
                sets += SetEntity(sessionId = id, exerciseId = pe.exerciseId, exerciseIndex = exIndex, setIndex = i, reps = reps, weightKg = weight)
            }
        }
        db.sets().insertAll(sets)
        onChanged()
        return id
    }

    suspend fun updateSet(set: SetEntity) = db.sets().update(set)

    suspend fun setDone(set: SetEntity, done: Boolean) {
        db.sets().update(set.copy(done = done, doneAt = if (done) nowMs() else null))
    }

    suspend fun addSet(sessionId: Long, exerciseIndex: Int, exerciseId: String) {
        val last = db.sets().lastOf(sessionId, exerciseIndex)
        db.sets().insert(
            SetEntity(
                sessionId = sessionId, exerciseId = exerciseId, exerciseIndex = exerciseIndex,
                setIndex = (last?.setIndex ?: -1) + 1, reps = last?.reps ?: 8, weightKg = last?.weightKg ?: 0.0,
            )
        )
    }

    suspend fun removeSet(set: SetEntity) = db.sets().delete(set.id)

    suspend fun discardSession(id: Long) {
        db.sets().deleteForSession(id)
        db.sessions().delete(id)
        onChanged()
    }

    suspend fun finishSession(id: Long): FinishSummary {
        val session = db.sessions().get(id) ?: error(t("No session"))
        ProgramStore.ensure(db, settings)
        val dayTitle = Program.day(session.dayId)?.title ?: t("Workout")
        val sets = db.sets().forSessionNow(id)
        val done = sets.filter { it.done }
        val volume = done.sumOf { it.reps * it.weightKg }

        // PRs: best estimated 1RM (or reps for bodyweight) beats every previous session.
        val prs = mutableListOf<String>()
        done.groupBy { it.exerciseId }.forEach { (exId, exSets) ->
            val prev = db.sets().historyFor(exId).filter { it.sessionId != id }
            if (prev.isNotEmpty()) {
                val prevBest = prev.maxOf { Progression.e1rm(it.reps, it.weightKg) }
                val nowBest = exSets.maxOf { Progression.e1rm(it.reps, it.weightKg) }
                if (nowBest > prevBest + 0.01) prs += com.forge.app.domain.Exercises.byId(exId).name
            }
        }

        val complete = done.size >= Stats.requiredSets(sets.size)
        val base = when {
            !complete -> 0
            session.makeUp -> Xp.MAKE_UP
            else -> Xp.WORKOUT
        }
        val xp = base + done.size * Xp.SET + prs.take(3).size * Xp.PR
        val finishedAt = nowMs()
        db.sessions().update(
            session.copy(finishedAt = finishedAt, xp = xp, volumeKg = volume, prs = prs.size, prNames = prs.joinToString("|"), complete = complete)
        )
        award("session:$id", xp, (if (!complete) t("Partial: ") else if (session.makeUp) t("Make-up: ") else "") + dayTitle)
        onChanged()
        return FinishSummary(
            sessionId = id,
            dayTitle = dayTitle,
            minutes = ((finishedAt - session.startedAt) / 60000).toInt(),
            setsDone = done.size,
            setsTotal = sets.size,
            volumeKg = volume,
            prs = prs,
            xp = xp,
            makeUp = session.makeUp,
        )
    }

    // ------------------------------------------------------------- food

    suspend fun addFood(date: LocalDate, name: String, m: Macros, servings: Double = 1.0, source: String = "") {
        val scaled = m * servings
        db.food().insert(
            FoodEntity(
                date = date.toEpochDay(), at = nowMs(), name = name,
                kcal = scaled.kcal, protein = scaled.protein, fat = scaled.fat, carbs = scaled.carbs,
                servings = servings, source = source,
            )
        )
        onChanged()
    }

    suspend fun deleteFood(id: Long) { db.food().delete(id); onChanged() }

    suspend fun addCustomFood(name: String, serving: String, m: Macros) {
        db.customFoods().insert(CustomFoodEntity(name = name, serving = serving, kcal = m.kcal, protein = m.protein, fat = m.fat, carbs = m.carbs))
    }

    suspend fun deleteCustomFood(id: Long) = db.customFoods().delete(id)

    // ------------------------------------------------------------- weight

    suspend fun logWeight(date: LocalDate, kg: Double) {
        db.weights().upsert(WeightEntity(date.toEpochDay(), kg, nowMs()))
        award("weigh:${date.toEpochDay()}", Xp.WEIGH_IN, t("Weigh-in · %s", Fmt.day(date)))
        onChanged()
    }

    suspend fun deleteWeight(date: LocalDate) {
        db.weights().delete(date.toEpochDay())
        db.xp().deleteKey("weigh:${date.toEpochDay()}")
        onChanged()
    }

    val measurements: Flow<List<MeasurementEntity>> = db.measurements().all()

    suspend fun logMeasurements(m: MeasurementEntity) {
        db.measurements().upsert(m)
        award("measure:${m.date}", 15, t("Body measurements"))
    }

    // ------------------------------------------------------------- habits

    suspend fun addHabit(name: String, kind: String, counted: Boolean): Long {
        val sort = (db.habits().allDefsNow().maxOfOrNull { it.sort } ?: -1) + 1
        val id = db.habits().insertDef(
            HabitDef(
                name = name.trim(), kind = kind, counted = counted && kind == HabitDef.KIND_QUIT,
                startDate = LocalDate.now().toEpochDay(), sort = sort,
            ),
        )
        onChanged()
        return id
    }

    suspend fun updateHabit(def: HabitDef) {
        db.habits().updateDef(def.copy(name = def.name.trim(), counted = def.counted && def.isQuit))
        onChanged()
    }

    /** Removes the habit and its history. Penalties already issued stay on the record. */
    suspend fun deleteHabit(id: Long) {
        db.habits().deleteLogs(id)
        db.habits().deleteDef(id)
        onChanged()
    }

    /**
     * Logs one day of one habit. value: null clears it. BUILD: 1 done / 0 not done.
     * QUIT: 0 clean / n slipped. A slip issues a penalty; correcting it voids the penalty.
     */
    suspend fun setHabit(def: HabitDef, date: LocalDate, value: Int?) {
        val d = date.toEpochDay()
        val cur = db.habits().log(def.id, d)
        val log = HabitLog(def.id, d, value, cur?.urges ?: 0)
        db.habits().upsertLog(log)
        val xpKey = "habit:${def.id}:$d"
        val penaltyKey = "slip:${def.id}:$d"
        when (Stats.kept(def, log)) {
            null -> { db.xp().deleteKey(xpKey); dropPenaltyIfActive(penaltyKey) }
            true -> { award(xpKey, Xp.CLEAN_DAY, "${def.name} · ${Fmt.day(date)}"); dropPenaltyIfActive(penaltyKey) }
            false -> {
                db.xp().deleteKey(xpKey)
                if (def.isQuit) createPenalty(penaltyKey, PenaltyKind.SLIP, t("Slip: %1${'$'}s on %2${'$'}s", def.name, Fmt.day(date)), d * 7 + def.id)
            }
        }
        onChanged()
    }

    /** "Clean day" from the night notification: every quit habit not yet logged today is logged clean. */
    suspend fun logQuitHabitsClean(date: LocalDate) {
        val d = date.toEpochDay()
        val logs = db.habits().logsFor(d).associateBy { it.habitId }
        db.habits().defsNow().filter { it.isQuit && it.startDate <= d && logs[it.id]?.value == null }
            .forEach { setHabit(it, date, 0) }
    }

    suspend fun urgeResisted(habitId: Long, date: LocalDate) {
        val d = date.toEpochDay()
        val cur = db.habits().log(habitId, d) ?: HabitLog(habitId, d)
        val n = cur.urges + 1
        db.habits().upsertLog(cur.copy(urges = n))
        if (n <= 5) award("urge:$d:$habitId:$n", Xp.URGE, t("Urge resisted"))
        onChanged()
    }

    private suspend fun createPenalty(key: String, kind: PenaltyKind, reason: String, seed: Long) {
        val s = settings.current()
        if (!s.penaltiesOn) return
        // A slip that was corrected and then logged again gets its penalty back.
        db.penalties().byKey(key)?.let { old ->
            if (old.status == "VOID") {
                val now = nowMs()
                db.penalties().update(old.copy(status = "ACTIVE", reason = reason, createdAt = now, dueAt = now + Penalties.DUE_HOURS * 3_600_000, jarAmount = s.jarAmount, seen = false))
            }
            return
        }
        val task = Penalties.roll(kind, seed * 31 + kind.ordinal)
        val now = nowMs()
        db.penalties().insert(
            PenaltyEntity(
                key = key, kind = kind.name, reason = reason,
                amount = task.amount, unit = task.unit, what = task.what,
                createdAt = now, dueAt = now + Penalties.DUE_HOURS * 3_600_000,
                jarAmount = s.jarAmount,
            )
        )
    }

    /** Undo for a mis-tap: only removes a penalty nobody has acted on yet. */
    // ------------------------------------------------------------- programs

    private fun newId(prefix: String) = prefix + java.lang.Long.toString(nowMs(), 36)

    /** Makes [id] the program in use: its weekly layout becomes the schedule, and the week count restarts. */
    suspend fun activateProgram(id: String) {
        ProgramStore.ensure(db, settings)
        val p = ProgramStore.program(id) ?: return
        settings.update { s ->
            s.copy(
                activeProgramId = id,
                schedule = p.schedule,
                programStart = s.weekStartOf(LocalDate.now()).toEpochDay(),
            )
        }
        ProgramStore.reload(db, settings)
        onChanged()
    }

    /** Copies a template into a new program of the user's own. Returns its id. */
    suspend fun createFromTemplate(templateId: String?, name: String? = null): String {
        val t = templateId?.let { com.forge.app.domain.Templates.byId(it) }
        val pid = newId("p")
        val days = t?.days ?: listOf(com.forge.app.domain.TemplateDay(t("Day 1"), emptyList()))
        val dayIds = days.indices.map { "$pid-d${it + 1}" }
        val schedule = (t?.week ?: listOf(0, null, null, null, null, null, null)).map { i -> i?.let { dayIds[it] } ?: Program.REST }
        db.programs().upsertProgram(
            ProgramEntity(pid, name ?: t?.name ?: t("My program"), t?.note ?: "", t?.id ?: "", schedule.joinToString(","), createdAt = nowMs()),
        )
        db.programs().upsertDays(days.mapIndexed { i, d -> ProgramDayEntity(dayIds[i], pid, i, i + 1, d.name) })
        days.forEachIndexed { i, d -> with(ProgramStore) { db.programs().insertExercises(d.exercises.toEntities(dayIds[i])) } }
        ProgramStore.reload(db, settings)
        return pid
    }

    /** Copies one of the user's programs. Returns the new id. */
    suspend fun duplicateProgram(id: String): String? {
        val p = ProgramStore.program(id) ?: return null
        val pid = newId("p")
        val map = p.days.mapIndexed { i, d -> d.id to "$pid-d${i + 1}" }.toMap()
        db.programs().upsertProgram(
            ProgramEntity(pid, t("%s (copy)", p.name), p.note, p.templateId, p.schedule.joinToString(",") { map[it] ?: Program.REST }, createdAt = nowMs()),
        )
        db.programs().upsertDays(p.days.mapIndexed { i, d -> ProgramDayEntity(map.getValue(d.id), pid, i, i + 1, d.name) })
        p.days.forEach { d -> with(ProgramStore) { db.programs().insertExercises(d.exercises.toEntities(map.getValue(d.id))) } }
        ProgramStore.reload(db, settings)
        return pid
    }

    /** A day as the editor holds it. A null id is a new day. */
    data class DayDraft(val id: String?, val name: String, val exercises: List<com.forge.app.domain.ProgramExercise>)

    /**
     * Saves an edited program. [schedule] holds, for Monday..Sunday, an index into [days] or null for rest.
     * Removed days are kept (soft-deleted) so old sessions keep their names.
     */
    suspend fun saveProgram(id: String, name: String, note: String, days: List<DayDraft>, schedule: List<Int?>) {
        ProgramStore.ensure(db, settings)
        val old = ProgramStore.program(id) ?: return
        val existing = db.programs().daysNow().filter { it.programId == id }
        var next = (existing.maxOfOrNull { it.position } ?: -1) + 1
        val keepNumbers = id == ProgramStore.ORIGINAL_ID
        val saved = days.mapIndexed { i, d ->
            val dayId = d.id ?: "$id-n${java.lang.Long.toString(nowMs(), 36)}${next++}"
            val prev = existing.firstOrNull { it.id == dayId }
            ProgramDayEntity(
                id = dayId, programId = id, position = i,
                number = if (keepNumbers && prev != null) prev.number else i + 1,
                name = d.name.trim().ifBlank { t("Day %s", i + 1) },
            )
        }
        val keptIds = saved.map { it.id }.toSet()
        db.programs().upsertDays(saved + existing.filter { it.id !in keptIds }.map { it.copy(deleted = true) })
        saved.forEachIndexed { i, d ->
            db.programs().clearDay(d.id)
            with(ProgramStore) { db.programs().insertExercises(days[i].exercises.toEntities(d.id)) }
        }
        val sched = schedule.map { idx -> idx?.let { saved.getOrNull(it)?.id } ?: Program.REST }
        db.programs().upsertProgram(
            ProgramEntity(id, name.trim().ifBlank { old.name }, note, old.templateId, sched.joinToString(","), old.createdAt),
        )
        if (settings.current().activeProgramId == id) settings.update { it.copy(schedule = sched) }
        ProgramStore.reload(db, settings)
        onChanged()
    }

    /** Deletes a program that isn't in use. Its days stay behind for history. */
    suspend fun deleteProgram(id: String) {
        if (settings.current().activeProgramId == id) return
        val p = db.programs().programsNow().firstOrNull { it.id == id } ?: return
        db.programs().upsertProgram(p.copy(deleted = true))
        db.programs().upsertDays(db.programs().daysNow().filter { it.programId == id }.map { it.copy(deleted = true) })
        ProgramStore.reload(db, settings)
    }

    /**
     * Adds a program received as a QR code. Its custom exercises are added as the user's own,
     * reusing one with the same name and equipment if it already exists. Returns the new id.
     */
    suspend fun importProgram(shared: ProgramCode.Shared): String {
        ProgramStore.ensure(db, settings)
        val existing = db.programs().customNow().filter { !it.deleted }
        val idMap = shared.c.associate { c ->
            val same = existing.firstOrNull { it.name.equals(c.n, ignoreCase = true) && it.equip == c.q }
            c.id to (same?.id ?: newId("c") + c.id.hashCode().toUInt().toString(36).take(4)).also { id ->
                if (same == null) db.programs().upsertCustom(CustomExerciseEntity(id, c.n, c.q, c.g, "", c.r))
            }
        }
        val pid = newId("p")
        val dayIds = shared.d.indices.map { "$pid-d${it + 1}" }
        val schedule = shared.w.map { i -> if (i >= 0) dayIds[i] else Program.REST }
        db.programs().upsertProgram(ProgramEntity(pid, shared.n, t("Shared with you"), "", schedule.joinToString(","), createdAt = nowMs()))
        db.programs().upsertDays(shared.d.mapIndexed { i, d -> ProgramDayEntity(dayIds[i], pid, i, i + 1, d.n) })
        shared.d.forEachIndexed { i, d ->
            with(ProgramStore) { db.programs().insertExercises(ProgramCode.toExercises(d, idMap).toEntities(dayIds[i])) }
        }
        ProgramStore.reload(db, settings)
        return pid
    }

    /** Adds a custom exercise. Returns its id. */
    suspend fun addCustomExercise(name: String, equip: com.forge.app.domain.Equip, group: com.forge.app.domain.MuscleGroup, restSec: Int, notes: String): String {
        val id = newId("c")
        db.programs().upsertCustom(CustomExerciseEntity(id, name.trim(), equip.name, group.name, "", restSec, notes.trim()))
        ProgramStore.reload(db, settings)
        return id
    }

    /** Hides a custom exercise from the picker. History and programs that use it keep its name. */
    suspend fun deleteCustomExercise(id: String) {
        val c = db.programs().customNow().firstOrNull { it.id == id } ?: return
        db.programs().upsertCustom(c.copy(deleted = true))
        ProgramStore.reload(db, settings)
    }

    /**
     * Marks or unmarks a training day as sick. Marking cancels that day's missed-workout
     * penalty (if it is still active) and gives the lost XP back. Unmarking removes the
     * cancelled penalty so evaluation can issue the miss again if the deadline has passed.
     */
    suspend fun setSick(date: LocalDate, sick: Boolean) {
        val d = date.toEpochDay()
        val keepFrom = LocalDate.now().minusDays(400).toEpochDay()
        settings.update { s ->
            val days = if (sick) s.sickDays + d else s.sickDays - d
            s.copy(sickDays = days.distinct().filter { it >= keepFrom }.sorted())
        }
        val key = "miss:$d"
        if (sick) {
            db.xp().deleteKey(key)
            db.penalties().byKey(key)?.let { p ->
                if (p.status == "ACTIVE") db.penalties().update(p.copy(status = "VOID", jarAmount = 0))
            }
        } else {
            db.penalties().byKey(key)?.takeIf { it.status == "VOID" }?.let { db.penalties().delete(it.id) }
            evaluate()
        }
        onChanged()
    }

    private suspend fun dropPenaltyIfActive(key: String) {
        val p = db.penalties().byKey(key) ?: return
        if (p.status == "ACTIVE" && !p.escalated) {
            db.penalties().update(p.copy(status = "VOID", jarAmount = 0))
        }
    }

    // ------------------------------------------------------------- penalties

    suspend fun completePenalty(id: Long) {
        val p = db.penalties().get(id) ?: return
        if (p.status != "ACTIVE") return
        db.penalties().update(p.copy(status = "DONE", completedAt = nowMs()))
        award("penalty_done:$id", Xp.PENALTY_DONE, t("Penalty paid: %s", p.taskText))
        onChanged()
    }

    suspend fun markPenaltySeen(id: Long) {
        val p = db.penalties().get(id) ?: return
        db.penalties().update(p.copy(seen = true))
    }

    suspend fun payJar() {
        db.penalties().allNow().filter { it.jarAmount > 0 && !it.jarPaid }.forEach {
            db.penalties().update(it.copy(jarPaid = true))
        }
    }

    // ------------------------------------------------------------- evaluation

    /**
     * Turns the passage of time into consequences: missed sessions, overdue penalties,
     * daily nutrition XP. Idempotent — safe to call as often as needed.
     */
    suspend fun evaluate(now: LocalDateTime = LocalDateTime.now()): EvalResult = evalLock.withLock {
        ProgramStore.ensure(db, settings)
        val s = settings.current()
        if (!s.onboarded) return@withLock EvalResult()
        val today = now.toLocalDate()
        val nowMs = millis(now)
        val allSessions = db.sessions().allNow()
        val done = Stats.doneDates(allSessions)
        // A session still running for that day is not a miss yet.
        val inProgress = allSessions.filter { it.finishedAt == null }.map { it.date }.toSet()
        val from = maxOf(s.trackingStart, today.minusDays(21).toEpochDay())

        val newMisses = mutableListOf<PenaltyEntity>()
        for (d in from..today.toEpochDay()) {
            val date = LocalDate.ofEpochDay(d)
            if (!s.isTraining(date) || s.isSick(date) || d in done || d in inProgress) continue
            if (now.isBefore(s.deadlineAt(date))) continue
            val day = s.dayFor(date) ?: continue
            val key = "miss:$d"
            award(key, Xp.MISSED_WORKOUT, t("Missed %s", day.title))
            if (s.penaltiesOn) {
                val task = Penalties.roll(PenaltyKind.MISSED_WORKOUT, d * 31 + 7)
                val p = PenaltyEntity(
                    key = key, kind = PenaltyKind.MISSED_WORKOUT.name,
                    reason = t("Missed %1${'$'}s on %2${'$'}s", day.title, Fmt.day(date)),
                    amount = task.amount, unit = task.unit, what = task.what,
                    createdAt = nowMs, dueAt = nowMs + Penalties.DUE_HOURS * 3_600_000,
                    jarAmount = s.jarAmount,
                )
                if (db.penalties().insert(p) != -1L) newMisses += p
            }
        }

        val escalated = mutableListOf<PenaltyEntity>()
        val failed = mutableListOf<PenaltyEntity>()
        for (p in db.penalties().activeNow()) {
            if (p.dueAt >= nowMs) continue
            if (!p.escalated) {
                val up = p.copy(amount = p.amount * 2, escalated = true, dueAt = nowMs + Penalties.DUE_HOURS * 3_600_000, seen = false)
                db.penalties().update(up)
                award("overdue:${p.id}", Xp.PENALTY_OVERDUE, t("Penalty overdue — doubled"))
                escalated += up
            } else {
                val f = p.copy(status = "FAILED")
                db.penalties().update(f)
                award("failed:${p.id}", Xp.PENALTY_FAILED, t("Penalty failed"))
                failed += f
            }
        }

        // Nutrition XP for finished days.
        if (from < today.toEpochDay()) {
            val foods = db.food().betweenNow(from, today.toEpochDay() - 1).groupBy { it.date }
            foods.forEach { (d, list) ->
                val m = Stats.sum(list)
                if (Stats.proteinHit(m.protein, s.protein)) award("protein:$d", Xp.PROTEIN_DAY, t("Protein target hit · %s", Fmt.day(LocalDate.ofEpochDay(d))))
                if (Stats.kcalHit(m.kcal, s.kcal)) award("kcal:$d", Xp.CALORIE_DAY, t("Calorie target hit · %s", Fmt.day(LocalDate.ofEpochDay(d))))
            }
        }

        if (newMisses.isNotEmpty() || escalated.isNotEmpty() || failed.isNotEmpty()) onChanged()
        EvalResult(newMisses, escalated, failed)
    }

    // ------------------------------------------------------------- backup

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun exportJson(): String {
        val b = Backup(
            settings = settings.encode(settings.current()),
            sessions = db.sessions().allNow(),
            sets = db.sets().allNow(),
            food = db.food().allNow(),
            weights = db.weights().allNow(),
            habitDefs = db.habits().allDefsNow(),
            habitLogs = db.habits().logsNow(),
            penalties = db.penalties().allNow(),
            xp = db.xp().allNow(),
            customFoods = db.customFoods().allNow(),
            measurements = db.measurements().allNow(),
            programs = db.programs().programsNow(),
            programDays = db.programs().daysNow(),
            programExercises = db.programs().exercisesNow(),
            customExercises = db.programs().customNow(),
        )
        return json.encodeToString(Backup.serializer(), b)
    }

    suspend fun importJson(text: String) {
        val b = json.decodeFromString(Backup.serializer(), text)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { db.clearAllTables() }
        b.sessions.forEach { db.sessions().insert(it) }
        db.sets().insertAll(b.sets)
        db.food().insertAll(b.food)
        b.weights.forEach { db.weights().upsert(it) }
        val legacy = b.version < 2
        val (defs, logs) = if (legacy) migrateLegacyHabitRows(b.habits.map(::legacyRow)) else b.habitDefs to b.habitLogs
        defs.forEach { db.habits().insertDef(it) }
        logs.forEach { db.habits().upsertLog(it) }
        b.penalties.forEach { db.penalties().insert(if (legacy) legacyPenalty(it) else it) }
        b.xp.forEach { db.xp().insert(if (legacy) legacyXp(it) else it) }
        b.customFoods.forEach { db.customFoods().insert(it) }
        b.measurements.forEach { db.measurements().upsert(it) }
        b.programs.forEach { db.programs().upsertProgram(it) }
        db.programs().upsertDays(b.programDays)
        db.programs().insertExercises(b.programExercises.map { it.copy(id = 0) })
        b.customExercises.forEach { db.programs().upsertCustom(it) }
        val restored = settings.decode(b.settings)
        settings.update { restored }
        // A backup from before programs existed gets the original program back, with its schedule.
        ProgramStore.reload(db, settings)
        onChanged()
    }

    // Version-1 backups: same conversion as the database migration (see MIGRATION_1_2).

    private fun JsonElement?.intish(): Int? {
        val p = this as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.intOrNull ?: p.booleanOrNull?.let { if (it) 1 else 0 }
    }

    private fun legacyRow(o: JsonObject): LegacyHabitRow {
        val v = o.values.toList()
        return LegacyHabitRow(
            date = (v.getOrNull(0) as? JsonPrimitive)?.longOrNull ?: 0,
            first = v.getOrNull(1).intish(),
            second = v.getOrNull(2).intish(),
            urges = v.getOrNull(3).intish() ?: 0,
        )
    }

    private fun legacyHabitNo(key: String, letterAt: Int) = if (key.getOrNull(letterAt) == 's') 1 else 2

    private fun legacyPenalty(p: PenaltyEntity): PenaltyEntity {
        if (p.kind == PenaltyKind.MISSED_WORKOUT.name) return p
        val n = legacyHabitNo(p.key, 0)
        val day = p.reason.substringAfter(" on ", "")
        return p.copy(kind = PenaltyKind.SLIP.name, key = "slip:$n:${p.key.substringAfter(':')}", reason = t("Slip: Habit %1${'$'}s on %2${'$'}s", n, day))
    }

    private fun legacyXp(x: XpEntity): XpEntity {
        if (!x.key.startsWith("clean_")) return x
        val n = legacyHabitNo(x.key, 6)
        val tail = x.reason.substringAfter(" · ", "").let { if (it.isBlank()) "" else " · $it" }
        return x.copy(key = "habit:$n:${x.key.substringAfter(':')}", reason = t("Habit %1${'$'}s kept%2${'$'}s", n, tail))
    }
}
