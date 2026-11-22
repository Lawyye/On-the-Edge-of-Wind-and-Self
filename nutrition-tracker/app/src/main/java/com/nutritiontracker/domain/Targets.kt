package com.nutritiontracker.domain

import com.nutritiontracker.core.calc.CalorieGoal
import com.nutritiontracker.core.calc.EnergyCalculator
import com.nutritiontracker.core.calc.EnergyProfile
import com.nutritiontracker.core.calc.MacroCalculator
import com.nutritiontracker.core.calc.MacroGoals
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.calc.NutrientTotals
import com.nutritiontracker.core.calc.WeightPoint
import com.nutritiontracker.core.calc.WeightTrend
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.norms.DriNorms
import com.nutritiontracker.core.norms.NormEvaluator
import com.nutritiontracker.core.norms.NutrientNorm
import com.nutritiontracker.core.norms.UserNutrientGoal
import com.nutritiontracker.data.DiaryEntry
import com.nutritiontracker.data.Profile
import com.nutritiontracker.data.UserGoal
import java.time.LocalDate

data class DailyTargets(
    val calorie: CalorieGoal,
    val macros: MacroGoals,
    val norms: Map<Nutrient, NutrientNorm>,
    val waterGoalMl: Int,
    /** Вес, использованный для расчёта: среднее за 7 дней, последнее взвешивание или стартовый вес. */
    val weightUsedKg: Double,
    val ageYears: Int,
)

object Targets {
    fun currentWeight(profile: Profile, weights: List<WeightPoint>, today: LocalDate): Double =
        WeightTrend.trailingAverage(weights, today)
            ?: WeightTrend.daily(weights).lastOrNull { !it.date.isAfter(today) }?.weightKg
            ?: profile.startWeightKg

    fun compute(profile: Profile, weights: List<WeightPoint>, userGoals: List<UserGoal>, today: LocalDate): DailyTargets {
        val weight = currentWeight(profile, weights, today).coerceIn(20.0, 400.0)
        val age = EnergyCalculator.ageYears(profile.birthDate, today).coerceIn(EnergyCalculator.MIN_AGE, EnergyCalculator.MAX_AGE)
        val goal = EnergyCalculator.calorieGoal(
            profile = EnergyProfile(profile.sex, profile.birthDate, profile.heightCm, weight, profile.activity),
            today = if (EnergyCalculator.ageYears(profile.birthDate, today) < EnergyCalculator.MIN_AGE) profile.birthDate.plusYears(18) else today,
            mode = profile.deficitMode,
            customDeficitKcal = profile.customDeficitKcal,
            manualGoalKcal = profile.manualGoalKcal,
            adaptiveTdeeKcal = if (profile.adaptiveEnabled) profile.appliedAdaptiveTdee else null,
            targetWeightKg = profile.targetWeightKg,
        )
        val losingWeight = profile.deficitMode != com.nutritiontracker.core.calc.DeficitMode.MAINTAIN && goal.deficitKcal > 0
        val reference = MacroCalculator.referenceWeight(weight, profile.targetWeightKg, losingWeight)
        val macros = MacroCalculator.goals(goal.goalKcal, reference, profile.macroPlan)
        val base = DriNorms.withMacroPlan(DriNorms.forPerson(profile.sex, age, weight, goal.goalKcal), macros, profile.macroPlan, age)
        val norms = NormEvaluator.applyUserGoals(base, userGoals.map { UserNutrientGoal(it.nutrient, it.target, it.maximum) })
        return DailyTargets(goal, macros, norms, profile.waterGoalMl, weight, age)
    }
}

/** Итоги дня по дневнику. */
data class DaySummary(
    val date: LocalDate,
    val entries: List<DiaryEntry>,
    val totals: NutrientTotals,
    val byMeal: Map<MealType, NutrientTotals>,
) {
    val energy get() = totals[Nutrient.ENERGY]

    companion object {
        fun of(date: LocalDate, entries: List<DiaryEntry>): DaySummary = DaySummary(
            date = date,
            entries = entries,
            totals = NutrientMath.total(entries.map { it.eaten }),
            byMeal = MealType.entries.associateWith { m -> NutrientMath.total(entries.filter { it.meal == m }.map { it.eaten }) },
        )
    }
}

/** Средние за период: делятся на число дней, в которые есть записи. */
data class PeriodSummary(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DaySummary>,
    val loggedDays: Int,
    /** Средние за день по дням с записями. null — записей нет. */
    val dailyAverage: NutrientTotals?,
) {
    companion object {
        fun of(from: LocalDate, to: LocalDate, entries: List<DiaryEntry>): PeriodSummary {
            val byDay = entries.groupBy { it.date }
            val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }
                .map { d -> DaySummary.of(d, byDay[d] ?: emptyList()) }.toList()
            val logged = days.count { it.entries.isNotEmpty() }
            val avg = if (logged == 0) null else NutrientMath.total(entries.map { it.eaten }).dividedBy(logged.toDouble())
            return PeriodSummary(from, to, days, logged, avg)
        }
    }
}
