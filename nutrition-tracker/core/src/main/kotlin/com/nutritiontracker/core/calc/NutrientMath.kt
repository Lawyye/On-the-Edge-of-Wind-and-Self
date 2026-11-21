package com.nutritiontracker.core.calc

import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients

/**
 * Сумма одного нутриента по нескольким позициям (продуктам дня, ингредиентам).
 *
 * - [value] = null: ни для одной позиции нет данных — «нет данных».
 * - [isComplete]: данные есть для всех позиций — значение точное.
 * - [isLowerBound]: данные есть не для всех позиций — фактическое
 *   потребление не меньше [value], но может быть больше.
 */
data class NutrientTotal(
    val nutrient: Nutrient,
    val value: Double?,
    val itemsWithData: Int,
    val itemsTotal: Int,
    val hasPartialItems: Boolean,
) {
    val isUnknown: Boolean get() = value == null
    val isComplete: Boolean get() = value != null && itemsWithData == itemsTotal && !hasPartialItems
    val isLowerBound: Boolean get() = value != null && !isComplete
    val itemsWithoutData: Int get() = itemsTotal - itemsWithData
}

class NutrientTotals internal constructor(
    private val totals: Map<Nutrient, NutrientTotal>,
    val itemCount: Int,
) {
    operator fun get(nutrient: Nutrient): NutrientTotal = totals.getValue(nutrient)

    fun all(): Collection<NutrientTotal> = totals.values

    /** Значения как набор нутриентов: неизвестные отсутствуют, неполные помечены. */
    fun asNutrients(): Nutrients {
        val values = totals.values.filter { it.value != null }.associate { it.nutrient to it.value!! }
        val incomplete = totals.values.filter { it.isLowerBound }.map { it.nutrient }.toSet()
        return Nutrients.of(values, incomplete)
    }

    /** Делит все значения (для средних за период). Счётчики позиций не меняются. */
    fun dividedBy(divisor: Double): NutrientTotals {
        require(divisor.isFinite() && divisor > 0.0) { "Делитель должен быть положительным: $divisor" }
        return NutrientTotals(totals.mapValues { (_, t) -> t.copy(value = t.value?.div(divisor)) }, itemCount)
    }
}

object NutrientMath {
    /**
     * Пищевая ценность съеденного количества:
     * значение на 100 × количество / 100.
     * Количество — в единице основы продукта (г или мл).
     */
    fun forAmount(per100: Nutrients, amount: Double): Nutrients {
        require(amount.isFinite() && amount >= 0.0) { "Количество должно быть неотрицательным: $amount" }
        return per100.scaled(amount / 100.0)
    }

    /** Значение одного нутриента для количества; null — если оно неизвестно. */
    fun valueForAmount(per100: Nutrients, nutrient: Nutrient, amount: Double): Double? =
        per100[nutrient]?.let { it * amount / 100.0 }

    /**
     * Суммирует позиции. Пустой список даёт нули: если ничего не съедено,
     * потребление действительно равно нулю.
     */
    fun total(items: List<Nutrients>): NutrientTotals {
        val totals = Nutrient.entries.associateWith { nutrient ->
            var sum = 0.0
            var withData = 0
            var partial = false
            for (item in items) {
                val v = item[nutrient] ?: continue
                sum += v
                withData++
                if (!item.isComplete(nutrient)) partial = true
            }
            NutrientTotal(
                nutrient = nutrient,
                value = if (items.isEmpty()) 0.0 else if (withData == 0) null else sum,
                itemsWithData = withData,
                itemsTotal = items.size,
                hasPartialItems = partial,
            )
        }
        return NutrientTotals(totals, items.size)
    }
}
