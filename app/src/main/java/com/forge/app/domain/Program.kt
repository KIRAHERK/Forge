package com.forge.app.domain

import com.forge.app.i18n.t

/*
 * The training program, copied exactly from
 * "4_Day_Home_Muscle_Program_Redesigned_v2.pdf". Do not edit sets, reps or order.
 */

enum class RepUnit(private val en: String) {
    NONE(""), SIDE("/ side"), LEG("/ leg"), SEC("s"), SEC_SIDE("s / side");

    val label: String get() = if (en.isEmpty()) "" else t(en)

    /** Counted in seconds rather than reps. */
    val timed: Boolean get() = this == SEC || this == SEC_SIDE
    /** Each side or leg is done separately. */
    val perSide: Boolean get() = this != NONE && this != SEC
}

enum class Equip(private val en: String) {
    BODYWEIGHT("Bodyweight"), DUMBBELL("Dumbbell"), BARBELL("Barbell"), KETTLEBELL("Kettlebell"),
    CABLE("Cable"), MACHINE("Machine"), BAND("Band");

    val label: String get() = t(en)

    /** Gear you'd normally only find in a gym. */
    val gymOnly: Boolean get() = this == CABLE || this == MACHINE
}

enum class MuscleGroup(private val en: String) {
    CHEST("Chest"), BACK("muscle|Back"), SHOULDERS("Shoulders"), ARMS("Arms"), LEGS("Legs"), CORE("Core"), FULL_BODY("Full body");

    val label: String get() = t(en)
}

data class ProgramExercise(
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val unit: RepUnit = RepUnit.NONE,
    val tag: String? = null,
) {
    val exercise: Exercise get() = Exercises.byId(exerciseId)
    val repsText: String get() = (if (repMin == repMax) "$repMin" else "$repMin–$repMax") + if (unit != RepUnit.NONE) " ${unit.label}" else ""
    val prescription: String get() = "$sets × $repsText"
    val displayName: String get() = exercise.name + (tag?.let { " — " + t(it) } ?: "")
}

data class WorkoutDay(
    val id: String,
    val number: Int,
    val name: String,
    val exercises: List<ProgramExercise>,
) {
    val title: String get() = t("Day %1${'$'}s — %2${'$'}s", number, t(name))
    val totalSets: Int get() = exercises.sumOf { it.sets }

    /** Rough session length: work time per set (x2 for single-side work) plus rest. */
    val estMinutes: Int
        get() {
            var sec = 0
            exercises.forEach { pe ->
                val sides = if (pe.unit.perSide) 2 else 1
                sec += pe.sets * (40 * sides + pe.exercise.restSec)
            }
            return (sec / 60.0).let { Math.round(it / 5.0).toInt() * 5 }.coerceAtLeast(20)
        }
}

object Program {
    const val TITLE = "4-Day Home Muscle Program"
    const val SUBTITLE = "Chest Priority • Legs • Back • Arms • Physique"
    const val REST_TITLE = "Day 3 — Rest"
    val REST_NOTE get() = t("Full recovery day. Light walking is fine.")
    const val NOTES = "Consistency first. Progress follows."

    val D1 = WorkoutDay(
        "D1", 1, "Chest Priority", listOf(
            ProgramExercise("pushup", 2, 8, 15, tag = "warm-up"),
            ProgramExercise("db_floor_press_1arm", 4, 8, 15, RepUnit.SIDE),
            ProgramExercise("pushup_feet_elevated", 3, 8, 15),
            ProgramExercise("db_squeeze_press", 3, 10, 15),
            ProgramExercise("pushup_close_grip", 2, 8, 15),
        )
    )

    val D2 = WorkoutDay(
        "D2", 2, "Legs + Posterior Chain", listOf(
            ProgramExercise("barbell_squat", 4, 8, 15),
            ProgramExercise("bulgarian_split_squat", 3, 8, 12, RepUnit.LEG),
            ProgramExercise("barbell_rdl", 3, 8, 15),
            ProgramExercise("sl_rdl", 2, 10, 12, RepUnit.LEG),
            ProgramExercise("sl_calf_raise", 4, 12, 20),
        )
    )

