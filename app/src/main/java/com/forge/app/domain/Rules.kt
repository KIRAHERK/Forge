package com.forge.app.domain

import com.forge.app.i18n.t

import kotlin.math.abs
import kotlin.random.Random

// ---------------------------------------------------------------- XP & levels

object Xp {
    const val SET = 5
    const val WORKOUT = 100
    const val MAKE_UP = 60
    const val PR = 25
    const val PROTEIN_DAY = 25
    const val CALORIE_DAY = 20
    const val WEIGH_IN = 10
    const val CLEAN_DAY = 15
    const val URGE = 10
    const val PENALTY_DONE = 30
    const val MISSED_WORKOUT = -150
    const val PENALTY_OVERDUE = -100
    const val PENALTY_FAILED = -200

    /** XP needed to go from [level] to level + 1. */
    fun stepFor(level: Int) = 300 + 100 * (level - 1)

    data class LevelInfo(val level: Int, val into: Int, val needed: Int, val title: String) {
        val progress: Float get() = if (needed == 0) 0f else into.toFloat() / needed
    }

    fun level(totalXp: Int): LevelInfo {
        var lvl = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (remaining >= stepFor(lvl)) {
            remaining -= stepFor(lvl); lvl++
        }
        return LevelInfo(lvl, remaining, stepFor(lvl), title(lvl))
    }

    fun title(level: Int) = when {
        level < 5 -> t("Beginner")
        level < 10 -> t("Novice")
        level < 15 -> t("Trained")
        level < 20 -> t("Committed")
        level < 30 -> t("Disciplined")
        level < 40 -> t("Hardened")
        else -> t("Elite")
    }
}

// ---------------------------------------------------------------- Penalties

enum class PenaltyKind(val label: String) { MISSED_WORKOUT(t("Missed workout")), SLIP(t("Slip")) }

data class PenaltyTask(val amount: Int, val unit: String, val what: String) {
    val text: String get() = if (unit.isBlank()) "$amount $what" else "$amount $unit $what"
}

object Penalties {
    /** Sick days: at most this many in any rolling window of [SICK_WINDOW_DAYS]. */
    const val SICK_LIMIT = 3
    const val SICK_WINDOW_DAYS = 30L

    /** Missed workout — heavier. */
    private val heavy = listOf(
        PenaltyTask(100, "", t("push-ups, any number of sets")),
        PenaltyTask(60, "", "burpees"),
        PenaltyTask(6, t("min"), t("plank, accumulated")),
        PenaltyTask(200, "", t("bodyweight squats")),
        PenaltyTask(100, "", t("walking lunges")),
    )

    /** Slipping on a habit you're quitting — lighter, but it adds up. */
    private val light = listOf(
        PenaltyTask(50, "", "push-ups"),
        PenaltyTask(40, "", "burpees"),
        PenaltyTask(4, t("min"), t("plank, accumulated")),
        PenaltyTask(150, "", t("bodyweight squats")),
        PenaltyTask(2, t("min"), t("cold shower")),
    )

    fun roll(kind: PenaltyKind, seed: Long): PenaltyTask {
        val pool = if (kind == PenaltyKind.MISSED_WORKOUT) heavy else light
        return pool[Random(seed).nextInt(pool.size)]
    }

    /** Everything that can come out of the roll — used for the reveal animation. */
    fun reel(kind: PenaltyKind) = (if (kind == PenaltyKind.MISSED_WORKOUT) heavy else light).map { it.text }

    const val DUE_HOURS = 24L
    const val MAKE_UP_HOURS = 48L
}

// ---------------------------------------------------------------- Progression

data class SetRecord(val reps: Int, val weight: Double)

object Progression {
    fun e1rm(reps: Int, weight: Double): Double = if (weight <= 0) reps.toDouble() else weight * (1 + reps / 30.0)

    /**
     * Double progression advice from the last session's working sets.
     */
    fun advice(pe: ProgramExercise, last: List<SetRecord>): String {
        val ex = pe.exercise
        if (last.isEmpty()) {
            return if (ex.loaded) t("First time: pick a weight you can lift for %1${'$'}s–%2${'$'}s clean reps, stopping 1–2 reps before failure.", pe.repMin, pe.repMax)
            else t("First time: controlled reps, stop 1–2 before failure. Log what you get.")
        }
        val topWeight = last.maxOf { it.weight }
        val atTop = last.filter { abs(it.weight - topWeight) < 0.01 }
        val hitTop = last.size >= pe.sets && atTop.size >= pe.sets && atTop.all { it.reps >= pe.repMax }
        return if (hitTop) {
            if (ex.loaded) t("You hit %1${'$'}s on every set last time. Go heavier: %2${'$'}s kg.", pe.repMax, fmtKg(topWeight + ex.weightStep))
            else t("You hit %s on every set. Make it harder: slower lowering, higher feet or a loaded backpack.", pe.repMax)
        } else {
            val total = last.sumOf { it.reps }
            val w = if (ex.loaded && topWeight > 0) t(" at %s kg", fmtKg(topWeight)) else ""
            "Last time: ${last.joinToString(" · ") { it.reps.toString() }}$w. Beat $total total reps."
        }
    }

