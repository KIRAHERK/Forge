package com.forge.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

// Dates are stored as LocalDate.toEpochDay(); instants as epoch millis.

@Serializable
@Entity(tableName = "sessions", indices = [Index("date")])
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val dayId: String,
    val startedAt: Long,
    val finishedAt: Long? = null,
    val makeUp: Boolean = false,
    val xp: Int = 0,
    val volumeKg: Double = 0.0,
    val prs: Int = 0,
    val prNames: String = "",
    /** False when finished with under 75% of sets — logged, but not counted as done. */
    val complete: Boolean = true,
)

@Serializable
@Entity(tableName = "sets", indices = [Index("sessionId"), Index("exerciseId")])
data class SetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val exerciseId: String,
    val exerciseIndex: Int,
    val setIndex: Int,
    val reps: Int,
    val weightKg: Double,
    val done: Boolean = false,
    val doneAt: Long? = null,
)

@Serializable
@Entity(tableName = "food", indices = [Index("date")])
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val at: Long,
    val name: String,
    val kcal: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
    val servings: Double = 1.0,
    val source: String = "",
)

@Serializable
@Entity(tableName = "weights")
data class WeightEntity(
    @PrimaryKey val date: Long,
    val kg: Double,
    val at: Long,
)

/** A habit the user defined. BUILD = something to do daily; QUIT = something to stay clean of. */
@Serializable
@Entity(tableName = "habit_defs")
data class HabitDef(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: String = KIND_BUILD,
    /** QUIT habits only: ask how many times instead of yes/no. */
    val counted: Boolean = false,
    val startDate: Long,
    val archived: Boolean = false,
    val sort: Int = 0,
) {
    val isQuit: Boolean get() = kind == KIND_QUIT

    companion object {
        const val KIND_BUILD = "BUILD"
        const val KIND_QUIT = "QUIT"
    }
}

/**
 * One day of one habit. value: null = not logged.
 * BUILD: 1 = done, 0 = not done. QUIT: 0 = clean, n > 0 = slipped (n times when counted).
 */
@Serializable
@Entity(tableName = "habit_logs", primaryKeys = ["habitId", "date"])
data class HabitLog(
    val habitId: Long,
    val date: Long,
    val value: Int? = null,
    val urges: Int = 0,
)

@Serializable
@Entity(tableName = "penalties", indices = [Index(value = ["key"], unique = true)])
data class PenaltyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val kind: String,
    val reason: String,
    val amount: Int,
    val unit: String,
    val what: String,
    val createdAt: Long,
    val dueAt: Long,
    /** ACTIVE, DONE, FAILED */
    val status: String = "ACTIVE",
    val escalated: Boolean = false,
    val completedAt: Long? = null,
    val jarAmount: Int = 0,
    val jarPaid: Boolean = false,
    val seen: Boolean = false,
) {
    val taskText: String get() = if (unit.isBlank()) "$amount $what" else "$amount $unit $what"
}

@Serializable
@Entity(tableName = "xp", indices = [Index(value = ["key"], unique = true)])
data class XpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val key: String,
    val at: Long,
    val amount: Int,
    val reason: String,
)

@Serializable
@Entity(tableName = "custom_foods")
data class CustomFoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val serving: String,
    val kcal: Int,
    val protein: Double,
    val fat: Double,
    val carbs: Double,
)

@Serializable
@Entity(tableName = "measurements")
data class MeasurementEntity(
    @PrimaryKey val date: Long,
    val chest: Double? = null,
    val arm: Double? = null,
    val waist: Double? = null,
    val thigh: Double? = null,
)

@Dao
interface MeasurementDao {
    @Upsert suspend fun upsert(m: MeasurementEntity)
    @Query("SELECT * FROM measurements ORDER BY date") fun all(): Flow<List<MeasurementEntity>>
    @Query("SELECT * FROM measurements ORDER BY date") suspend fun allNow(): List<MeasurementEntity>
    @Query("DELETE FROM measurements WHERE date = :date") suspend fun delete(date: Long)
}