    val D4 = WorkoutDay(
        "D4", 4, "Upper + Back + Arms", listOf(
            ProgramExercise("pushup_feet_elevated", 3, 8, 15),
            ProgramExercise("db_floor_press_1arm", 3, 8, 15, RepUnit.SIDE),
            ProgramExercise("db_row_supported", 4, 8, 15, RepUnit.SIDE),
            ProgramExercise("db_reverse_fly", 3, 12, 20),
            ProgramExercise("db_curl", 3, 8, 15),
            ProgramExercise("hammer_curl", 3, 8, 15),
            ProgramExercise("oh_triceps_ext_1arm", 3, 10, 15, RepUnit.SIDE),
        )
    )

    val D5 = WorkoutDay(
        "D5", 5, "Legs + Delts/Arms", listOf(
            ProgramExercise("barbell_squat", 3, 10, 15),
            ProgramExercise("barbell_rdl", 3, 10, 15),
            ProgramExercise("reverse_lunge", 3, 10, 15, RepUnit.LEG),
            ProgramExercise("barbell_hip_thrust", 3, 12, 20),
            ProgramExercise("sl_calf_raise", 4, 15, 25),
            ProgramExercise("db_lateral_raise", 3, 12, 20),
        )
    )

    val days = listOf(D1, D2, D4, D5)

    /** A day of any program, past or present. Falls back to the original days before the store loads. */
    fun day(id: String?): WorkoutDay? = com.forge.app.data.ProgramStore.day(id) ?: days.firstOrNull { it.id == id }

    /** Order of the 7-day cycle as written in the PDF: Day 1, Day 2, Day 3 (rest), Day 4, Day 5, then rest. */
    val defaultCycle = listOf("D1", "D2", REST, "D4", "D5", REST, REST)
    const val REST = "R"
}

data class Exercise(
    val id: String,
    val name: String,
    val equip: Equip,
    val restSec: Int,
    val muscles: String,
    val setup: List<String>,
    val steps: List<String>,
    val cues: List<String>,
    val mistakes: List<String>,
    val homeTip: String? = null,
    val compound: Boolean = false,
    val group: MuscleGroup = MuscleGroup.FULL_BODY,
    /** Made by the user: no form guide, may be deleted. */
    val custom: Boolean = false,
    /** The English name, shown small under the Arabic one so gym talk still matches. */
    val en: String = name,
) {
    val loaded: Boolean get() = equip != Equip.BODYWEIGHT && equip != Equip.BAND
    val weightStep: Double get() = when (equip) {
        Equip.BARBELL, Equip.CABLE, Equip.MACHINE -> 2.5
        Equip.KETTLEBELL -> 4.0
        else -> 1.0
    }
    val hasGuide: Boolean get() = steps.isNotEmpty()
}

/*
 * Form guides. Compiled from coaching references (ACE, NASM, Motra, Garage Gym Reviews,
 * Muscle & Strength, Eric Cressey, StrengthLog and others) and condensed to what matters
 * under the bar.
 */
