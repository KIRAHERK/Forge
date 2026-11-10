package com.forge.app.domain

import kotlin.math.roundToInt

data class Macros(val kcal: Int, val protein: Double, val fat: Double, val carbs: Double) {
    operator fun plus(o: Macros) = Macros(kcal + o.kcal, protein + o.protein, fat + o.fat, carbs + o.carbs)
    operator fun times(f: Double) = Macros((kcal * f).roundToInt(), protein * f, fat * f, carbs * f)

    companion object {
        val ZERO = Macros(0, 0.0, 0.0, 0.0)
    }
}

/*
 * Targets and rules copied from "muscle_gain_nutrition_targets.pdf".
 */
object Plan {
    const val KCAL = 2700
    const val PROTEIN = 130
    const val FAT = 75
    const val CARBS = 376

    const val START_WEIGHT = 81.0
    const val MILESTONE = 90.0

    const val RATE_MIN = 0.2 // kg per week
    const val RATE_MAX = 0.4
    const val TIMELINE = "About 6–11 months to reach 90 kg"
    const val GOAL_TEXT = "Build muscle and move from 81 kg to 90 kg, then reassess before deciding whether to continue toward 95–100 kg."
    const val RATE_NOTE = "The timeline is an estimate, not a promise. Early scale increases can include water and glycogen, not just muscle."

    val checkIn = listOf(
        "Aim for about 2,700 calories daily and keep protein near 130 g, unless your clinician gives you a different protein limit.",
        "Weigh yourself each morning after using the bathroom and before eating.",
        "Compare weekly average weights, not individual readings.",
        "After two weeks: if average weight is not rising, add 150–200 calories/day. If it rises much faster than 0.2–0.4 kg/week, reduce intake slightly.",
    )


    const val MEDICAL_NOTE =
        "These targets are a starting estimate, not medical advice. If you have a medical condition, " +
            "check protein and calorie limits with your clinician. Add anything they tell you under Settings → Plan notes."

    val lowCost = listOf(
        "Calories" to "Rice, oats, potatoes, bread, pasta, and other affordable grains.",
        "Protein" to "Lentils, beans, chickpeas, eggs, milk, yogurt, and affordable meat or canned fish.",
        "Easy calorie additions" to "Peanut butter, nuts, and measured cooking oil.",
    )
}

enum class FoodGroup(val label: String) { PROTEIN("Protein"), CARB("Carbs"), DAIRY("Dairy"), FAT("Fats & nuts"), FRUIT_VEG("Fruit & veg"), MEAL("Meals") }

data class Food(val id: String, val name: String, val serving: String, val m: Macros, val group: FoodGroup)

/*
 * Per-serving values from USDA FoodData Central (rounded). Check your own labels —
 * brands differ.
 */
