package com.nutritiontracker.core.voice

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize

sealed interface ResolvedAmount {
    /**
     * Количество в единице основы продукта.
     * [explanation] показывается пользователю: например «3 шт × 182 г (USDA: 1 medium)».
     * [usesReferenceWeight] — вес взят из справочных данных, а не назван пользователем.
     */
    data class Resolved(
        val amount: Double,
        val unit: AmountUnit,
        val explanation: String?,
        val usesReferenceWeight: Boolean,
        val serving: ServingSize? = null,
    ) : ResolvedAmount

    /** Количество нельзя определить без пользователя. */
    data class NeedsInput(val reasonRu: String) : ResolvedAmount
}

/**
 * Переводит распознанное количество в граммы или миллилитры продукта.
 * Справочный вес штуки или порции берётся только из данных продукта и
 * всегда сопровождается пояснением.
 */
object QuantityResolver {
    fun resolve(quantity: ParsedQuantity?, basis: AmountUnit, servings: List<ServingSize>): ResolvedAmount {
        if (quantity == null) return ResolvedAmount.NeedsInput("Количество не прозвучало")
        return when (quantity) {
            is ParsedQuantity.Mass ->
                if (basis == AmountUnit.GRAM) {
                    ResolvedAmount.Resolved(quantity.grams, AmountUnit.GRAM, null, false)
                } else {
                    ResolvedAmount.NeedsInput("Продукт учитывается в миллилитрах, а названа масса — укажите объём в мл")
                }

            is ParsedQuantity.Volume ->
                if (basis == AmountUnit.MILLILITER) {
                    ResolvedAmount.Resolved(quantity.milliliters, AmountUnit.MILLILITER, null, false)
                } else {
                    ResolvedAmount.NeedsInput("Продукт учитывается в граммах, а назван объём — укажите массу в граммах")
                }

            is ParsedQuantity.Count -> resolveCount(quantity, basis, servings)
        }
    }

    private fun resolveCount(q: ParsedQuantity.Count, basis: AmountUnit, servings: List<ServingSize>): ResolvedAmount {
        val countText = formatCount(q.count)
        if (q.eachGrams != null) {
            return if (basis == AmountUnit.GRAM) {
                ResolvedAmount.Resolved(q.count * q.eachGrams, basis, "$countText × ${formatCount(q.eachGrams)} г", false)
            } else {
                ResolvedAmount.NeedsInput("Продукт учитывается в миллилитрах — укажите объём")
            }
        }
        if (q.eachMl != null) {
            return if (basis == AmountUnit.MILLILITER) {
                ResolvedAmount.Resolved(q.count * q.eachMl, basis, "$countText × ${formatCount(q.eachMl)} мл", false)
            } else {
                ResolvedAmount.NeedsInput("Продукт учитывается в граммах — укажите массу")
            }
        }
        val wanted = when (q.kind) {
            CountKind.PIECE -> listOf(ServingKind.PIECE)
            CountKind.PORTION -> listOf(ServingKind.PORTION)
            CountKind.PACKAGE -> listOf(ServingKind.PACKAGE)
            CountKind.HOUSEHOLD -> emptyList()
        }
        val serving = if (q.kind == CountKind.HOUSEHOLD) {
            householdServing(q.unitWord, basis, servings)
        } else {
            wanted.firstNotNullOfOrNull { kind -> servings.firstOrNull { it.kind == kind } }
        }
        if (serving == null) {
            val what = when (q.kind) {
                CountKind.PIECE -> "одной штуки"
                CountKind.PORTION -> "порции"
                CountKind.PACKAGE -> "упаковки"
                CountKind.HOUSEHOLD -> "меры «${q.unitWord ?: "мера"}»"
            }
            return ResolvedAmount.NeedsInput("Нет справочного веса $what для этого продукта — укажите количество")
        }
        val unitSymbol = basis.symbol
        val cupNote = if (q.kind == CountKind.HOUSEHOLD && Regex("\\bcup\\b").containsMatchIn(serving.label.lowercase())) {
            "; американская чашка — 240 мл, ваш стакан может отличаться"
        } else {
            ""
        }
        return ResolvedAmount.Resolved(
            amount = q.count * serving.amount,
            unit = basis,
            explanation = "$countText × ${formatCount(serving.amount)} $unitSymbol (${serving.sourceNote}: ${serving.label}$cupNote)",
            usesReferenceWeight = true,
            serving = serving,
        )
    }