@Dao
interface SessionDao {
    @Insert suspend fun insert(s: SessionEntity): Long
    @Update suspend fun update(s: SessionEntity)
    @Query("SELECT * FROM sessions WHERE id = :id") suspend fun get(id: Long): SessionEntity?
    @Query("SELECT * FROM sessions WHERE id = :id") fun observe(id: Long): Flow<SessionEntity?>
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC") fun all(): Flow<List<SessionEntity>>
    @Query("SELECT * FROM sessions") suspend fun allNow(): List<SessionEntity>
    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") fun active(): Flow<SessionEntity?>
    @Query("SELECT * FROM sessions WHERE finishedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun activeNow(): SessionEntity?
    @Query("SELECT * FROM sessions WHERE finishedAt IS NOT NULL AND date BETWEEN :from AND :to") suspend fun doneBetween(from: Long, to: Long): List<SessionEntity>
    @Query("DELETE FROM sessions WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface SetDao {
    @Insert suspend fun insertAll(s: List<SetEntity>)
    @Insert suspend fun insert(s: SetEntity): Long
    @Update suspend fun update(s: SetEntity)
    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY exerciseIndex, setIndex") fun forSession(sessionId: Long): Flow<List<SetEntity>>
    @Query("SELECT * FROM sets WHERE sessionId = :sessionId ORDER BY exerciseIndex, setIndex") suspend fun forSessionNow(sessionId: Long): List<SetEntity>
    @Query("SELECT * FROM sets WHERE sessionId = :sessionId AND exerciseIndex = :exIndex ORDER BY setIndex DESC LIMIT 1") suspend fun lastOf(sessionId: Long, exIndex: Int): SetEntity?
    @Query("DELETE FROM sets WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM sets WHERE sessionId = :sessionId") suspend fun deleteForSession(sessionId: Long)
    @Query(
        """SELECT sets.* FROM sets JOIN sessions ON sets.sessionId = sessions.id
           WHERE sets.exerciseId = :exerciseId AND sets.done = 1 AND sessions.finishedAt IS NOT NULL
           ORDER BY sessions.startedAt ASC, sets.setIndex ASC"""
    ) suspend fun historyFor(exerciseId: String): List<SetEntity>
    @Query(
        """SELECT sets.* FROM sets JOIN sessions ON sets.sessionId = sessions.id
           WHERE sets.done = 1 AND sessions.finishedAt IS NOT NULL"""
    ) fun allDone(): Flow<List<SetEntity>>
    @Query("SELECT * FROM sets") suspend fun allNow(): List<SetEntity>
}

@Dao
interface FoodDao {
    @Insert suspend fun insert(f: FoodEntity): Long
    @Insert suspend fun insertAll(f: List<FoodEntity>)
    @Query("DELETE FROM food WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM food WHERE date = :date ORDER BY at") fun forDay(date: Long): Flow<List<FoodEntity>>
    @Query("SELECT * FROM food WHERE date = :date ORDER BY at") suspend fun forDayNow(date: Long): List<FoodEntity>
    @Query("SELECT * FROM food WHERE date BETWEEN :from AND :to") fun between(from: Long, to: Long): Flow<List<FoodEntity>>
    @Query("SELECT * FROM food WHERE date BETWEEN :from AND :to") suspend fun betweenNow(from: Long, to: Long): List<FoodEntity>
    @Query("SELECT * FROM food ORDER BY at DESC LIMIT 60") fun recent(): Flow<List<FoodEntity>>
    @Query("SELECT * FROM food") suspend fun allNow(): List<FoodEntity>
}

@Dao
interface WeightDao {
    @Upsert suspend fun upsert(w: WeightEntity)
    @Query("DELETE FROM weights WHERE date = :date") suspend fun delete(date: Long)
    @Query("SELECT * FROM weights ORDER BY date") fun all(): Flow<List<WeightEntity>>
    @Query("SELECT * FROM weights ORDER BY date") suspend fun allNow(): List<WeightEntity>
    @Query("SELECT * FROM weights WHERE date = :date") suspend fun get(date: Long): WeightEntity?
}

@Dao
interface HabitDao {
    @Insert suspend fun insertDef(d: HabitDef): Long
    @Update suspend fun updateDef(d: HabitDef)
    @Query("DELETE FROM habit_defs WHERE id = :id") suspend fun deleteDef(id: Long)
    @Query("SELECT * FROM habit_defs WHERE archived = 0 ORDER BY sort, id") fun defs(): Flow<List<HabitDef>>
    @Query("SELECT * FROM habit_defs WHERE archived = 0 ORDER BY sort, id") suspend fun defsNow(): List<HabitDef>
    @Query("SELECT * FROM habit_defs") suspend fun allDefsNow(): List<HabitDef>
    @Query("SELECT * FROM habit_defs WHERE id = :id") suspend fun def(id: Long): HabitDef?

    @Upsert suspend fun upsertLog(l: HabitLog)
    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date") suspend fun log(habitId: Long, date: Long): HabitLog?
    @Query("SELECT * FROM habit_logs ORDER BY date") fun logs(): Flow<List<HabitLog>>
    @Query("SELECT * FROM habit_logs ORDER BY date") suspend fun logsNow(): List<HabitLog>
    @Query("SELECT * FROM habit_logs WHERE date = :date") suspend fun logsFor(date: Long): List<HabitLog>
    @Query("DELETE FROM habit_logs WHERE habitId = :habitId") suspend fun deleteLogs(habitId: Long)
}

@Dao
interface PenaltyDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(p: PenaltyEntity): Long
    @Update suspend fun update(p: PenaltyEntity)
    @Query("SELECT * FROM penalties ORDER BY createdAt DESC") fun all(): Flow<List<PenaltyEntity>>
    @Query("SELECT * FROM penalties ORDER BY createdAt DESC") suspend fun allNow(): List<PenaltyEntity>
    @Query("SELECT * FROM penalties WHERE status = 'ACTIVE' ORDER BY dueAt") suspend fun activeNow(): List<PenaltyEntity>
    @Query("SELECT * FROM penalties WHERE id = :id") suspend fun get(id: Long): PenaltyEntity?
    @Query("SELECT * FROM penalties WHERE `key` = :key") suspend fun byKey(key: String): PenaltyEntity?
    @Query("DELETE FROM penalties WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface XpDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(x: XpEntity): Long
    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp") fun total(): Flow<Int>
    @Query("SELECT COALESCE(SUM(amount), 0) FROM xp") suspend fun totalNow(): Int
    @Query("SELECT * FROM xp ORDER BY at DESC LIMIT 50") fun recent(): Flow<List<XpEntity>>
    @Query("SELECT * FROM xp") suspend fun allNow(): List<XpEntity>
    @Query("DELETE FROM xp WHERE `key` = :key") suspend fun deleteKey(key: String)
}

@Dao
interface CustomFoodDao {
    @Insert suspend fun insert(f: CustomFoodEntity): Long
    @Query("DELETE FROM custom_foods WHERE id = :id") suspend fun delete(id: Long)
    @Query("SELECT * FROM custom_foods ORDER BY name") fun all(): Flow<List<CustomFoodEntity>>
    @Query("SELECT * FROM custom_foods ORDER BY name") suspend fun allNow(): List<CustomFoodEntity>
}

@Database(
    entities = [
        SessionEntity::class, SetEntity::class, FoodEntity::class, WeightEntity::class,
        HabitDef::class, HabitLog::class, PenaltyEntity::class, XpEntity::class, CustomFoodEntity::class,
        MeasurementEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ForgeDb : RoomDatabase() {
    abstract fun sessions(): SessionDao
    abstract fun sets(): SetDao
    abstract fun food(): FoodDao
    abstract fun weights(): WeightDao
    abstract fun habits(): HabitDao
    abstract fun penalties(): PenaltyDao
    abstract fun xp(): XpDao
    abstract fun customFoods(): CustomFoodDao
    abstract fun measurements(): MeasurementDao

    companion object {
        fun create(context: Context): ForgeDb =
            Room.databaseBuilder(context, ForgeDb::class.java, "forge.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}

/**
 * v1 → v2: version 1 tracked two fixed habits in one table (date, first, second, urges).
 * They become ordinary user habits called "Habit 1" and "Habit 2" that the user can rename.
 * Columns are read by position so nothing about the old habits is spelled out here.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_defs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, " +
                "`kind` TEXT NOT NULL, `counted` INTEGER NOT NULL, `startDate` INTEGER NOT NULL, `archived` INTEGER NOT NULL, `sort` INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `habit_logs` (`habitId` INTEGER NOT NULL, `date` INTEGER NOT NULL, `value` INTEGER, " +
                "`urges` INTEGER NOT NULL, PRIMARY KEY(`habitId`, `date`))",
        )
        migrateLegacyHabitRows(db.query("SELECT * FROM habits ORDER BY 1").use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        LegacyHabitRow(
                            date = c.getLong(0),
                            first = if (c.isNull(1)) null else c.getInt(1),
                            second = if (c.isNull(2)) null else c.getInt(2),
                            urges = if (c.isNull(3)) 0 else c.getInt(3),
                        ),
                    )
                }
            }
        }).let { (defs, logs) ->
            defs.forEach {
                db.execSQL(
                    "INSERT INTO habit_defs (id, name, kind, counted, startDate, archived, sort) VALUES (?, ?, ?, ?, ?, 0, ?)",
                    arrayOf<Any>(it.id, it.name, it.kind, if (it.counted) 1 else 0, it.startDate, it.sort),
                )
            }
            logs.forEach {
                db.execSQL(
                    "INSERT OR REPLACE INTO habit_logs (habitId, date, value, urges) VALUES (?, ?, ?, ?)",
                    arrayOf<Any?>(it.habitId, it.date, it.value, it.urges),
                )
            }
        }
        db.execSQL("DROP TABLE IF EXISTS habits")

        // Penalties and XP that came from those habits: neutral wording, keys in the new "id:date" form.
        // Old keys started with different letters for the first and second habit.
        db.execSQL(
            "UPDATE penalties SET kind = 'SLIP', " +
                "`key` = 'slip:' || (CASE WHEN substr(`key`, 1, 1) = 's' THEN '1' ELSE '2' END) || ':' || substr(`key`, instr(`key`, ':') + 1), " +
                "reason = 'Slip: Habit ' || (CASE WHEN substr(`key`, 1, 1) = 's' THEN '1' ELSE '2' END) || ' on ' || substr(reason, instr(reason, ' on ') + 4) " +
                "WHERE kind <> 'MISSED_WORKOUT'",
        )
        db.execSQL(
            "UPDATE xp SET " +
                "`key` = 'habit:' || (CASE WHEN substr(`key`, 7, 1) = 's' THEN '1' ELSE '2' END) || ':' || substr(`key`, instr(`key`, ':') + 1), " +
                "reason = 'Habit ' || (CASE WHEN substr(`key`, 7, 1) = 's' THEN '1' ELSE '2' END) || ' kept' " +
                "|| (CASE WHEN instr(reason, ' · ') > 0 THEN substr(reason, instr(reason, ' · ')) ELSE '' END) " +
                "WHERE substr(`key`, 1, 6) = 'clean_'",
        )
    }
}

/** A version-1 habit row, by position: date, first habit, second habit, urges resisted. */
data class LegacyHabitRow(val date: Long, val first: Int?, val second: Int?, val urges: Int)

/** Shared by the database migration and by importing a version-1 backup. */
fun migrateLegacyHabitRows(rows: List<LegacyHabitRow>): Pair<List<HabitDef>, List<HabitLog>> {
    val defs = mutableListOf<HabitDef>()
    val logs = mutableListOf<HabitLog>()
    val firstRows = rows.filter { it.first != null }
    val secondRows = rows.filter { it.second != null }
    if (firstRows.isNotEmpty()) {
        defs += HabitDef(id = 1, name = "Habit 1", kind = HabitDef.KIND_QUIT, counted = true, startDate = firstRows.minOf { it.date }, sort = 0)
    }
    if (secondRows.isNotEmpty()) {
        defs += HabitDef(id = 2, name = "Habit 2", kind = HabitDef.KIND_QUIT, counted = false, startDate = secondRows.minOf { it.date }, sort = 1)
    }
    val urgesTo = defs.firstOrNull()?.id
    rows.forEach { r ->
        if (r.first != null) logs += HabitLog(1, r.date, r.first, if (urgesTo == 1L) r.urges else 0)
        if (r.second != null) logs += HabitLog(2, r.date, if (r.second > 0) 1 else 0, if (urgesTo == 2L) r.urges else 0)
    }
    return defs to logs
}
