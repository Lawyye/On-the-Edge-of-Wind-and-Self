package com.nutritiontracker.core.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.sign

/** Калорийность дня по дневнику. [complete] = для всех записей дня известна калорийность. */
data class DayIntake(val date: LocalDate, val kcal: Double, val complete: Boolean)

sealed interface AdaptiveTdeeResult {
    data class Estimate(
        val tdeeKcal: Double,
        val averageIntakeKcal: Double,
        val weightSlopeKgPerWeek: Double,
        val windowStart: LocalDate,
        val windowEnd: LocalDate,
        val loggedDays: Int,
        val weightEntries: Int,
    ) : AdaptiveTdeeResult

    data class NotEnoughData(val reasonRu: String) : AdaptiveTdeeResult
}

/**
 * Оценка реального расхода энергии по балансу: средняя калорийность рациона
 * минус энергия изменения массы тела (тренд веса × 7700 ккал/кг).
 *
 * Используется тенденция веса за несколько недель (линейная регрессия),
 * а не разница двух взвешиваний. Оценка не меняет цель сама по себе:
 * применение решается отдельно и ограничено шагом [MAX_STEP_KCAL] в неделю.
 */
object AdaptiveTdee {
    const val WINDOW_DAYS = 28L
    const val MIN_DAYS = 14L
    const val MIN_LOGGED_FRACTION = 0.8
    const val MIN_WEIGHT_ENTRIES = 6
    const val MIN_WEIGHT_SPAN_DAYS = 10L
    const val MAX_STEP_KCAL = 150.0
    const val MIN_PLAUSIBLE_KCAL = 1000.0
    const val MAX_PLAUSIBLE_KCAL = 6000.0

    fun estimate(intakes: List<DayIntake>, weights: List<WeightPoint>, today: LocalDate): AdaptiveTdeeResult {
        val end = today.minusDays(1) // сегодняшний день ещё не закончен
        val firstData = (intakes.map { it.date } + weights.map { it.date }).minOrNull()
            ?: return AdaptiveTdeeResult.NotEnoughData("Нет записей питания и веса.")
        val start = maxOf(end.minusDays(WINDOW_DAYS - 1), firstData)
        val days = ChronoUnit.DAYS.between(start, end) + 1
        if (days < MIN_DAYS) {
            return AdaptiveTdeeResult.NotEnoughData("Нужно минимум $MIN_DAYS дней наблюдений, сейчас $days.")
        }
        val inWindow = { d: LocalDate -> !d.isBefore(start) && !d.isAfter(end) }
        val logged = intakes.filter { inWindow(it.date) && it.complete && it.kcal > 0 }
            .groupBy { it.date }.map { (_, v) -> v.first() }
        if (logged.size < days * MIN_LOGGED_FRACTION) {
            return AdaptiveTdeeResult.NotEnoughData(
                "Питание полностью записано за ${logged.size} из $days дней; нужно хотя бы ${(MIN_LOGGED_FRACTION * 100).toInt()}%.",
            )
        }
        val w = WeightTrend.daily(weights.filter { inWindow(it.date) })
        if (w.size < MIN_WEIGHT_ENTRIES) {
            return AdaptiveTdeeResult.NotEnoughData("Нужно хотя бы $MIN_WEIGHT_ENTRIES взвешиваний за период, сейчас ${w.size}.")
        }
        val span = ChronoUnit.DAYS.between(w.first().date, w.last().date)
        if (span < MIN_WEIGHT_SPAN_DAYS) {
            return AdaptiveTdeeResult.NotEnoughData("Взвешивания должны охватывать хотя бы $MIN_WEIGHT_SPAN_DAYS дней.")
        }
        val slope = WeightTrend.slopeKgPerDay(w)
            ?: return AdaptiveTdeeResult.NotEnoughData("Не удалось определить тенденцию веса.")
        val avgIntake = logged.map { it.kcal }.average()
        val tdee = avgIntake - slope * EnergyCalculator.KCAL_PER_KG_BODY_WEIGHT
        if (tdee !in MIN_PLAUSIBLE_KCAL..MAX_PLAUSIBLE_KCAL) {
            return AdaptiveTdeeResult.NotEnoughData(
                "Оценка ${tdee.toInt()} ккал выглядит неправдоподобно — проверьте записи веса и питания.",
            )
        }
        return AdaptiveTdeeResult.Estimate(
            tdeeKcal = tdee,
            averageIntakeKcal = avgIntake,
            weightSlopeKgPerWeek = slope * 7.0,
            windowStart = start,
            windowEnd = end,
            loggedDays = logged.size,
            weightEntries = w.size,
        )
    }

    /**
     * Следующее применяемое значение расхода: движется к новой оценке не
     * больше чем на [maxStep] за одно обновление, чтобы шум в данных не
     * раскачивал цель.
     */
    fun nextApplied(previous: Double?, estimate: Double, maxStep: Double = MAX_STEP_KCAL): Double {
        if (previous == null) return estimate
        val diff = estimate - previous
        return if (abs(diff) <= maxStep) estimate else previous + sign(diff) * maxStep
    }
}