object Foods {
    val all = listOf(
        // Protein
        Food("egg", "Egg, whole", "1 large (50 g)", Macros(72, 6.3, 4.8, 0.4), FoodGroup.PROTEIN),
        Food("egg_white", "Egg white", "1 large", Macros(17, 3.6, 0.1, 0.2), FoodGroup.PROTEIN),
        Food("tuna", "Tuna, canned in water", "1 can drained (110 g)", Macros(128, 28.0, 0.9, 0.0), FoodGroup.PROTEIN),
        Food("sardines", "Sardines in oil", "1 can drained (92 g)", Macros(191, 22.7, 10.5, 0.0), FoodGroup.PROTEIN),
        Food("chicken_breast", "Chicken breast, cooked", "150 g", Macros(248, 46.5, 5.4, 0.0), FoodGroup.PROTEIN),
        Food("chicken_thigh", "Chicken thigh, cooked, skinless", "150 g", Macros(314, 39.0, 16.4, 0.0), FoodGroup.PROTEIN),
        Food("beef_mince", "Beef mince 10% fat, cooked", "100 g", Macros(217, 26.1, 11.8, 0.0), FoodGroup.PROTEIN),
        Food("lentils", "Lentils, cooked", "200 g (1 cup)", Macros(232, 18.0, 0.8, 40.2), FoodGroup.PROTEIN),
        Food("chickpeas", "Chickpeas, cooked", "150 g", Macros(246, 13.4, 3.9, 41.1), FoodGroup.PROTEIN),
        Food("beans", "Kidney / black beans, cooked", "150 g", Macros(195, 13.2, 0.8, 34.6), FoodGroup.PROTEIN),
        Food("foul", "Foul medames (fava beans)", "200 g", Macros(220, 15.2, 0.8, 39.3), FoodGroup.PROTEIN),
        Food("falafel", "Falafel", "4 pieces (68 g)", Macros(226, 9.0, 12.1, 21.6), FoodGroup.PROTEIN),
        Food("whey", "Whey protein", "1 scoop (30 g)", Macros(120, 24.0, 1.5, 3.0), FoodGroup.PROTEIN),
        // Dairy
        Food("milk_whole", "Milk, whole", "250 ml", Macros(157, 8.1, 8.4, 12.0), FoodGroup.DAIRY),
        Food("milk_semi", "Milk, semi-skimmed", "250 ml", Macros(124, 8.2, 4.9, 12.0), FoodGroup.DAIRY),
        Food("greek_yogurt", "Greek yogurt, plain 2%", "200 g", Macros(146, 19.9, 3.8, 7.9), FoodGroup.DAIRY),
        Food("yogurt", "Yogurt, plain whole", "200 g", Macros(122, 6.9, 6.5, 9.3), FoodGroup.DAIRY),
        Food("cottage", "Cottage cheese 2%", "200 g", Macros(162, 21.0, 4.6, 9.6), FoodGroup.DAIRY),
        Food("cheese", "Cheddar / hard cheese", "30 g", Macros(121, 6.9, 10.0, 0.9), FoodGroup.DAIRY),
        Food("labneh", "Labneh", "50 g", Macros(80, 3.0, 6.5, 2.5), FoodGroup.DAIRY),
        // Carbs
        Food("rice", "Rice, white, cooked", "200 g", Macros(260, 5.4, 0.6, 56.0), FoodGroup.CARB),
        Food("oats", "Oats, dry", "80 g", Macros(303, 10.6, 5.2, 54.2), FoodGroup.CARB),
        Food("bread", "Bread, wholewheat", "1 slice (32 g)", Macros(81, 4.0, 1.1, 13.8), FoodGroup.CARB),
        Food("bread_white", "Bread, white", "1 slice (30 g)", Macros(80, 2.7, 1.0, 15.0), FoodGroup.CARB),
        Food("pita", "Pita bread", "1 large (60 g)", Macros(165, 5.5, 0.7, 33.4), FoodGroup.CARB),
        Food("pasta", "Pasta, cooked", "200 g", Macros(316, 11.6, 1.9, 61.8), FoodGroup.CARB),
        Food("potato", "Potato, boiled", "300 g", Macros(261, 5.7, 0.3, 60.3), FoodGroup.CARB),
        // Fats
        Food("pb", "Peanut butter", "1 tbsp (16 g)", Macros(94, 3.6, 8.0, 3.2), FoodGroup.FAT),
        Food("peanuts", "Peanuts", "30 g", Macros(170, 7.7, 14.8, 4.8), FoodGroup.FAT),
        Food("almonds", "Almonds", "30 g", Macros(173, 6.4, 14.9, 6.5), FoodGroup.FAT),
        Food("olive_oil", "Olive / cooking oil", "1 tbsp", Macros(119, 0.0, 13.5, 0.0), FoodGroup.FAT),
        Food("tahini", "Tahini", "1 tbsp (15 g)", Macros(89, 2.6, 8.0, 3.2), FoodGroup.FAT),
        Food("hummus", "Hummus", "100 g", Macros(166, 7.9, 9.6, 14.3), FoodGroup.FAT),
        // Fruit & veg
        Food("banana", "Banana", "1 medium", Macros(105, 1.3, 0.4, 27.0), FoodGroup.FRUIT_VEG),
        Food("dates", "Dates", "3 (24 g)", Macros(67, 0.6, 0.1, 18.0), FoodGroup.FRUIT_VEG),
        Food("apple", "Apple", "1 medium", Macros(95, 0.5, 0.3, 25.0), FoodGroup.FRUIT_VEG),
        Food("peas", "Green peas, cooked", "100 g", Macros(78, 5.2, 0.3, 14.3), FoodGroup.FRUIT_VEG),
        Food("honey", "Honey", "1 tbsp", Macros(64, 0.1, 0.0, 17.3), FoodGroup.FRUIT_VEG),
    )

