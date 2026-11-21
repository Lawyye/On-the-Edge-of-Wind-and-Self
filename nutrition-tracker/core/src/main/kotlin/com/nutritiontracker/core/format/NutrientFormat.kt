package com.nutritiontracker.core.format

import com.nutritiontracker.core.calc.NutrientTotal
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.NutrientUnit
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Округление только для показа. В базе и расчётах значения хранятся с полной точностью.
 */
object NutrientFormat {
    const val NO_DATA = "нет данных"

    /** Число знаков после запятой в зависимости от величины. */
    fun decimalsFor(value: Double, unit: NutrientUnit): Int = when {
        unit == NutrientUnit.KCAL -> 0
        value == 0.0 -> 0
        value >= 100 -> 0
        value >= 10 -> 1
        value >= 1 -> 1
        value >= 0.1 -> 2
        else -> 3
    }

    fun number(value: Double, decimals: Int): String {
        val bd = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = if (bd.scale() < 0) bd.setScale(0).toPlainString() else bd.toPlainString()
        return plain.replace('.', ',')
    }

    fun value(value: Double, unit: NutrientUnit): String = number(value, decimalsFor(value, unit))

    fun withUnit(value: Double?, nutrient: Nutrient): String =
        if (value == null) NO_DATA else "${value(value, nutrient.unit)} ${nutrient.unit.symbol}"

    /** Сумма с учётом неполноты: «≥ 12,5 мг», если данные есть не для всех продуктов. */
    fun total(total: NutrientTotal): String {
        val v = total.value ?: return NO_DATA
        val prefix = if (total.isLowerBound) "≥ " else ""
        return "$prefix${value(v, total.nutrient.unit)} ${total.nutrient.unit.symbol}"
    }

    fun percent(p: Double): String = "${number(p, if (p < 10) 1 else 0)}%"

    fun amount(v: Double): String = number(v, if (v >= 100) 0 else 1)

    fun kcal(v: Double): String = number(v, 0)

    fun kg(v: Double): String = number(v, 1)

    fun signedKg(v: Double): String = (if (v > 0) "+" else if (v < 0) "−" else "") + number(kotlin.math.abs(v), 1)
}
