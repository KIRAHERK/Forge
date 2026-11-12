package com.forge.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.serialization.Serializable

// User programs. A program has days; a day has exercises in order. Days and programs are
// only ever soft-deleted, because finished sessions keep pointing at their day's id.

@Serializable
@Entity(tableName = "programs")
data class ProgramEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String = "",
    /** The template it was started from, if any. */
    val templateId: String = "",
    /** Monday..Sunday, comma-separated: a day id or "R" for rest. */
    val schedule: String = "R,R,R,R,R,R,R",
    val createdAt: Long = 0,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "program_days", indices = [Index("programId")])
data class ProgramDayEntity(
    @PrimaryKey val id: String,
    val programId: String,
    val position: Int,
    /** Shown as "Day N". The original program keeps its 1, 2, 4, 5. */
    val number: Int,
    val name: String,
    val deleted: Boolean = false,
)

@Serializable
@Entity(tableName = "program_exercises", indices = [Index("dayId")])
data class ProgramExerciseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: String,
    val position: Int,
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val unit: String = "NONE",
    val tag: String? = null,
)

@Serializable
@Entity(tableName = "custom_exercises")
data class CustomExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val equip: String,
    val muscleGroup: String,
    val muscles: String = "",
    val restSec: Int = 90,
    val notes: String = "",
    val deleted: Boolean = false,
)

@Dao
interface ProgramDao {
    @Query("SELECT * FROM programs ORDER BY createdAt") suspend fun programsNow(): List<ProgramEntity>
    @Query("SELECT * FROM program_days ORDER BY programId, position") suspend fun daysNow(): List<ProgramDayEntity>
    @Query("SELECT * FROM program_exercises ORDER BY dayId, position") suspend fun exercisesNow(): List<ProgramExerciseEntity>
    @Query("SELECT * FROM custom_exercises ORDER BY name") suspend fun customNow(): List<CustomExerciseEntity>

    @Upsert suspend fun upsertProgram(p: ProgramEntity)
    @Upsert suspend fun upsertDays(d: List<ProgramDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertExercises(e: List<ProgramExerciseEntity>)
    @Query("DELETE FROM program_exercises WHERE dayId = :dayId") suspend fun clearDay(dayId: String)
    @Upsert suspend fun upsertCustom(c: CustomExerciseEntity)
}
