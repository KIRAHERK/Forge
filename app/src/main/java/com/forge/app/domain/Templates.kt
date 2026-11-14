package com.forge.app.domain

import com.forge.app.i18n.t

/** A ready-made program the user can start from and then edit. */
data class TemplateDay(val name: String, val exercises: List<ProgramExercise>)

data class ProgramTemplate(
    val id: String,
    val name: String,
    val note: String,
    val equipment: String,
    val days: List<TemplateDay>,
    /** Monday..Sunday: index into [days], or null for rest. */
    val week: List<Int?>,
    val credit: String? = null,
) {
    val daysPerWeek: Int get() = week.count { it != null }
}

object Templates {
    private fun pe(id: String, sets: Int, min: Int, max: Int, unit: RepUnit = RepUnit.NONE, tag: String? = null) =
        ProgramExercise(id, sets, min, max, unit, tag)

    /** The original program, credited. Day ids D1/D2/D4/D5 are kept for it so old history matches. */
    val forge = ProgramTemplate(
        id = "forge-4day",
        name = t(Program.TITLE),
        note = t("Chest priority, legs, back and arms. The program Forge started with."),
        equipment = t("Dumbbells, barbell, bench"),
        days = listOf(Program.D1, Program.D2, Program.D4, Program.D5).map { TemplateDay(it.name, it.exercises) },
        week = listOf(0, 1, null, 2, 3, null, null),
        credit = t("by Hussein Al Herk"),
    )

    val fullBodyGym = ProgramTemplate(
        id = "full-body-gym",
        name = t("Full Body 3-Day"),
        note = t("Three whole-body sessions a week. A solid start in a gym."),
        equipment = t("Gym"),
        days = listOf(
            TemplateDay(t("Full Body A"), listOf(
                pe("barbell_squat", 3, 6, 10), pe("bb_bench_press", 3, 6, 10), pe("bb_row", 3, 8, 12),
                pe("leg_curl", 2, 10, 15), pe("plank", 3, 30, 45, RepUnit.SEC),
            )),
            TemplateDay(t("Full Body B"), listOf(
                pe("deadlift", 3, 5, 8), pe("incline_db_press", 3, 8, 12), pe("lat_pulldown", 3, 8, 12),
                pe("walking_lunge", 2, 10, 12, RepUnit.LEG), pe("face_pull", 2, 12, 20),
            )),
            TemplateDay(t("Full Body C"), listOf(
                pe("leg_press", 3, 10, 15), pe("db_bench_press", 3, 8, 12), pe("seated_cable_row", 3, 8, 12),
                pe("db_lateral_raise", 3, 12, 20), pe("bb_curl", 2, 8, 12), pe("triceps_pushdown", 2, 10, 15),
            )),
        ),
        week = listOf(0, null, 1, null, 2, null, null),
    )

    val upperLowerGym = ProgramTemplate(
        id = "upper-lower-gym",
        name = t("Upper / Lower 4-Day"),
        note = t("Two upper and two lower sessions a week. More volume per muscle."),
        equipment = t("Gym"),
        days = listOf(
            TemplateDay(t("Upper A"), listOf(
                pe("bb_bench_press", 4, 6, 10), pe("bb_row", 4, 8, 12), pe("db_shoulder_press", 3, 8, 12),
                pe("lat_pulldown", 3, 10, 12), pe("bb_curl", 2, 8, 12), pe("triceps_pushdown", 2, 10, 15),
            )),
            TemplateDay(t("Lower A"), listOf(
                pe("barbell_squat", 4, 6, 10), pe("barbell_rdl", 3, 8, 12), pe("leg_extension", 3, 12, 15),
                pe("leg_curl", 3, 10, 15), pe("standing_calf_raise", 4, 10, 15), pe("hanging_knee_raise", 3, 10, 15),
            )),
            TemplateDay(t("Upper B"), listOf(
                pe("incline_db_press", 4, 8, 12), pe("seated_cable_row", 4, 8, 12), pe("pullup", 3, 6, 10),
                pe("cable_lateral_raise", 3, 12, 20), pe("incline_db_curl", 2, 10, 12), pe("skull_crusher", 2, 10, 12),
            )),
            TemplateDay(t("Lower B"), listOf(
                pe("deadlift", 3, 5, 8), pe("leg_press", 3, 10, 15), pe("walking_lunge", 3, 10, 12, RepUnit.LEG),
                pe("leg_curl", 3, 10, 15), pe("standing_calf_raise", 4, 10, 15), pe("cable_crunch", 3, 10, 15),
            )),
        ),
        week = listOf(0, 1, null, 2, 3, null, null),
    )

