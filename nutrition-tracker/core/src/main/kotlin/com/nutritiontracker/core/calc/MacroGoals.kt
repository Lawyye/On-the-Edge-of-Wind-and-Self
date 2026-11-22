package com.nutritiontracker.core.calc

import kotlin.math.max

/**
 * План распределения калорийности:
 * - белок — в граммах на килограмм опорного веса ([proteinGPerKg]);
 * - жиры — доля калорийности ([fatPercent]);
 * - углеводы — оставшиеся калории.
 *
 * Почему белок в г/кг, а не в процентах: при снижении веса для сохранения
 * мышечной массы обзоры рекомендуют около 1,2–1,6 г/кг в сутки (напр.,
 * Leidy et al., Am J Clin Nutr 2015). Процент от калорийности при дефиците
 * давал бы слишком мало белка именно тогда, когда его нужно больше.
 * RDA NASEM 0,8 г/кг — минимум для здорового человека, а не цель при похудении.
 */
data class MacroPlan(
    val proteinGPerKg: Double = DEFAULT_PROTEIN_G_PER_KG,
    val fatPercent: Int = DEFAULT_FAT_PERCENT,
) {
    companion object {
        const val DEFAULT_PROTEIN_G_PER_KG = 1.6
        const val DEFAULT_FAT_PERCENT = 30
        const val MIN_PROTEIN_G_PER_KG = 0.8
        const val MAX_PROTEIN_G_PER_KG = 2.2
        val FAT_PERCENT_RANGE = 20..40
        val DEFAULT = MacroPlan()
    }
}

/** Допустимые диапазоны NASEM (AMDR) для взрослых, % калорийности. */
object Amdr {
    val PROTEIN = 10..35
    val FAT = 20..35
    val CARBS = 45..65
    /** RDA для углеводов — минимум для обеспечения мозга глюкозой, г/сут. */
    const val CARBS_RDA_G = 130.0
}

data class MacroGoals(
    val proteinG: Double,
    val fatG: Double,
    val carbsG: Double,
    /** Опорный вес, от которого считался белок. */
    val referenceWeightKg: Double,
    val proteinPercent: Double,
    val fatPercent: Double,
    val carbsPercent: Double,
    val warnings: List<String>,
)

object MacroCalculator {
    const val KCAL_PER_G_PROTEIN = 4.0
    const val KCAL_PER_G_CARBS = 4.0
    const val KCAL_PER_G_FAT = 9.0

    /** ИМТ, начиная с которого белок считается от желаемого веса. */
    const val OBESITY_BMI = 30.0

    /**
     * Опорный вес для белка — текущий вес. Только при ожирении (ИМТ ≥ 30) и
     * снижении веса берётся желаемый вес: иначе при большой массе жира норма
     * белка получается завышенной.
     */
    fun referenceWeight(currentKg: Double, targetKg: Double, heightCm: Double, losingWeight: Boolean): Double {
        val bmi = EnergyCalculator.bmi(currentKg, heightCm)
        return if (losingWeight && bmi >= OBESITY_BMI && targetKg < currentKg) targetKg else currentKg
    }

    fun goals(calorieGoalKcal: Double, referenceWeightKg: Double, plan: MacroPlan): MacroGoals {
        require(calorieGoalKcal.isFinite() && calorieGoalKcal > 0) { "Калорийность должна быть больше нуля" }
        require(referenceWeightKg.isFinite() && referenceWeightKg > 0) { "Вес должен быть больше нуля" }
        require(plan.proteinGPerKg in MacroPlan.MIN_PROTEIN_G_PER_KG..MacroPlan.MAX_PROTEIN_G_PER_KG) {
            "Белок — от ${MacroPlan.MIN_PROTEIN_G_PER_KG} до ${MacroPlan.MAX_PROTEIN_G_PER_KG} г/кг"
        }
        require(plan.fatPercent in MacroPlan.FAT_PERCENT_RANGE) { "Жиры — от 20 до 40% калорийности" }

        val protein = plan.proteinGPerKg * referenceWeightKg
        val fat = calorieGoalKcal * plan.fatPercent / 100.0 / KCAL_PER_G_FAT
        val carbsKcal = calorieGoalKcal - protein * KCAL_PER_G_PROTEIN - fat * KCAL_PER_G_FAT
        val carbs = max(0.0, carbsKcal) / KCAL_PER_G_CARBS

        val pPct = protein * KCAL_PER_G_PROTEIN / calorieGoalKcal * 100
        val fPct = plan.fatPercent.toDouble()
        val cPct = carbs * KCAL_PER_G_CARBS / calorieGoalKcal * 100
        val warnings = buildList {
            if (carbsKcal <= 0) add("Белок и жиры уже занимают всю калорийность — на углеводы ничего не остаётся. Уменьшите белок или жиры.")
            else if (carbs < Amdr.CARBS_RDA_G) add("Углеводов меньше 130 г — минимальной нормы NASEM (RDA) для обеспечения мозга глюкозой.")
            if (pPct > Amdr.PROTEIN.last) add("Белок больше 35% калорийности — выше допустимого диапазона NASEM (AMDR).")
            if (cPct > 0 && cPct < Amdr.CARBS.first && carbs >= Amdr.CARBS_RDA_G) {
                add("Углеводы — ${cPct.toInt()}% калорийности, ниже диапазона AMDR 45–65%. Это допустимо при осознанном выборе, но не является нормой NASEM.")
            }
        }
        return MacroGoals(protein, fat, carbs, referenceWeightKg, pPct, fPct, cPct, warnings)
    }
}
