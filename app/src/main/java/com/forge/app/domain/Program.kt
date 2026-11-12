package com.forge.app.domain

/*
 * The training program, copied exactly from
 * "4_Day_Home_Muscle_Program_Redesigned_v2.pdf". Do not edit sets, reps or order.
 */

enum class RepUnit(val label: String) {
    NONE(""), SIDE("/ side"), LEG("/ leg"), SEC("s"), SEC_SIDE("s / side");

    /** Counted in seconds rather than reps. */
    val timed: Boolean get() = this == SEC || this == SEC_SIDE
    /** Each side or leg is done separately. */
    val perSide: Boolean get() = this != NONE && this != SEC
}

enum class Equip(val label: String) {
    BODYWEIGHT("Bodyweight"), DUMBBELL("Dumbbell"), BARBELL("Barbell"), KETTLEBELL("Kettlebell"),
    CABLE("Cable"), MACHINE("Machine"), BAND("Band");

    /** Gear you'd normally only find in a gym. */
    val gymOnly: Boolean get() = this == CABLE || this == MACHINE
}

enum class MuscleGroup(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), ARMS("Arms"), LEGS("Legs"), CORE("Core"), FULL_BODY("Full body")
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
    val displayName: String get() = exercise.name + (tag?.let { " — $it" } ?: "")
}