object Exercises {
    val all: List<Exercise> = listOf(
        Exercise(
            id = "pushup", name = t("Push-ups"), en = "Push-ups", equip = Equip.BODYWEIGHT, restSec = 75,
            muscles = t("Chest, front delts, triceps, core"),
            setup = listOf(
                t("Hands slightly wider than shoulders, fingers pointing forward."),
                t("Body in one straight line from head to heels. Squeeze glutes, brace abs."),
            ),
            steps = listOf(
                t("Lower under control until your chest is a fist from the floor."),
                t("Elbows travel back at about 45° to your torso, not out to the sides."),
                t("Press the floor away until your arms are straight. Keep the plank rigid."),
            ),
            cues = listOf(t("Screw your hands into the floor"), t("Ribs down, glutes tight"), t("Chest leads, hips follow")),
            mistakes = listOf(t("Hips sagging or piking up"), t("Elbows flared to 90°"), t("Half reps that stop short of the floor"), t("Head dropping toward the floor")),
            homeTip = t("On Day 1 these are your warm-up. Smooth reps, stop 2–3 reps before failure."),
        ),
        Exercise(
            id = "db_floor_press_1arm", name = t("1-arm Dumbbell Floor Press"), en = "1-arm Dumbbell Floor Press", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = t("Chest, triceps, front delts. Core resists twisting."),
            setup = listOf(
                t("Lie on your back, knees bent, feet flat."),
                t("Dumbbell in one hand over your shoulder, wrist stacked over the elbow."),
                t("Pull the shoulder blade down and back before the first rep."),
            ),
            steps = listOf(
                t("Lower until your upper arm touches the floor, elbow about 45° from your body."),
                t("Pause for a moment on the floor. No bounce."),
                t("Press to full lockout without shrugging. Finish all reps, then switch sides."),
            ),
            cues = listOf(t("Drive your heels into the floor"), t("Wrist straight over the forearm"), t("Don't let your torso roll toward the weight")),
            mistakes = listOf(t("Bouncing the elbow off the floor"), t("Flaring the elbow wide"), t("Arching the lower back"), t("Twisting the torso")),
        ),
        Exercise(
            id = "pushup_feet_elevated", name = t("Feet-Elevated Push-ups"), en = "Feet-Elevated Push-ups", equip = Equip.BODYWEIGHT, restSec = 90,
            muscles = t("Upper chest, front delts, triceps"),
            setup = listOf(
                t("Feet on a chair, bed or step. Hands on the floor slightly wider than shoulders."),
                t("Higher feet = harder and more upper chest."),
            ),
            steps = listOf(
                t("Hold a rigid plank and lower until your chest nearly touches the floor."),
                t("Keep elbows tucked at about 45°."),
                t("Press back up to straight arms."),
            ),
            cues = listOf(t("Squeeze glutes so the hips don't sag"), t("Shoulders down, away from your ears"), t("Chest to the floor, not your face")),
            mistakes = listOf(t("Hips piking up to shorten the range"), t("Elbows flaring"), t("Reaching for the floor with your head")),
            homeTip = t("Top of the rep range on every set? Raise your feet higher or wear a loaded backpack."),
        ),
        Exercise(
            id = "db_squeeze_press", name = t("Dumbbell Squeeze Press"), en = "Dumbbell Squeeze Press", equip = Equip.DUMBBELL, restSec = 75,
            muscles = t("Chest (inner and mid), triceps"),
            setup = listOf(
                t("Lie on the floor or a bench with a dumbbell in each hand, palms facing each other."),
                t("Press the dumbbells together over your chest."),
            ),
            steps = listOf(
                t("Squeeze the dumbbells hard into each other."),
                t("Keep squeezing and lower them to your chest. On the floor, stop when your upper arms touch."),
                t("Press back up while holding the squeeze. Stop just short of lockout to keep tension on the chest."),
            ),
            cues = listOf(t("The squeeze never stops"), t("Exhale on the way up"), t("Use less weight than your normal press")),
            mistakes = listOf(t("Letting the dumbbells drift apart"), t("Going too heavy"), t("Arching the lower back"), t("Locking the elbows")),
        ),
        Exercise(
            id = "pushup_close_grip", name = t("Close-Grip Push-ups"), en = "Close-Grip Push-ups", equip = Equip.BODYWEIGHT, restSec = 75,
            muscles = t("Triceps, inner chest, front delts"),
            setup = listOf(
                t("Hands just inside shoulder width, under your shoulders or slightly narrower."),
                t("Hold a straight plank."),
            ),
            steps = listOf(
                t("Lower with your elbows close, brushing your ribs."),
                t("Chest to just above your hands."),
                t("Press back up to full lockout."),
            ),
            cues = listOf(t("Elbows back, not out"), t("Head to heels in one line")),
            mistakes = listOf(t("Hands so close the wrists hurt (they don't need to touch)"), t("Elbows flaring"), t("Hips sagging")),
            homeTip = t("If you fall under 8 reps, finish the set from your knees."),
        ),
        Exercise(
            id = "barbell_squat", name = t("Barbell Squat"), en = "Barbell Squat", equip = Equip.BARBELL, restSec = 150, compound = true,
            muscles = t("Quads, glutes, adductors, lower back"),
            setup = listOf(
                t("Bar on your upper traps, not your neck. Hands just outside the shoulders."),
                t("Feet about shoulder-width, toes turned out 15–30°."),
                t("Big breath into your belly and brace before every rep."),
            ),
            steps = listOf(
                t("Break at the hips and knees together and sit down between your heels."),
                t("Knees track over your toes. Go as deep as you can with a neutral back — thighs at least parallel."),
                t("Drive up through the whole foot. Chest and hips rise together."),
            ),
            cues = listOf(t("Brace like you're about to take a punch"), t("Knees out over the toes"), t("Whole foot planted, heels down")),
            mistakes = listOf(t("Knees caving in"), t("Heels lifting"), t("Hips shooting up first"), t("Losing the brace at the bottom"), t("Cutting depth")),
            homeTip = t("No rack? Only use a weight you can safely get onto your back and off again."),
        ),
        Exercise(
            id = "bulgarian_split_squat", name = t("Bulgarian Split Squat"), en = "Bulgarian Split Squat", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = t("Quads, glutes, adductors"),
            setup = listOf(
                t("Rear foot laces-down on a chair or bench about knee height."),
                t("Feet on two train tracks, not a tightrope — that fixes most wobbling."),
                t("Front foot far enough forward that the front heel stays down at the bottom."),
            ),
            steps = listOf(
                t("Lower straight down until the rear knee nearly touches the floor."),
                t("Keep most of your weight on the front foot."),
                t("Drive through the front heel and midfoot to stand. Finish all reps, then switch legs."),
            ),
            cues = listOf(t("The back foot is a kickstand, the front leg does the work"), t("Slight forward lean = more glutes, upright = more quads")),
            mistakes = listOf(t("Feet in one line (balance problems)"), t("Rear foot too high"), t("Pushing off the back leg"), t("Front heel lifting")),
            homeTip = t("Losing balance? Move the front foot further from the bench."),
        ),
        Exercise(
            id = "barbell_rdl", name = t("Barbell Romanian Deadlift"), en = "Barbell Romanian Deadlift", equip = Equip.BARBELL, restSec = 120, compound = true,
            muscles = t("Hamstrings, glutes, spinal erectors"),
            setup = listOf(
                t("Stand tall holding the bar at hip height, hands just outside your thighs."),
                t("Feet hip-width. Soft bend in the knees — it stays fixed the whole rep."),
            ),
            steps = listOf(
                t("Push your hips back as if shutting a car door with your backside."),
                t("The bar slides down your thighs and past the knees, staying close."),
                t("Stop when you feel a strong hamstring stretch with a flat back, usually around mid-shin."),
                t("Drive the hips forward to stand. Squeeze the glutes at the top without leaning back."),
            ),
            cues = listOf(t("Hips back, not down"), t("Keep the bar against your legs"), t("Long neck, flat back")),
            mistakes = listOf(t("Turning it into a squat"), t("Rounding the back at the bottom"), t("Bar drifting away from the legs"), t("Leaning back at lockout")),
        ),
        Exercise(
            id = "sl_rdl", name = t("Single-Leg Romanian Deadlift"), en = "Single-Leg Romanian Deadlift", equip = Equip.DUMBBELL, restSec = 75,
            muscles = t("Hamstrings, glutes, hip stabilisers"),
            setup = listOf(
                t("Dumbbell in the hand opposite the working leg."),
                t("Stand on the working leg with a soft knee."),
                t("Hold a wall or chair with the free hand if balance limits you. That is allowed."),
            ),
            steps = listOf(
                t("Hinge at the hip and let the free leg rise behind you, in line with your torso."),
                t("Lower until you feel the hamstring stretch. Hips stay square to the floor."),
                t("Drive through the standing heel to return to tall."),
            ),
            cues = listOf(t("Both hip bones point at the floor"), t("Reach back with the free heel"), t("Slow on the way down")),
            mistakes = listOf(t("Free hip opening up / torso rotating"), t("Rounding the back"), t("Locking the standing knee"), t("Rushing the descent")),
        ),
        Exercise(
            id = "sl_calf_raise", name = t("Single-Leg Calf Raise"), en = "Single-Leg Calf Raise", equip = Equip.BODYWEIGHT, restSec = 60,
            muscles = t("Calves"),
            setup = listOf(
                t("Ball of one foot on the edge of a step, heel hanging."),
                t("Hold a wall for balance. Hold a dumbbell in the other hand once bodyweight gets easy."),
            ),
            steps = listOf(
                t("Lower the heel as far as it goes and pause 1–2 seconds in the stretch."),
                t("Rise as high as you can onto the big toe and pause 1 second."),
                t("Finish the reps on one leg, then switch."),
            ),
            cues = listOf(t("The bottom pause kills the bounce"), t("Full height at the top"), t("Knee straight, not locked")),
            mistakes = listOf(t("Bouncing out of the stretch"), t("Half reps"), t("Rolling onto the outside of the foot")),
        ),
        Exercise(
            id = "db_row_supported", name = t("1-arm Supported Dumbbell Row"), en = "1-arm Supported Dumbbell Row", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = t("Lats, mid-back, rear delts, biceps"),
            setup = listOf(
                t("One hand and the same-side knee on a bench, or hand on a chair with a staggered stance."),
                t("Back flat, roughly parallel to the floor. Dumbbell hangs under the shoulder."),
            ),
            steps = listOf(
                t("Let the shoulder stretch down at the bottom."),
                t("Pull the dumbbell toward your hip with the elbow close to your body."),
                t("Squeeze the shoulder blade back for a beat, then lower to a full stretch. Finish all reps, switch sides."),
            ),
            cues = listOf(t("Pull to the hip, not the chest"), t("Drive the elbow back"), t("Torso stays still")),
            mistakes = listOf(t("Jerking the torso to heave the weight"), t("Shrugging toward the ear"), t("Skipping the stretch at the bottom"), t("Rounded back")),
        ),
        Exercise(
            id = "db_reverse_fly", name = t("Dumbbell Reverse Fly"), en = "Dumbbell Reverse Fly", equip = Equip.DUMBBELL, restSec = 60,
            muscles = t("Rear delts, upper back"),
            setup = listOf(
                t("Hinge forward to 45° or lower with a flat back, or lie chest-down on an incline bench."),
                t("Light dumbbells below your chest, palms facing in, soft bend in the elbows."),
            ),
            steps = listOf(
                t("Raise the arms out to the sides in a wide arc until they're in line with your body."),
                t("Pause and squeeze for a moment."),
                t("Lower slowly."),
            ),
            cues = listOf(t("Lead with the elbows"), t("Shoulders down, away from the ears"), t("Light weight, strict reps")),
            mistakes = listOf(t("Swinging the torso"), t("Shrugging — traps take over"), t("Going too heavy"), t("Back rounding late in the set")),
        ),
        Exercise(
            id = "db_curl", name = t("Dumbbell Curl"), en = "Dumbbell Curl", equip = Equip.DUMBBELL, restSec = 60,
            muscles = t("Biceps"),
            setup = listOf(t("Stand tall, dumbbells at your sides, palms forward.")),
            steps = listOf(
                t("Curl up by bending at the elbow only."),
                t("Squeeze at the top."),
                t("Lower over 2–3 seconds to a full stretch."),
            ),
            cues = listOf(t("Elbows pinned to your sides"), t("Full stretch at the bottom"), t("No body swing")),
            mistakes = listOf(t("Leaning back to swing the weight"), t("Elbows drifting forward"), t("Half reps"), t("Dropping the weight on the way down")),
        ),
        Exercise(
            id = "hammer_curl", name = t("Hammer Curl"), en = "Hammer Curl", equip = Equip.DUMBBELL, restSec = 60,
            muscles = t("Brachialis, forearms, biceps"),
            setup = listOf(t("Dumbbells at your sides, palms facing each other.")),
            steps = listOf(
                t("Curl up keeping the neutral grip — thumbs up the whole time."),
                t("Squeeze at the top."),
                t("Lower slowly to a full stretch."),
            ),
            cues = listOf(t("Thumbs to shoulders"), t("Elbows stay at your sides"), t("Control the negative")),
            mistakes = listOf(t("Swinging"), t("Rotating the wrist"), t("Elbows moving forward")),
        ),
        Exercise(
            id = "oh_triceps_ext_1arm", name = t("1-arm Overhead Triceps Extension"), en = "1-arm Overhead Triceps Extension", equip = Equip.DUMBBELL, restSec = 60,
            muscles = t("Triceps, especially the long head"),
            setup = listOf(
                t("Seated or standing, dumbbell overhead, elbow pointing up and slightly forward."),
                t("Support the working elbow with your free hand if it helps."),
            ),
            steps = listOf(
                t("Keep the upper arm still and lower the dumbbell behind your head until the elbow is at least 90°."),
                t("Extend back up to just before lockout."),
                t("Finish all reps, then switch sides."),
            ),
            cues = listOf(t("Only the elbow moves"), t("Elbow stays close to your head"), t("Slow on the way down")),
            mistakes = listOf(t("Elbow flaring out"), t("Upper arm moving"), t("Arching the lower back"), t("Slamming into lockout")),
            homeTip = t("Shoulder pain overhead? Stop and swap for a lighter weight or a smaller range."),
        ),
        Exercise(
            id = "reverse_lunge", name = t("Reverse Lunges"), en = "Reverse Lunges", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = t("Quads, glutes"),
            setup = listOf(t("Stand tall, feet hip-width, dumbbells at your sides (or bodyweight).")),
            steps = listOf(
                t("Step one foot back and lower until the back knee nearly touches the floor."),
                t("Front shin stays close to vertical, weight in the front heel and midfoot."),
                t("Push through the front foot to return to standing. Count reps per leg."),
            ),
            cues = listOf(t("Step back far enough to hit 90° at both knees"), t("Torso tall"), t("Front knee tracks over the toes")),
            mistakes = listOf(t("Stepping too short"), t("Front knee caving in"), t("Pushing off the back foot"), t("Torso collapsing forward")),
        ),
        Exercise(
            id = "barbell_hip_thrust", name = t("Barbell Hip Thrust"), en = "Barbell Hip Thrust", equip = Equip.BARBELL, restSec = 120, compound = true,
            muscles = t("Glutes, hamstrings"),
            setup = listOf(
                t("Upper back (bottom of the shoulder blades) against a couch or bench edge."),
                t("Bar over the hip crease with a pad or folded towel."),
                t("Feet flat about shoulder-width, placed so your shins are vertical at the top."),
            ),
            steps = listOf(
                t("Tuck the chin and keep the ribs down."),
                t("Drive through the heels and push the hips up until your torso is parallel to the floor."),
                t("Squeeze the glutes hard at the top for 1 second, then lower under control."),
            ),
            cues = listOf(t("Chin tucked, eyes forward — not at the ceiling"), t("Ribs down"), t("Shins vertical at the top")),
            mistakes = listOf(t("Arching the lower back instead of extending the hips"), t("Head thrown back"), t("Feet too far or too close"), t("Pushing through the toes")),
        ),
        Exercise(
            id = "db_lateral_raise", name = t("Dumbbell Lateral Raise"), en = "Dumbbell Lateral Raise", equip = Equip.DUMBBELL, restSec = 60,
            muscles = t("Side delts"),
            setup = listOf(t("Stand tall, dumbbells at your sides, slight forward lean, soft bend in the elbows.")),
            steps = listOf(
                t("Raise the arms out to the sides to about shoulder height, leading with the elbows."),
                t("Pause briefly at the top."),
                t("Lower over 2–3 seconds."),
            ),
            cues = listOf(t("Push the weights out, not up"), t("Shoulders down — no shrug"), t("Light weight, strict reps")),
            mistakes = listOf(t("Swinging"), t("Shrugging with the traps"), t("Going too heavy"), t("Raising far above shoulder height")),
        ),
    )

