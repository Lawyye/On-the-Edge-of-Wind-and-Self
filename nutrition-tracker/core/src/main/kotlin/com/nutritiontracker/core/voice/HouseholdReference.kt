package com.nutritiontracker.core.voice

import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize

/**
 * Справочные веса ложек для продуктов, которые обычно меряют ложками.
 * Значения — порции USDA SR Legacy для соответствующих продуктов. Нужны,
 * когда выбран товар без бытовых мер (например, из Open Food Facts):
 * «2 чайные ложки подсолнечного масла» → 2 × 4,5 г с пояснением источника.
 * Ключ — английское название продукта из словаря FoodQueryTranslator.
 */
object HouseholdReference {
    private const val NOTE = "справочно, USDA SR Legacy"

    private fun spoons(tbsp: Double, tsp: Double) = listOf(
        ServingSize("1 tbsp", tbsp, ServingKind.HOUSEHOLD, NOTE),
        ServingSize("1 tsp", tsp, ServingKind.HOUSEHOLD, NOTE),
    )

    private val table: Map<String, List<ServingSize>> = mapOf(
        "olive oil" to spoons(13.5, 4.5),
        "sunflower oil" to spoons(13.6, 4.5),
        "coconut oil" to spoons(13.6, 4.5),
        "butter" to spoons(14.2, 4.7),
        "sugar" to spoons(12.5, 4.2),
        "honey" to spoons(21.0, 7.1),
        "salt table" to spoons(18.0, 6.0),
        "sour cream" to spoons(12.0, 4.0),
        "mayonnaise" to spoons(13.8, 4.6),
    )

    /** Справочные ложки для продукта словаря или пустой список. */
    fun servingsFor(englishQuery: String?): List<ServingSize> {
        val q = englishQuery?.lowercase() ?: return emptyList()
        return table.entries.firstOrNull { (key, _) -> q == key || q.startsWith("$key ") }?.value.orEmpty()
    }
}