    private val map = all.associateBy { it.id }
    fun byId(id: String) = map[id]
}

enum class MealType(val label: String) { BREAKFAST("Breakfast"), MAIN("Lunch / Dinner"), SNACK("Snack"), SHAKE("Shake") }

data class Recipe(
    val id: String,
    val name: String,
    val type: MealType,
    val servings: Int,
    val minutes: Int,
    val perServing: Macros,
    val ingredients: List<String>,
    val steps: List<String>,
    val why: String,
    val batch: Boolean = false,
)

/*
 * Budget recipes built from the PDF's low-cost food list. Macros are per serving,
 * calculated from USDA values for the listed amounts.
 */
object Recipes {
    val all = listOf(
        Recipe(
            "pb_oats", "Peanut Butter Oats", MealType.BREAKFAST, 1, 8,
            Macros(785, 29.0, 32.0, 102.0),
            listOf("80 g oats", "300 ml whole milk", "2 tbsp peanut butter (32 g)", "1 banana, sliced", "Pinch of salt, cinnamon"),
            listOf(
                "Simmer oats in the milk with a pinch of salt for 4–5 minutes, stirring.",
                "Take off the heat and stir in the peanut butter.",
                "Top with banana and cinnamon.",
            ),
            "Almost 800 kcal in one bowl. The easiest way to put a dent in 2,700.",
        ),
        Recipe(
            "egg_potato_hash", "Egg & Potato Hash", MealType.BREAKFAST, 1, 20,
            Macros(690, 32.0, 33.0, 66.0),
            listOf("4 eggs", "300 g potatoes, diced", "1 tbsp oil", "½ onion", "Salt, pepper, paprika"),
            listOf(
                "Boil or microwave the diced potato until just soft (6–8 min).",
                "Fry with the onion in the oil until browned at the edges.",
                "Make four gaps, crack in the eggs, cover and cook until the whites set.",
            ),
            "Cheap, filling and 32 g protein before noon.",
        ),
        Recipe(
            "foul_eggs", "Foul with Eggs & Pita", MealType.BREAKFAST, 1, 10,
            Macros(815, 39.0, 25.0, 106.0),
            listOf("200 g foul medames (canned fava beans)", "1 tbsp olive oil", "2 eggs", "2 pita breads", "Lemon, cumin, garlic, salt"),
            listOf(
                "Warm the beans with a splash of water, mash half of them.",
                "Season with lemon, cumin, garlic and salt; finish with the olive oil.",
                "Serve with boiled or fried eggs and the pita.",
            ),
            "Plant and egg protein together, very low cost per gram.",
        ),
        Recipe(
            "tuna_rice", "Tuna Rice Bowl", MealType.MAIN, 1, 10,
            Macros(650, 40.0, 16.0, 84.0),
            listOf("1 can tuna in water, drained", "250 g cooked rice", "100 g frozen peas", "1 tbsp olive oil", "Soy sauce or lemon, chilli"),
            listOf(
                "Microwave the peas for 2 minutes.",
                "Mix rice, peas and tuna; dress with oil and soy sauce or lemon.",
                "Eat hot or cold — it keeps 2 days in the fridge.",
            ),
            "40 g protein for the price of one can.",
        ),
        Recipe(
            "dal_rice", "Red Lentil Dal, Rice & Eggs", MealType.MAIN, 4, 35,
            Macros(770, 38.0, 19.0, 113.0),
            listOf(
                "300 g dry red lentils", "1 onion, chopped", "2 tbsp oil", "400 g canned tomatoes",
                "Garlic, cumin, turmeric, chilli", "800 g cooked rice (200 g per serving)", "8 eggs, boiled (2 per serving)",
            ),
            listOf(
                "Fry onion and garlic in the oil, add spices for 30 seconds.",
                "Add rinsed lentils, tomatoes and 900 ml water. Simmer 20–25 min until soft.",
                "Serve each portion with 200 g rice and 2 boiled eggs.",
            ),
            "Batch-cook on Sunday, four lunches done.",
            batch = true,
        ),
        Recipe(
            "chicken_rice_pot", "Chicken Thigh Rice Pot", MealType.MAIN, 4, 40,
            Macros(714, 48.0, 16.0, 90.0),
            listOf(
                "800 g boneless skinless chicken thighs", "400 g dry rice", "1 onion", "2 tbsp oil",
                "200 g frozen mixed vegetables", "1 L stock", "Paprika, cumin, garlic, salt",
            ),
            listOf(
                "Brown the seasoned chicken in the oil, set aside.",
                "Soften the onion, add rice and stir for 1 minute.",
                "Add stock, vegetables and chicken. Cover and cook on low 18–20 min until the rice is done.",
            ),
            "Thighs are cheaper than breast and stay juicy when reheated.",
            batch = true,
        ),
        Recipe(
            "beef_lentil_bolognese", "Beef & Lentil Bolognese", MealType.MAIN, 4, 45,
            Macros(775, 45.0, 16.0, 113.0),
            listOf(
                "400 g beef mince (10% fat)", "150 g dry brown lentils", "700 g passata", "1 onion",
                "1 tbsp oil", "Garlic, oregano, salt", "400 g dry pasta",
            ),
            listOf(
                "Brown the mince with onion and garlic in the oil.",
                "Add lentils, passata and 400 ml water. Simmer 30–35 min until the lentils are soft.",
                "Cook the pasta and split everything into 4 portions.",
            ),
            "Lentils stretch the meat and add protein and fibre.",
            batch = true,
        ),
        Recipe(
            "bean_chili", "Beef & Bean Chili with Rice", MealType.MAIN, 4, 40,
            Macros(615, 32.0, 12.0, 92.0),
            listOf(
                "300 g beef mince (10% fat)", "2 cans kidney beans, drained (480 g)", "400 g canned tomatoes",
                "1 onion", "1 tbsp oil", "Chilli, cumin, paprika", "800 g cooked rice",
            ),
            listOf(
                "Brown the mince and onion in the oil, add spices.",
                "Add beans and tomatoes, simmer 25 min.",
                "Serve over 200 g rice per portion.",
            ),
            "Freezes well. Make double.",
            batch = true,
        ),
        Recipe(
            "egg_fried_rice", "Egg Fried Rice", MealType.MAIN, 1, 12,
            Macros(810, 34.0, 29.0, 100.0),
            listOf("300 g cooked rice (day-old is best)", "3 eggs", "100 g frozen peas", "1 tbsp oil", "1 tbsp soy sauce", "Spring onion"),
            listOf(
                "Scramble the eggs in half the oil, set aside.",
                "Fry the rice and peas in the rest of the oil on high heat for 3–4 min.",
                "Add eggs and soy sauce, toss for 1 minute.",
            ),
            "Uses leftover rice. Add a can of tuna for +28 g protein.",
        ),
        Recipe(
            "sardine_pasta", "Sardine Garlic Pasta", MealType.MAIN, 1, 15,
            Macros(680, 36.0, 26.0, 75.0),
            listOf("100 g dry pasta", "1 can sardines in oil, drained", "1 tbsp olive oil", "2 garlic cloves", "Lemon, chilli flakes, parsley"),
            listOf(
                "Cook the pasta.",
                "Warm garlic and chilli in the oil, add sardines and break them up.",
                "Toss with the pasta, a splash of pasta water and lemon.",
            ),
            "Canned fish is among the cheapest protein per gram.",
        ),
        Recipe(
            "chicken_pita", "Chicken Hummus Pita", MealType.MAIN, 1, 10,
            Macros(700, 63.0, 13.0, 79.0),
            listOf("150 g cooked chicken breast", "2 pita breads", "60 g hummus", "Tomato, cucumber, lettuce"),
            listOf(
                "Warm the pita.",
                "Spread hummus, add sliced chicken and vegetables.",
                "Roll tight and eat. Pack it for later in foil.",
            ),
            "63 g protein — the highest-protein option here.",
        ),
        Recipe(
            "shakshuka", "Chickpea Shakshuka", MealType.MAIN, 2, 25,
            Macros(490, 26.0, 21.0, 52.0),
            listOf("4 eggs", "200 g canned chickpeas, drained", "400 g canned tomatoes", "1 onion", "1 tbsp oil", "Cumin, paprika, chilli", "2 slices wholewheat bread"),
            listOf(
                "Soften onion in oil, add spices, tomatoes and chickpeas. Simmer 10 min.",
                "Make 4 wells, crack in the eggs, cover and cook 5–7 min.",
                "Serve with the bread. Eat both portions if you're short on calories.",
            ),
            "One pan, two portions.",
        ),
        Recipe(
            "yogurt_bowl", "Greek Yogurt Bowl", MealType.SNACK, 1, 3,
            Macros(510, 35.0, 17.0, 58.0),
            listOf("250 g plain Greek yogurt", "40 g oats", "1 tbsp honey", "20 g peanuts"),
            listOf("Mix it all in a bowl. Done."),
            "35 g protein in three minutes.",
        ),
        Recipe(
            "pb_toast_milk", "Peanut Butter Toast & Milk", MealType.SNACK, 1, 4,
            Macros(540, 25.0, 28.0, 49.0),
            listOf("2 slices wholewheat bread", "2 tbsp peanut butter", "300 ml whole milk"),
            listOf("Toast, spread, drink the milk with it."),
            "The fallback snack when nothing is prepared.",
        ),
        Recipe(
            "mass_shake", "Homemade Mass Shake", MealType.SHAKE, 1, 3,
            Macros(835, 32.0, 37.0, 99.0),
            listOf("500 ml whole milk", "60 g oats", "2 tbsp peanut butter", "1 banana", "Ice, cinnamon (optional)"),
            listOf("Blend the oats first until fine, then add everything else and blend 30 seconds."),
            "835 kcal you can drink in five minutes. For days with no appetite.",
        ),
    )

    private val map = all.associateBy { it.id }
    fun byId(id: String) = map[id]
}

data class DayPlan(val id: String, val name: String, val note: String, val recipeIds: List<String>) {
    val recipes get() = recipeIds.mapNotNull { Recipes.byId(it) }
    val total: Macros get() = recipes.fold(Macros.ZERO) { acc, r -> acc + r.perServing }
}

object DayPlans {
    val all = listOf(
        DayPlan("budget", "Budget staples", "Oats, lentils, yogurt, tuna. The cheapest day.", listOf("pb_oats", "dal_rice", "yogurt_bowl", "tuna_rice")),
        DayPlan("home", "Home cooking", "Foul, chicken rice pot, toast, chili.", listOf("foul_eggs", "chicken_rice_pot", "pb_toast_milk", "bean_chili")),
        DayPlan("quick", "Little cooking", "Ten minutes or less per meal.", listOf("pb_oats", "tuna_rice", "egg_fried_rice", "yogurt_bowl")),
        DayPlan("low_appetite", "Low appetite", "Drink part of it. Shake, tuna, bolognese, toast.", listOf("mass_shake", "tuna_rice", "beef_lentil_bolognese", "pb_toast_milk")),
    )
}