    val pplGym = ProgramTemplate(
        id = "ppl-gym",
        name = t("Push Pull Legs"),
        note = t("Push, pull and legs, each twice a week. For experienced lifters."),
        equipment = t("Gym"),
        days = listOf(
            TemplateDay(t("Push"), listOf(
                pe("bb_bench_press", 4, 6, 10), pe("incline_db_press", 3, 8, 12), pe("overhead_press", 3, 6, 10),
                pe("cable_fly", 3, 12, 15), pe("db_lateral_raise", 3, 12, 20), pe("triceps_pushdown", 3, 10, 15),
            )),
            TemplateDay(t("Pull"), listOf(
                pe("deadlift", 3, 5, 8), pe("pullup", 3, 6, 10), pe("seated_cable_row", 3, 8, 12),
                pe("face_pull", 3, 12, 20), pe("bb_curl", 3, 8, 12), pe("hammer_curl", 2, 10, 12),
            )),
            TemplateDay(t("Legs"), listOf(
                pe("barbell_squat", 4, 6, 10), pe("barbell_rdl", 3, 8, 12), pe("leg_press", 3, 10, 15),
                pe("leg_curl", 3, 10, 15), pe("standing_calf_raise", 4, 10, 15), pe("plank", 3, 30, 60, RepUnit.SEC),
            )),
        ),
        week = listOf(0, 1, 2, 0, 1, 2, null),
    )

    val dumbbellHome = ProgramTemplate(
        id = "dumbbell-home",
        name = t("Dumbbell Home 3-Day"),
        note = t("Two full-body sessions alternating. Only dumbbells and a bench or chair."),
        equipment = t("Dumbbells"),
        days = listOf(
            TemplateDay(t("Dumbbell A"), listOf(
                pe("goblet_squat", 3, 10, 15), pe("db_bench_press", 3, 8, 12), pe("db_row_supported", 3, 8, 12, RepUnit.SIDE),
                pe("db_shoulder_press", 3, 8, 12), pe("db_curl", 2, 10, 15), pe("plank", 3, 30, 45, RepUnit.SEC),
            )),
            TemplateDay(t("Dumbbell B"), listOf(
                pe("db_rdl", 3, 8, 12), pe("bulgarian_split_squat", 3, 8, 12, RepUnit.LEG), pe("incline_db_press", 3, 8, 12),
                pe("db_row_bent", 3, 8, 12), pe("db_lateral_raise", 3, 12, 20), pe("oh_triceps_ext_1arm", 2, 10, 15, RepUnit.SIDE),
            )),
        ),
        week = listOf(0, null, 1, null, 0, null, null),
    )

    val bodyweight = ProgramTemplate(
        id = "bodyweight",
        name = t("Bodyweight 3-Day"),
        note = t("No equipment at all. Make the moves harder as you get stronger."),
        equipment = t("None"),
        days = listOf(
            TemplateDay(t("Bodyweight A"), listOf(
                pe("bw_squat", 3, 15, 25), pe("pushup", 3, 8, 20), pe("inverted_row", 3, 8, 15),
                pe("glute_bridge", 3, 12, 20), pe("plank", 3, 30, 60, RepUnit.SEC),
            )),
            TemplateDay(t("Bodyweight B"), listOf(
                pe("split_squat", 3, 10, 15, RepUnit.LEG), pe("pike_pushup", 3, 6, 12), pe("bench_dip", 3, 8, 15),
                pe("sl_glute_bridge", 3, 10, 15, RepUnit.LEG), pe("dead_bug", 3, 8, 12, RepUnit.SIDE), pe("burpee", 2, 8, 12),
            )),
        ),
        week = listOf(0, null, 1, null, 0, null, null),
    )

    val all: List<ProgramTemplate> = listOf(forge, fullBodyGym, upperLowerGym, pplGym, dumbbellHome, bodyweight)
    fun byId(id: String) = all.firstOrNull { it.id == id }
}