data class WorkoutDay(
    val id: String,
    val number: Int,
    val name: String,
    val exercises: List<ProgramExercise>,
) {
    val title: String get() = "Day $number — $name"
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
    const val REST_NOTE = "Full recovery day. Light walking is fine."
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
            id = "pushup", name = "Push-ups", equip = Equip.BODYWEIGHT, restSec = 75,
            muscles = "Chest, front delts, triceps, core",
            setup = listOf(
                "Hands slightly wider than shoulders, fingers pointing forward.",
                "Body in one straight line from head to heels. Squeeze glutes, brace abs.",
            ),
            steps = listOf(
                "Lower under control until your chest is a fist from the floor.",
                "Elbows travel back at about 45° to your torso, not out to the sides.",
                "Press the floor away until your arms are straight. Keep the plank rigid.",
            ),
            cues = listOf("Screw your hands into the floor", "Ribs down, glutes tight", "Chest leads, hips follow"),
            mistakes = listOf("Hips sagging or piking up", "Elbows flared to 90°", "Half reps that stop short of the floor", "Head dropping toward the floor"),
            homeTip = "On Day 1 these are your warm-up. Smooth reps, stop 2–3 reps before failure.",
        ),
        Exercise(
            id = "db_floor_press_1arm", name = "1-arm Dumbbell Floor Press", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = "Chest, triceps, front delts. Core resists twisting.",
            setup = listOf(
                "Lie on your back, knees bent, feet flat.",
                "Dumbbell in one hand over your shoulder, wrist stacked over the elbow.",
                "Pull the shoulder blade down and back before the first rep.",
            ),
            steps = listOf(
                "Lower until your upper arm touches the floor, elbow about 45° from your body.",
                "Pause for a moment on the floor. No bounce.",
                "Press to full lockout without shrugging. Finish all reps, then switch sides.",
            ),
            cues = listOf("Drive your heels into the floor", "Wrist straight over the forearm", "Don't let your torso roll toward the weight"),
            mistakes = listOf("Bouncing the elbow off the floor", "Flaring the elbow wide", "Arching the lower back", "Twisting the torso"),
        ),
        Exercise(
            id = "pushup_feet_elevated", name = "Feet-Elevated Push-ups", equip = Equip.BODYWEIGHT, restSec = 90,
            muscles = "Upper chest, front delts, triceps",
            setup = listOf(
                "Feet on a chair, bed or step. Hands on the floor slightly wider than shoulders.",
                "Higher feet = harder and more upper chest.",
            ),
            steps = listOf(
                "Hold a rigid plank and lower until your chest nearly touches the floor.",
                "Keep elbows tucked at about 45°.",
                "Press back up to straight arms.",
            ),
            cues = listOf("Squeeze glutes so the hips don't sag", "Shoulders down, away from your ears", "Chest to the floor, not your face"),
            mistakes = listOf("Hips piking up to shorten the range", "Elbows flaring", "Reaching for the floor with your head"),
            homeTip = "Top of the rep range on every set? Raise your feet higher or wear a loaded backpack.",
        ),
        Exercise(
            id = "db_squeeze_press", name = "Dumbbell Squeeze Press", equip = Equip.DUMBBELL, restSec = 75,
            muscles = "Chest (inner and mid), triceps",
            setup = listOf(
                "Lie on the floor or a bench with a dumbbell in each hand, palms facing each other.",
                "Press the dumbbells together over your chest.",
            ),
            steps = listOf(
                "Squeeze the dumbbells hard into each other.",
                "Keep squeezing and lower them to your chest. On the floor, stop when your upper arms touch.",
                "Press back up while holding the squeeze. Stop just short of lockout to keep tension on the chest.",
            ),
            cues = listOf("The squeeze never stops", "Exhale on the way up", "Use less weight than your normal press"),
            mistakes = listOf("Letting the dumbbells drift apart", "Going too heavy", "Arching the lower back", "Locking the elbows"),
        ),
        Exercise(
            id = "pushup_close_grip", name = "Close-Grip Push-ups", equip = Equip.BODYWEIGHT, restSec = 75,
            muscles = "Triceps, inner chest, front delts",
            setup = listOf(
                "Hands just inside shoulder width, under your shoulders or slightly narrower.",
                "Hold a straight plank.",
            ),
            steps = listOf(
                "Lower with your elbows close, brushing your ribs.",
                "Chest to just above your hands.",
                "Press back up to full lockout.",
            ),
            cues = listOf("Elbows back, not out", "Head to heels in one line"),
            mistakes = listOf("Hands so close the wrists hurt (they don't need to touch)", "Elbows flaring", "Hips sagging"),
            homeTip = "If you fall under 8 reps, finish the set from your knees.",
        ),
        Exercise(
            id = "barbell_squat", name = "Barbell Squat", equip = Equip.BARBELL, restSec = 150, compound = true,
            muscles = "Quads, glutes, adductors, lower back",
            setup = listOf(
                "Bar on your upper traps, not your neck. Hands just outside the shoulders.",
                "Feet about shoulder-width, toes turned out 15–30°.",
                "Big breath into your belly and brace before every rep.",
            ),
            steps = listOf(
                "Break at the hips and knees together and sit down between your heels.",
                "Knees track over your toes. Go as deep as you can with a neutral back — thighs at least parallel.",
                "Drive up through the whole foot. Chest and hips rise together.",
            ),
            cues = listOf("Brace like you're about to take a punch", "Knees out over the toes", "Whole foot planted, heels down"),
            mistakes = listOf("Knees caving in", "Heels lifting", "Hips shooting up first", "Losing the brace at the bottom", "Cutting depth"),
            homeTip = "No rack? Only use a weight you can safely get onto your back and off again.",
        ),
        Exercise(
            id = "bulgarian_split_squat", name = "Bulgarian Split Squat", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = "Quads, glutes, adductors",
            setup = listOf(
                "Rear foot laces-down on a chair or bench about knee height.",
                "Feet on two train tracks, not a tightrope — that fixes most wobbling.",
                "Front foot far enough forward that the front heel stays down at the bottom.",
            ),
            steps = listOf(
                "Lower straight down until the rear knee nearly touches the floor.",
                "Keep most of your weight on the front foot.",
                "Drive through the front heel and midfoot to stand. Finish all reps, then switch legs.",
            ),
            cues = listOf("The back foot is a kickstand, the front leg does the work", "Slight forward lean = more glutes, upright = more quads"),
            mistakes = listOf("Feet in one line (balance problems)", "Rear foot too high", "Pushing off the back leg", "Front heel lifting"),
            homeTip = "Losing balance? Move the front foot further from the bench.",
        ),
        Exercise(
            id = "barbell_rdl", name = "Barbell Romanian Deadlift", equip = Equip.BARBELL, restSec = 120, compound = true,
            muscles = "Hamstrings, glutes, spinal erectors",
            setup = listOf(
                "Stand tall holding the bar at hip height, hands just outside your thighs.",
                "Feet hip-width. Soft bend in the knees — it stays fixed the whole rep.",
            ),
            steps = listOf(
                "Push your hips back as if shutting a car door with your backside.",
                "The bar slides down your thighs and past the knees, staying close.",
                "Stop when you feel a strong hamstring stretch with a flat back, usually around mid-shin.",
                "Drive the hips forward to stand. Squeeze the glutes at the top without leaning back.",
            ),
            cues = listOf("Hips back, not down", "Keep the bar against your legs", "Long neck, flat back"),
            mistakes = listOf("Turning it into a squat", "Rounding the back at the bottom", "Bar drifting away from the legs", "Leaning back at lockout"),
        ),
        Exercise(
            id = "sl_rdl", name = "Single-Leg Romanian Deadlift", equip = Equip.DUMBBELL, restSec = 75,
            muscles = "Hamstrings, glutes, hip stabilisers",
            setup = listOf(
                "Dumbbell in the hand opposite the working leg.",
                "Stand on the working leg with a soft knee.",
                "Hold a wall or chair with the free hand if balance limits you. That is allowed.",
            ),
            steps = listOf(
                "Hinge at the hip and let the free leg rise behind you, in line with your torso.",
                "Lower until you feel the hamstring stretch. Hips stay square to the floor.",
                "Drive through the standing heel to return to tall.",
            ),
            cues = listOf("Both hip bones point at the floor", "Reach back with the free heel", "Slow on the way down"),
            mistakes = listOf("Free hip opening up / torso rotating", "Rounding the back", "Locking the standing knee", "Rushing the descent"),
        ),
        Exercise(
            id = "sl_calf_raise", name = "Single-Leg Calf Raise", equip = Equip.BODYWEIGHT, restSec = 60,
            muscles = "Calves",
            setup = listOf(
                "Ball of one foot on the edge of a step, heel hanging.",
                "Hold a wall for balance. Hold a dumbbell in the other hand once bodyweight gets easy.",
            ),
            steps = listOf(
                "Lower the heel as far as it goes and pause 1–2 seconds in the stretch.",
                "Rise as high as you can onto the big toe and pause 1 second.",
                "Finish the reps on one leg, then switch.",
            ),
            cues = listOf("The bottom pause kills the bounce", "Full height at the top", "Knee straight, not locked"),
            mistakes = listOf("Bouncing out of the stretch", "Half reps", "Rolling onto the outside of the foot"),
        ),
        Exercise(
            id = "db_row_supported", name = "1-arm Supported Dumbbell Row", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = "Lats, mid-back, rear delts, biceps",
            setup = listOf(
                "One hand and the same-side knee on a bench, or hand on a chair with a staggered stance.",
                "Back flat, roughly parallel to the floor. Dumbbell hangs under the shoulder.",
            ),
            steps = listOf(
                "Let the shoulder stretch down at the bottom.",
                "Pull the dumbbell toward your hip with the elbow close to your body.",
                "Squeeze the shoulder blade back for a beat, then lower to a full stretch. Finish all reps, switch sides.",
            ),
            cues = listOf("Pull to the hip, not the chest", "Drive the elbow back", "Torso stays still"),
            mistakes = listOf("Jerking the torso to heave the weight", "Shrugging toward the ear", "Skipping the stretch at the bottom", "Rounded back"),
        ),
        Exercise(
            id = "db_reverse_fly", name = "Dumbbell Reverse Fly", equip = Equip.DUMBBELL, restSec = 60,
            muscles = "Rear delts, upper back",
            setup = listOf(
                "Hinge forward to 45° or lower with a flat back, or lie chest-down on an incline bench.",
                "Light dumbbells below your chest, palms facing in, soft bend in the elbows.",
            ),
            steps = listOf(
                "Raise the arms out to the sides in a wide arc until they're in line with your body.",
                "Pause and squeeze for a moment.",
                "Lower slowly.",
            ),
            cues = listOf("Lead with the elbows", "Shoulders down, away from the ears", "Light weight, strict reps"),
            mistakes = listOf("Swinging the torso", "Shrugging — traps take over", "Going too heavy", "Back rounding late in the set"),
        ),
        Exercise(
            id = "db_curl", name = "Dumbbell Curl", equip = Equip.DUMBBELL, restSec = 60,
            muscles = "Biceps",
            setup = listOf("Stand tall, dumbbells at your sides, palms forward."),
            steps = listOf(
                "Curl up by bending at the elbow only.",
                "Squeeze at the top.",
                "Lower over 2–3 seconds to a full stretch.",
            ),
            cues = listOf("Elbows pinned to your sides", "Full stretch at the bottom", "No body swing"),
            mistakes = listOf("Leaning back to swing the weight", "Elbows drifting forward", "Half reps", "Dropping the weight on the way down"),
        ),
        Exercise(
            id = "hammer_curl", name = "Hammer Curl", equip = Equip.DUMBBELL, restSec = 60,
            muscles = "Brachialis, forearms, biceps",
            setup = listOf("Dumbbells at your sides, palms facing each other."),
            steps = listOf(
                "Curl up keeping the neutral grip — thumbs up the whole time.",
                "Squeeze at the top.",
                "Lower slowly to a full stretch.",
            ),
            cues = listOf("Thumbs to shoulders", "Elbows stay at your sides", "Control the negative"),
            mistakes = listOf("Swinging", "Rotating the wrist", "Elbows moving forward"),
        ),
        Exercise(
            id = "oh_triceps_ext_1arm", name = "1-arm Overhead Triceps Extension", equip = Equip.DUMBBELL, restSec = 60,
            muscles = "Triceps, especially the long head",
            setup = listOf(
                "Seated or standing, dumbbell overhead, elbow pointing up and slightly forward.",
                "Support the working elbow with your free hand if it helps.",
            ),
            steps = listOf(
                "Keep the upper arm still and lower the dumbbell behind your head until the elbow is at least 90°.",
                "Extend back up to just before lockout.",
                "Finish all reps, then switch sides.",
            ),
            cues = listOf("Only the elbow moves", "Elbow stays close to your head", "Slow on the way down"),
            mistakes = listOf("Elbow flaring out", "Upper arm moving", "Arching the lower back", "Slamming into lockout"),
            homeTip = "Shoulder pain overhead? Stop and swap for a lighter weight or a smaller range.",
        ),
        Exercise(
            id = "reverse_lunge", name = "Reverse Lunges", equip = Equip.DUMBBELL, restSec = 90, compound = true,
            muscles = "Quads, glutes",
            setup = listOf("Stand tall, feet hip-width, dumbbells at your sides (or bodyweight)."),
            steps = listOf(
                "Step one foot back and lower until the back knee nearly touches the floor.",
                "Front shin stays close to vertical, weight in the front heel and midfoot.",
                "Push through the front foot to return to standing. Count reps per leg.",
            ),
            cues = listOf("Step back far enough to hit 90° at both knees", "Torso tall", "Front knee tracks over the toes"),
            mistakes = listOf("Stepping too short", "Front knee caving in", "Pushing off the back foot", "Torso collapsing forward"),
        ),
        Exercise(
            id = "barbell_hip_thrust", name = "Barbell Hip Thrust", equip = Equip.BARBELL, restSec = 120, compound = true,
            muscles = "Glutes, hamstrings",
            setup = listOf(
                "Upper back (bottom of the shoulder blades) against a couch or bench edge.",
                "Bar over the hip crease with a pad or folded towel.",
                "Feet flat about shoulder-width, placed so your shins are vertical at the top.",
            ),
            steps = listOf(
                "Tuck the chin and keep the ribs down.",
                "Drive through the heels and push the hips up until your torso is parallel to the floor.",
                "Squeeze the glutes hard at the top for 1 second, then lower under control.",
            ),
            cues = listOf("Chin tucked, eyes forward — not at the ceiling", "Ribs down", "Shins vertical at the top"),
            mistakes = listOf("Arching the lower back instead of extending the hips", "Head thrown back", "Feet too far or too close", "Pushing through the toes"),
        ),
        Exercise(
            id = "db_lateral_raise", name = "Dumbbell Lateral Raise", equip = Equip.DUMBBELL, restSec = 60,
            muscles = "Side delts",
            setup = listOf("Stand tall, dumbbells at your sides, slight forward lean, soft bend in the elbows."),
            steps = listOf(
                "Raise the arms out to the sides to about shoulder height, leading with the elbows.",
                "Pause briefly at the top.",
                "Lower over 2–3 seconds.",
            ),
            cues = listOf("Push the weights out, not up", "Shoulders down — no shrug", "Light weight, strict reps"),
            mistakes = listOf("Swinging", "Shrugging with the traps", "Going too heavy", "Raising far above shoulder height"),
        ),
    )

    /** The 18 original guides plus the library: 80 built-in exercises. */
    val builtIn: List<Exercise> by lazy { all.map { it.copy(group = originalGroups[it.id] ?: it.group) } + ExerciseLibrary.more }
    private val builtInMap by lazy { builtIn.associateBy { it.id } }

    /** The user's own exercises, kept in sync with the database by [com.forge.app.data.ProgramStore]. */
    @Volatile var custom: Map<String, Exercise> = emptyMap()

    /** Built-in first, then custom. An id nothing knows (an exercise deleted long ago) still resolves. */
    fun byId(id: String): Exercise = builtInMap[id] ?: custom[id] ?: Exercise(
        id = id, name = "Removed exercise", equip = Equip.BODYWEIGHT, restSec = 90, muscles = "",
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

    const val EFFORT_RULE = "Stop each set 1–2 reps before failure. The last reps should be slow and hard."
    const val PROGRESSION_RULE =
        "Double progression: stay at a weight until you hit the top of the rep range on every set, then add weight and work back up."

    fun demoUrl(e: Exercise): String =
        "https://www.youtube.com/results?search_query=" + java.net.URLEncoder.encode("${e.name} proper form", "UTF-8")
}
