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
            householdServing(q.unitWord, servings)
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
     */
    private fun householdServing(unitWord: String?, servings: List<ServingSize>): ServingSize? {
        val w = unitWord?.lowercase() ?: return null
        val keys = when {
            w.contains("стол") && w.contains("лож") -> listOf("tbsp", "tablespoon")
            w.contains("чайн") && w.contains("лож") -> listOf("tsp", "teaspoon")
            w.startsWith("стакан") || w.startsWith("кружк") || w.startsWith("чашк") -> listOf("cup")
            else -> return null
        }
        return servings.firstOrNull { s ->
            s.kind == ServingKind.HOUSEHOLD && keys.any { k -> Regex("\\b$k\\b").containsMatchIn(s.label.lowercase()) }
        }
    }

    private fun formatCount(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else v.toString().replace('.', ',')
}
