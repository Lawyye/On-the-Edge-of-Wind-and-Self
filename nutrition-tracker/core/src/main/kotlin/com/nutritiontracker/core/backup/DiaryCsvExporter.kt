package com.nutritiontracker.core.backup

import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Экспорт дневника в CSV для Excel/Google Таблиц в русской локали:
 * разделитель «;», десятичная запятая, кодировка UTF-8 с BOM.
 * Пустая ячейка — нет данных (не ноль).
 */
object DiaryCsvExporter {
    data class Row(
        val date: LocalDate,
        val meal: MealType,
        val foodName: String,
        val brand: String?,
        val amount: Double,
        val unit: AmountUnit,
        val per100: Nutrients,
    )

    private const val SEP = ";"
    const val BOM = "﻿"

    fun export(rows: List<Row>): String {
        val sb = StringBuilder(BOM)
        val header = listOf("Дата", "Приём пищи", "Продукт", "Производитель", "Количество", "Единица") +
            Nutrient.entries.map { "${it.titleRu}, ${it.unit.symbol}" } +
            listOf("Нижняя граница (данные неполные)")
        sb.append(header.joinToString(SEP) { escape(it) }).append("\r\n")
        for (r in rows.sortedWith(compareBy({ it.date }, { it.meal.order }))) {
            val eaten = NutrientMath.forAmount(r.per100, r.amount)
            val cells = mutableListOf(
                r.date.toString(),
                r.meal.titleRu,
                neutralizeFormula(r.foodName),
                neutralizeFormula(r.brand ?: ""),
                number(r.amount),
                r.unit.symbol,
            )
            Nutrient.entries.forEach { n -> cells += eaten[n]?.let { number(it) } ?: "" }
            cells += eaten.incomplete.sortedBy { it.ordinal }.joinToString(", ") { it.titleRu }
            sb.append(cells.joinToString(SEP) { escape(it) }).append("\r\n")
        }
        return sb.toString()
    }

    /** Число с десятичной запятой, до 4 знаков после неё, без лишних нулей. */
    fun number(v: Double): String =
        BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',')

    /**
     * Защита от CSV-инъекции: названия продуктов приходят в том числе из
     * Open Food Facts, которую может редактировать кто угодно. Ячейка,
     * начинающаяся с «=», «+», «-», «@», табуляции или перевода строки,
     * в Excel и Google Таблицах выполнилась бы как формула, поэтому перед ней
     * ставится апостроф — значение показывается как обычный текст.
     */
    fun neutralizeFormula(s: String): String =
        if (s.isNotEmpty() && s[0] in FORMULA_STARTS) "'$s" else s

    private val FORMULA_STARTS = setOf('=', '+', '-', '@', '\t', '\r', '\n')

    private fun escape(s: String): String =
        if (s.contains(';') || s.contains('"') || s.contains('\n') || s.contains('\r')) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else {
            s
        }
}