    /**
     * Бытовая мера → справочная порция источника: «стакан», «кружка», «чашка» —
     * cup; «столовая ложка» — tbsp; «чайная ложка» — tsp. Просто «ложка» не
     * сопоставляется: неизвестно, столовая она или чайная.
     *
     * Если нужной меры в источнике нет, она выводится из другой меры того же
     * продукта по точным соотношениям американских мер (1 cup = 16 tbsp,
     * 1 tbsp = 3 tsp). Для продуктов в миллилитрах ложки — метрические 15 и 5 мл.
     */
    private fun householdServing(unitWord: String?, basis: AmountUnit, servings: List<ServingSize>): ServingSize? {
        val w = unitWord?.lowercase() ?: return null
        val measure = when {
            w.contains("стол") && w.contains("лож") -> Measure.TBSP
            w.contains("чайн") && w.contains("лож") -> Measure.TSP
            w.startsWith("стакан") || w.startsWith("круж") || w.startsWith("чаш") -> Measure.CUP
            else -> return null
        }
        val one = "1 ${measure.keys.first()}"
        perOne(measure, servings)?.let { (s, grams) ->
            val label = if (leadingCount(s.label) == 1.0) s.label else "${s.label} → $one"
            return ServingSize(label, grams, ServingKind.HOUSEHOLD, s.sourceNote)
        }
        for (other in Measure.entries) {
            if (other == measure) continue
            val (s, perOther) = perOne(other, servings) ?: continue
            val amount = perOther * measure.tsp / other.tsp
            val ratio = if (other.tsp > measure.tsp) "÷ ${formatCount(other.tsp / measure.tsp)}" else "× ${formatCount(measure.tsp / other.tsp)}"
            return ServingSize("1 ${other.keys.first()} $ratio = $one; из «${s.label}»", amount, ServingKind.HOUSEHOLD, s.sourceNote)
        }
        if (basis == AmountUnit.MILLILITER && measure != Measure.CUP) {
            return ServingSize(one, measure.metricMl, ServingKind.HOUSEHOLD, "метрическая мера")
        }
        return null
    }

    /** Мера источника и её вес в пересчёте на одну единицу: «0.5 cup» 120 г → 240 г. */
    private fun perOne(m: Measure, servings: List<ServingSize>): Pair<ServingSize, Double>? =
        servings.firstNotNullOfOrNull { s ->
            val lower = s.label.lowercase()
            if (s.kind != ServingKind.HOUSEHOLD || m.keys.none { k -> Regex("\\b$k\\b").containsMatchIn(lower) }) return@firstNotNullOfOrNull null
            val per = s.amount / leadingCount(s.label)
            if (per.isFinite() && per > 0) s to per else null
        }

    /** Число в начале описания меры: «2 tbsp» → 2, «0.5 cup» → 0,5, «1/4 cup» → 0,25; без числа — 1. */
    private fun leadingCount(label: String): Double {
        val m = Regex("^\\s*(\\d+(?:[.,]\\d+)?)(?:\\s*/\\s*(\\d+))?").find(label) ?: return 1.0
        val a = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return 1.0
        val d = m.groupValues[2].toDoubleOrNull()
        val v = if (d != null && d > 0) a / d else a
        return if (v.isFinite() && v > 0) v else 1.0
    }

    private enum class Measure(val keys: List<String>, val tsp: Double, val metricMl: Double) {
        TSP(listOf("tsp", "teaspoon", "teaspoons"), 1.0, 5.0),
        TBSP(listOf("tbsp", "tablespoon", "tablespoons"), 3.0, 15.0),
        CUP(listOf("cup", "cups"), 48.0, 0.0),
    }

    private fun formatCount(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
}