    /** The 18 original guides plus the library: 80 built-in exercises. */
    val builtIn: List<Exercise> by lazy { all.map { it.copy(group = originalGroups[it.id] ?: it.group) } + ExerciseLibrary.more }
    private val builtInMap by lazy { builtIn.associateBy { it.id } }

    /** The user's own exercises, kept in sync with the database by [com.forge.app.data.ProgramStore]. */
    @Volatile var custom: Map<String, Exercise> = emptyMap()

    /** Built-in first, then custom. An id nothing knows (an exercise deleted long ago) still resolves. */
    fun byId(id: String): Exercise = builtInMap[id] ?: custom[id] ?: Exercise(
        id = id, name = t("Removed exercise"), en = "Removed exercise", equip = Equip.BODYWEIGHT, restSec = 90, muscles = "",
        setup = emptyList(), steps = emptyList(), cues = emptyList(), mistakes = emptyList(), custom = true,
    )

    fun isBuiltIn(id: String) = id in builtInMap

    /** Custom exercises the user deleted: still named in history, gone from the picker. */
    @Volatile var retired: Set<String> = emptySet()

    /** Everything the picker offers: built-in plus custom, by name. */
    fun everything(): List<Exercise> = (builtIn + custom.values.filter { it.id !in retired }).sortedBy { it.name.lowercase() }

