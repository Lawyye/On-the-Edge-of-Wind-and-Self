package com.nutritiontracker.core.calc

import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Проверка согласованности заявленной калорийности с БЖУ.
 *
 * Калорийность из источника никогда не заменяется расчётной: в разных
 * системах учёта энергии используются разные коэффициенты (общие и
 * специфические коэффициенты Этуотера, учёт клетчатки по 2 ккал/г в ЕС или
 * включение её в углеводы в США). Поэтому проверка строит диапазон из
 * нескольких допустимых способов расчёта и сообщает лишь о грубых
 * расхождениях — например, когда вместо ккал введены кДж или значения
 * указаны на порцию, а не на 100 г.
 */
object EnergyConsistency {
    const val RELATIVE_TOLERANCE = 0.15
    const val ABSOLUTE_TOLERANCE_KCAL = 15.0

    enum class Status { CONSISTENT, MISMATCH, NOT_ENOUGH_DATA }

    data class Result(
        val status: Status,
        val declaredKcal: Double?,
        val expectedMinKcal: Double?,
        val expectedMaxKcal: Double?,
        /** Отклонение заявленной энергии от ближайшей границы диапазона, %. */
        val deviationPercent: Double?,
    )

    fun check(per100: Nutrients): Result {
        val energy = per100[Nutrient.ENERGY]
        val protein = per100[Nutrient.PROTEIN]
        val fat = per100[Nutrient.FAT]
        val carbs = per100[Nutrient.CARBS]
        if (energy == null || protein == null || fat == null || carbs == null) {
            return Result(Status.NOT_ENOUGH_DATA, energy, null, null, null)
        }
        val fiber = per100[Nutrient.FIBER] ?: 0.0
        val alcohol = per100[Nutrient.ALCOHOL] ?: 0.0
        val base = 4.0 * protein + 9.0 * fat + 7.0 * alcohol
        // США: углеводы «по разности» включают клетчатку, все по 4 ккал/г.
        val us = base + 4.0 * carbs
        // ЕС: углеводы без клетчатки, клетчатка отдельно по 2 ккал/г.
        val eu = base + 4.0 * carbs + 2.0 * fiber
        // Углеводы с клетчаткой, но клетчатка учтена по 2 ккал/г.
        val mixed = base + 4.0 * max(0.0, carbs - fiber) + 2.0 * min(carbs, fiber)
        val lo = minOf(us, eu, mixed)
        val hi = maxOf(us, eu, mixed)
        val lower = lo * (1 - RELATIVE_TOLERANCE) - ABSOLUTE_TOLERANCE_KCAL
        val upper = hi * (1 + RELATIVE_TOLERANCE) + ABSOLUTE_TOLERANCE_KCAL
        val consistent = energy in lower..upper
        val deviation = when {
            consistent -> 0.0
            energy < lower -> if (lo > 0) abs(energy - lo) / lo * 100.0 else null
            else -> if (hi > 0) abs(energy - hi) / hi * 100.0 else null
        }
        return Result(
            status = if (consistent) Status.CONSISTENT else Status.MISMATCH,
            declaredKcal = energy,
            expectedMinKcal = lo,
            expectedMaxKcal = hi,
            deviationPercent = deviation,
        )
    }
}