    fun fmtKg(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(java.util.Locale.US, "%.1f", v)
}

// ---------------------------------------------------------------- Motivation

object Lines {
    private val general = listOf(
        "Nobody is coming to do this for you.",
        "You don't need motivation. You need to start.",
        "The plan is already written. Your only job is to show up.",
        "Every session you skip is one you never get back.",
        "81 to 90 doesn't happen by thinking about it.",
        "Results are slow. That's not a reason to stop. It's the reason most people do.",
        "Small, boring, every day. That's the whole secret.",
        "Your excuses aren't as original as you think.",
        "If it were easy, everyone would look like that.",
        "The version of you at 90 kg didn't skip today.",
        "Feelings are optional. Reps are not.",
        "Do it tired. Do it bored. Just do it.",
        "The weeks pass either way. Decide what you'll have to show for them.",
        "Consistency first. Progress follows.",
    )
    private val preWorkout = listOf(
        "%s is on the schedule. The schedule doesn't care how you feel.",
        "Get the first set of %s done. The rest follows.",
        "%s today. Warm up, then go to work.",
        "%s. Forty-five minutes. You've lost more than that scrolling today.",
    )
    private val restDay = listOf(
        "Rest day. Growth happens now — eat like it.",
        "No training today. Hit your protein and sleep.",
        "Recovery is part of the program, not a day off from it.",
    )
    private val missed = listOf(
        "You missed. That's done. The next one isn't.",
        "One miss is an accident. Two is a pattern. Don't make it two.",
        "Pay the penalty and get back on schedule.",
    )
    private val streak = listOf(
        "%d sessions in a row. Don't be the one who breaks it.",
        "%d straight. This is who you are now.",
        "%d sessions without a miss. Keep the chain.",
    )
    private val proteinLow = listOf(
        "%d g of protein left. Muscle isn't built from intentions.",
        "You trained. Now feed it. %d g protein to go.",
        "%d g short on protein. Fix it before bed.",
    )
    private val kcalLow = listOf(
        "%d kcal short. You can't build without material.",
        "%d kcal to go. The scale won't move on its own.",
    )
    private val done = listOf(
        "Workout done. Now eat and recover.",
        "Session logged. That's another brick.",
        "Done. Tomorrow's version of you says thanks.",
    )

    /**
     * Arabic has its own lines, written in Arabic and approved one by one, rather than
     * translations of the English ones. Same moments, same placeholders.
     */
    private object Ar {
        val general = listOf(
            "قطرة فوق قطرة تصنع نهرًا.",
            "العقل السليم في الجسم السليم.",
            "لا تنتظر الحماس. ابدأ، وسيلحق بك.",
            "الخطة مكتوبة. مهمتك الوحيدة أن تحضر.",
            "النتائج بطيئة، لكنها لا تأتي لمن يتوقف.",
            "الأسابيع ستمضي على أي حال. قرّر ماذا ستجني منها.",
            "الالتزام أولًا، والتقدّم يأتي بعده.",
        )
        val preWorkout = listOf(
            "%s على الجدول اليوم. والجدول لا يسأل عن مزاجك.",
            "أنهِ أول جولة من %s، والباقي أسهل.",
            "%s اليوم. إحماء، ثم إلى العمل.",
            "%s: خمس وأربعون دقيقة فقط. أضعتَ أكثر منها اليوم على الهاتف.",
        )
        val restDay = listOf(
            "يوم راحة. العضلات تُبنى الآن، فكُل جيدًا.",
            "لا تدريب اليوم. أكمل البروتين ونم جيدًا.",
            "الراحة جزء من البرنامج، لا إجازة منه.",
        )
        val missed = listOf(
            "فاتك التمرين. انتهى الأمر، لكن القادم لم يفُتك بعد.",
            "مرة واحدة صدفة، ومرتان عادة. لا تجعلها مرتين.",
            "أنجز العقوبة، وعُد إلى الجدول.",
        )
        val streak = listOf(
            "%d جلسات متتالية. لا تكن أنت من يقطع السلسلة.",
            "%d على التوالي. هذا أنت الآن.",
        )
        val proteinLow = listOf(
            "بقي %d غ من البروتين. العضلات لا تُبنى بالنوايا.",
            "تمرّنت، فأطعم عضلاتك. بقي %d غ بروتين.",
        )
        val kcalLow = listOf(
            "ينقصك %d سعرة. لا بناء بلا مواد.",
        )
        val done = listOf(
            "انتهى التمرين. الآن كُل وارتح.",
            "أحسنت. نسختك في الغد تشكرك.",
        )
    }

    private val ar get() = com.forge.app.i18n.Lang.arabic
    private fun <T> List<T>.pick(seed: Long): T = this[Random(seed).nextInt(size)]

    fun general(seed: Long) = (if (ar) Ar.general else general).pick(seed)
    fun preWorkout(seed: Long, day: String) = (if (ar) Ar.preWorkout else preWorkout).pick(seed).format(day)
    fun restDay(seed: Long) = (if (ar) Ar.restDay else restDay).pick(seed)
    fun missed(seed: Long) = (if (ar) Ar.missed else missed).pick(seed)
    fun streak(seed: Long, n: Int) = (if (ar) Ar.streak else streak).pick(seed).format(n)
    fun proteinLow(seed: Long, left: Int) = (if (ar) Ar.proteinLow else proteinLow).pick(seed).format(left)
    fun kcalLow(seed: Long, left: Int) = (if (ar) Ar.kcalLow else kcalLow).pick(seed).format(left)
    fun done(seed: Long) = (if (ar) Ar.done else done).pick(seed)

    // Notification copy, escalating through the day.
    fun reminder(meta: String, deadline: String) = t("%1${'$'}s Due by %2${'$'}s. Start now and it's done.", meta, deadline)
    fun nudge(left: String) = t("Still not started. %s left before it counts as a miss.", left)
    fun final(left: String) = t("%s left. Start now or take the penalty.", left)
}