    private val originalGroups = mapOf(
        "pushup" to MuscleGroup.CHEST, "db_floor_press_1arm" to MuscleGroup.CHEST, "pushup_feet_elevated" to MuscleGroup.CHEST,
        "db_squeeze_press" to MuscleGroup.CHEST, "pushup_close_grip" to MuscleGroup.ARMS, "barbell_squat" to MuscleGroup.LEGS,
        "bulgarian_split_squat" to MuscleGroup.LEGS, "barbell_rdl" to MuscleGroup.LEGS, "sl_rdl" to MuscleGroup.LEGS,
        "sl_calf_raise" to MuscleGroup.LEGS, "db_row_supported" to MuscleGroup.BACK, "db_reverse_fly" to MuscleGroup.SHOULDERS,
        "db_curl" to MuscleGroup.ARMS, "hammer_curl" to MuscleGroup.ARMS, "oh_triceps_ext_1arm" to MuscleGroup.ARMS,
        "reverse_lunge" to MuscleGroup.LEGS, "barbell_hip_thrust" to MuscleGroup.LEGS, "db_lateral_raise" to MuscleGroup.SHOULDERS,
    )

    val EFFORT_RULE get() = t("Stop each set 1–2 reps before failure. The last reps should be slow and hard.")
    val PROGRESSION_RULE get() =
        t("Double progression: stay at a weight until you hit the top of the rep range on every set, then add weight and work back up.")

    fun demoUrl(e: Exercise): String =
        "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("${e.en} proper form", "UTF-8")
}
