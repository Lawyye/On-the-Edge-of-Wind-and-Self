package com.nutritiontracker.core.norms

import com.nutritiontracker.core.calc.NutrientTotal
import com.nutritiontracker.core.calc.NutrientTotals
import com.nutritiontracker.core.model.Nutrient

enum class AssessmentStatus(val titleRu: String) {
    NO_DATA("Нет данных"),
    NO_NORM("Нормы нет"),
    BELOW("Ниже нормы"),
    /** Значение ниже нормы, но данные неполные: недостаток не установлен. */
    BELOW_INCOMPLETE("Неполные данные"),
    MET("Норма выполнена"),
    WITHIN_LIMIT("В пределах ограничения"),
    ABOVE_MAXIMUM("Превышение"),
}

data class NutrientAssessment(
    val nutrient: Nutrient,
    val total: NutrientTotal,
    val norm: NutrientNorm?,
    /** Процент выполнения нормы; при неполных данных — нижняя граница. */
    val percentOfTarget: Double?,
    val status: AssessmentStatus,
    /** Сколько не хватает до нормы (только при полных данных). */
    val shortfall: Double?,
    /** Насколько превышен предел. */
    val excess: Double?,
)

/** Пользовательская цель для нутриента; переопределяет справочную норму. */
data class UserNutrientGoal(val nutrient: Nutrient, val target: Double?, val maximum: Double?)

object NormEvaluator {
    fun applyUserGoals(norms: Map<Nutrient, NutrientNorm>, goals: List<UserNutrientGoal>): Map<Nutrient, NutrientNorm> {
        if (goals.isEmpty()) return norms
        val result = LinkedHashMap(norms)
        for (g in goals) {
            val base = norms[g.nutrient]
            result[g.nutrient] = NutrientNorm(
                nutrient = g.nutrient,
                target = g.target ?: base?.target,
                kind = if (g.target != null) NormKind.USER else base?.kind,
                maximum = g.maximum ?: base?.maximum,
                maximumKind = if (g.maximum != null) MaximumKind.USER else base?.maximumKind,
                sourceRu = if (g.target != null || g.maximum != null) "Ваша цель" else (base?.sourceRu ?: "Ваша цель"),
                noteRu = base?.noteRu,
            )
        }
        return result
    }

    fun assess(total: NutrientTotal, norm: NutrientNorm?): NutrientAssessment {
        val value = total.value
        if (value == null) {
            return NutrientAssessment(total.nutrient, total, norm, null, AssessmentStatus.NO_DATA, null, null)
        }
        val maximum = norm?.maximum
        // Превышение предела достоверно даже при неполных данных: нижняя граница уже выше предела.
        if (maximum != null && value > maximum) {
            val percent = norm.target?.takeIf { it > 0 }?.let { value / it * 100.0 }
            return NutrientAssessment(total.nutrient, total, norm, percent, AssessmentStatus.ABOVE_MAXIMUM, null, value - maximum)
        }
        val target = norm?.target
        if (target == null || target <= 0.0) {
            val status = if (maximum != null) AssessmentStatus.WITHIN_LIMIT else AssessmentStatus.NO_NORM
            return NutrientAssessment(total.nutrient, total, norm, null, status, null, null)
        }
        val percent = value / target * 100.0
        val status = when {
            value >= target -> AssessmentStatus.MET
            total.isLowerBound -> AssessmentStatus.BELOW_INCOMPLETE
            else -> AssessmentStatus.BELOW
        }
        return NutrientAssessment(
            nutrient = total.nutrient,
            total = total,
            norm = norm,
            percentOfTarget = percent,
            status = status,
            shortfall = if (status == AssessmentStatus.BELOW) target - value else null,
            excess = null,
        )
    }

    fun assessAll(totals: NutrientTotals, norms: Map<Nutrient, NutrientNorm>, nutrients: List<Nutrient>): List<NutrientAssessment> =
        nutrients.map { assess(totals[it], norms[it]) }
}
