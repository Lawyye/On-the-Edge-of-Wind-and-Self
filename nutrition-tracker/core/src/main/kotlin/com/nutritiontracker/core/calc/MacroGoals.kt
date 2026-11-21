package com.nutritiontracker.core.calc

/**
 * Распределение калорийности между белками, жирами и углеводами в процентах.
 * Допустимые диапазоны (AMDR, NASEM 2005): белки 10–35%, жиры 20–35%,
 * углеводы 45–65% энергии для взрослых.
 */
data class MacroSplit(val proteinPercent: Int, val fatPercent: Int, val carbsPercent: Int) {
    val sum: Int get() = proteinPercent + fatPercent + carbsPercent

    fun outsideAmdr(): List<String> = buildList {
        if (proteinPercent !in PROTEIN_AMDR) add("Белки вне диапазона 10–35% энергии")
        if (fatPercent !in FAT_AMDR) add("Жиры вне диапазона 20–35% энергии")
        if (carbsPercent !in CARBS_AMDR) add("Углеводы вне диапазона 45–65% энергии")
    }

    companion object {
        val PROTEIN_AMDR = 10..35
        val FAT_AMDR = 20..35
        val CARBS_AMDR = 45..65

        /** Распределение по умолчанию: в пределах AMDR, с белком ближе к верхней части диапазона. */
        val DEFAULT = MacroSplit(25, 30, 45)
    }
}

data class MacroGoals(
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
)

object MacroCalculator {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0

    fun goals(calorieGoalKcal: Double, split: MacroSplit): MacroGoals {
        require(split.sum == 100) { "Сумма долей БЖУ должна быть 100%, сейчас ${split.sum}%" }
        return MacroGoals(
            proteinG = calorieGoalKcal * split.proteinPercent / 100.0 / KCAL_PER_G_PROTEIN,
            fatG = calorieGoalKcal * split.fatPercent / 100.0 / KCAL_PER_G_FAT,
            carbsG = calorieGoalKcal * split.carbsPercent / 100.0 / KCAL_PER_G_CARBS,
        )
    }
}
