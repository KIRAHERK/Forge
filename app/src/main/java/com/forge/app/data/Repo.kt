package com.forge.app.data

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
    val version: Int = 2,
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
        db.sessions().activeNow()?.let { return it.id }
        val day = Program.day(dayId) ?: error("Unknown day $dayId")
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
        val session = db.sessions().get(id) ?: error("No session")
        val day = Program.day(session.dayId)!!
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
        award("session:$id", xp, (if (!complete) "Partial: " else if (session.makeUp) "Make-up: " else "") + day.title)
        onChanged()
        return FinishSummary(
            sessionId = id,
            dayTitle = day.title,
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
        award("weigh:${date.toEpochDay()}", Xp.WEIGH_IN, "Weigh-in · ${Fmt.day(date)}")
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
        award("measure:${m.date}", 15, "Body measurements")
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
                if (def.isQuit) createPenalty(penaltyKey, PenaltyKind.SLIP, "Slip: ${def.name} on ${Fmt.day(date)}", d * 7 + def.id)
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
        if (n <= 5) award("urge:$d:$habitId:$n", Xp.URGE, "Urge resisted")
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
        award("penalty_done:$id", Xp.PENALTY_DONE, "Penalty paid: ${p.taskText}")
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
            if (!s.isTraining(date) || d in done || d in inProgress) continue
            if (now.isBefore(s.deadlineAt(date))) continue
            val day = s.dayFor(date) ?: continue
            val key = "miss:$d"
            award(key, Xp.MISSED_WORKOUT, "Missed ${day.title}")
            if (s.penaltiesOn) {
                val task = Penalties.roll(PenaltyKind.MISSED_WORKOUT, d * 31 + 7)
                val p = PenaltyEntity(
                    key = key, kind = PenaltyKind.MISSED_WORKOUT.name,
                    reason = "Missed ${day.title} on ${Fmt.day(date)}",
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
                award("overdue:${p.id}", Xp.PENALTY_OVERDUE, "Penalty overdue — doubled")
                escalated += up
            } else {
                val f = p.copy(status = "FAILED")
                db.penalties().update(f)
                award("failed:${p.id}", Xp.PENALTY_FAILED, "Penalty failed")
                failed += f
            }
        }

        // Nutrition XP for finished days.
        if (from < today.toEpochDay()) {
            val foods = db.food().betweenNow(from, today.toEpochDay() - 1).groupBy { it.date }
            foods.forEach { (d, list) ->
                val m = Stats.sum(list)
                if (Stats.proteinHit(m.protein, s.protein)) award("protein:$d", Xp.PROTEIN_DAY, "Protein target hit · ${Fmt.day(LocalDate.ofEpochDay(d))}")
                if (Stats.kcalHit(m.kcal, s.kcal)) award("kcal:$d", Xp.CALORIE_DAY, "Calorie target hit · ${Fmt.day(LocalDate.ofEpochDay(d))}")
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
        val restored = settings.decode(b.settings)
        settings.update { restored }
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
        return p.copy(kind = PenaltyKind.SLIP.name, key = "slip:$n:${p.key.substringAfter(':')}", reason = "Slip: Habit $n on $day")
    }

    private fun legacyXp(x: XpEntity): XpEntity {
        if (!x.key.startsWith("clean_")) return x
        val n = legacyHabitNo(x.key, 6)
        val tail = x.reason.substringAfter(" · ", "").let { if (it.isBlank()) "" else " · $it" }
        return x.copy(key = "habit:$n:${x.key.substringAfter(':')}", reason = "Habit $n kept$tail")
    }
}
