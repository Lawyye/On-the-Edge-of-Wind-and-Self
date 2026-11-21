package com.nutritiontracker.core.calc

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.Nutrients

data class RecipeIngredientInput(
    val name: String,
    /** Количество в единице основы продукта ингредиента. */
    val amount: Double,
    val unit: AmountUnit,
    /** Пищевая ценность ингредиента на 100 г или 100 мл. */
    val per100: Nutrients,
)

data class RecipeCalculation(
    val ingredientCount: Int,
    /** Сумма масс сырых ингредиентов, если все они указаны в граммах; иначе null. */
    val rawMassGrams: Double?,
    /** Есть ингредиенты в миллилитрах: их нельзя сложить с граммами без плотности. */
    val hasVolumeIngredients: Boolean,
    /** Пищевая ценность всего блюда. */
    val total: NutrientTotals,
    val cookedWeightGrams: Double?,
    /** На 100 г готового блюда; null, пока не указан вес готового блюда. */
    val per100Cooked: Nutrients?,
    /** Выход готового блюда, % от массы сырья (если обе массы известны). */
    val yieldPercent: Double?,
)

/**
 * Расчёт рецепта по фактическому весу готового блюда:
 * ценность на 100 г = суммарная ценность ингредиентов / вес готового блюда × 100.
 *
 * Вес готового блюда не подменяется суммой ингредиентов: при варке и
 * запекании масса меняется, и без взвешивания показатели на 100 г были бы неверны.
 */
object RecipeCalculator {
    const val MAX_COOKED_WEIGHT_G = 100_000.0

    fun calculate(ingredients: List<RecipeIngredientInput>, cookedWeightGrams: Double?): RecipeCalculation {
        ingredients.forEach {
            require(it.amount.isFinite() && it.amount > 0.0) { "Количество ингредиента «${it.name}» должно быть больше нуля" }
        }
        if (cookedWeightGrams != null) {
            require(cookedWeightGrams.isFinite() && cookedWeightGrams > 0.0) { "Вес готового блюда должен быть больше нуля" }
            require(cookedWeightGrams <= MAX_COOKED_WEIGHT_G) { "Вес готового блюда слишком большой: $cookedWeightGrams г" }
        }
        val scaled = ingredients.map { NutrientMath.forAmount(it.per100, it.amount) }
        val total = NutrientMath.total(scaled)
        val hasVolume = ingredients.any { it.unit == AmountUnit.MILLILITER }
        val rawMass = if (ingredients.isEmpty() || hasVolume) null else ingredients.sumOf { it.amount }
        val per100 = cookedWeightGrams?.takeIf { ingredients.isNotEmpty() }?.let { cooked ->
            total.asNutrients().scaled(100.0 / cooked)
        }
        return RecipeCalculation(
            ingredientCount = ingredients.size,
            rawMassGrams = rawMass,
            hasVolumeIngredients = hasVolume,
            total = total,
            cookedWeightGrams = cookedWeightGrams,
            per100Cooked = per100,
            yieldPercent = if (rawMass != null && rawMass > 0 && cookedWeightGrams != null) cookedWeightGrams / rawMass * 100.0 else null,
        )
    }
}
