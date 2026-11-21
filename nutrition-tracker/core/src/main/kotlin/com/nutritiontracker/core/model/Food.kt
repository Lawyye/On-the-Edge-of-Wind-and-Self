package com.nutritiontracker.core.model

enum class FoodSource(val key: String, val titleRu: String, val isOnline: Boolean) {
    USDA_FOUNDATION("usda_foundation", "USDA FoodData Central · Foundation", true),
    USDA_SR_LEGACY("usda_sr_legacy", "USDA FoodData Central · SR Legacy", true),
    USDA_SURVEY("usda_survey", "USDA FoodData Central · FNDDS", true),
    USDA_BRANDED("usda_branded", "USDA FoodData Central · Branded", true),
    OPEN_FOOD_FACTS("off", "Open Food Facts", true),
    USER("user", "Мой продукт", false),
    RECIPE("recipe", "Мой рецепт", false),
    ;

    val isUsda: Boolean get() = key.startsWith("usda")

    companion object {
        fun fromKey(key: String): FoodSource? = entries.firstOrNull { it.key == key }
    }
}

enum class ServingKind { PIECE, PORTION, PACKAGE, HOUSEHOLD }

/**
 * Справочный размер порции или штуки. [amount] — в единице основы продукта
 * (г или мл). [sourceNote] обязательно показывается пользователю: откуда взят вес.
 */
data class ServingSize(
    val label: String,
    val amount: Double,
    val kind: ServingKind,
    val sourceNote: String,
) {
    init {
        require(amount.isFinite() && amount > 0.0) { "Размер порции должен быть положительным: $amount" }
    }
}

/**
 * Продукт в том виде, в каком он приходит из источника или создаётся
 * пользователем. Пищевая ценность — на 100 единиц основы [basis].
 */
data class FoodData(
    val name: String,
    val nameEn: String? = null,
    val brand: String? = null,
    val category: String? = null,
    val source: FoodSource,
    val externalId: String? = null,
    val basis: AmountUnit = AmountUnit.GRAM,
    val nutrients: Nutrients,
    val servings: List<ServingSize> = emptyList(),
    /** Дата публикации/изменения данных у источника, как её сообщает источник. */
    val sourceUpdated: String? = null,
    /** Пометки о том, как получены отдельные значения (пересчёт кДж → ккал и т. п.). */
    val dataNotes: List<String> = emptyList(),
)

enum class MealType(val key: String, val titleRu: String, val order: Int) {
    BREAKFAST("breakfast", "Завтрак", 0),
    LUNCH("lunch", "Обед", 1),
    DINNER("dinner", "Ужин", 2),
    SNACK("snack", "Перекусы", 3),
    ;

    companion object {
        fun fromKey(key: String): MealType? = entries.firstOrNull { it.key == key }
    }
}
