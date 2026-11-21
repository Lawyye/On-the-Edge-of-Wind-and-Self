package com.nutritiontracker.core.backup

import kotlinx.serialization.Serializable

/*
 * Формат резервной копии. Поля повторяют таблицы базы данных, чтобы копия
 * переносилась на другой телефон без потерь. Пищевая ценность хранится как
 * словарь «ключ нутриента → значение»: отсутствующий ключ означает
 * «нет данных», а не ноль.
 */

@Serializable
data class BackupFile(
    val format: String = FORMAT,
    val schemaVersion: Int = SCHEMA_VERSION,
    val appVersion: String,
    /** Время создания, миллисекунды Unix. */
    val exportedAt: Long,
    /** SHA-256 канонического JSON поля [payload] в шестнадцатеричном виде. */
    val payloadSha256: String,
    val payload: BackupPayload,
) {
    companion object {
        const val FORMAT = "nutrition-tracker-backup"
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupPayload(
    val profile: ProfileDto? = null,
    val foods: List<FoodDto> = emptyList(),
    val entries: List<FoodEntryDto> = emptyList(),
    val recipes: List<RecipeDto> = emptyList(),
    val recipeIngredients: List<RecipeIngredientDto> = emptyList(),
    val weights: List<WeightDto> = emptyList(),
    val water: List<WaterDto> = emptyList(),
    val nutrientGoals: List<NutrientGoalDto> = emptyList(),
    val favorites: List<FavoriteDto> = emptyList(),
    val searchHistory: List<SearchHistoryDto> = emptyList(),
    val mealTemplates: List<MealTemplateDto> = emptyList(),
    val mealTemplateItems: List<MealTemplateItemDto> = emptyList(),
    val settings: SettingsDto? = null,
)

@Serializable
data class ProfileDto(
    val sex: String,
    val birthEpochDay: Long,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activityLevel: String,
    val deficitMode: String,
    val customDeficitKcal: Double? = null,
    val manualGoalKcal: Double? = null,
    val proteinPercent: Int,
    val fatPercent: Int,
    val carbsPercent: Int,
    val waterGoalMl: Int,
    val adaptiveEnabled: Boolean,
    val appliedAdaptiveTdee: Double? = null,
    val adaptiveUpdatedEpochDay: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ServingDto(val label: String, val amount: Double, val kind: String, val sourceNote: String)

@Serializable
data class FoodDto(
    val id: Long,
    val name: String,
    val nameEn: String? = null,
    val aliases: List<String> = emptyList(),
    val brand: String? = null,
    val category: String? = null,
    val source: String,
    val externalId: String? = null,
    val basis: String,
    val nutrients: Map<String, Double>,
    val incompleteNutrients: List<String> = emptyList(),
    val servings: List<ServingDto> = emptyList(),
    val sourceUpdated: String? = null,
    val dataNotes: List<String> = emptyList(),
    val recipeId: Long? = null,
    val detailsLoaded: Boolean = true,
    val fetchedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val useCount: Int = 0,
    val lastUsedAt: Long? = null,
)

@Serializable
data class FoodEntryDto(
    val id: Long,
    val foodId: Long? = null,
    val epochDay: Long,
    val mealType: String,
    val amount: Double,
    val unit: String,
    val foodName: String,
    val brand: String? = null,
    val source: String,
    val nutrientsPer100: Map<String, Double>,
    val incompleteNutrients: List<String> = emptyList(),
    val servingLabel: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RecipeDto(
    val id: Long,
    val name: String,
    val cookedWeightG: Double? = null,
    val servingsCount: Int? = null,
    val foodId: Long? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class RecipeIngredientDto(
    val id: Long,
    val recipeId: Long,
    val foodId: Long? = null,
    val name: String,
    val amount: Double,
    val unit: String,
    val nutrientsPer100: Map<String, Double>,
    val incompleteNutrients: List<String> = emptyList(),
    val position: Int,
)

@Serializable
data class WeightDto(
    val id: Long,
    val epochDay: Long,
    val weightKg: Double? = null,
    val waistCm: Double? = null,
    val hipsCm: Double? = null,
    val chestCm: Double? = null,
    val neckCm: Double? = null,
    val armCm: Double? = null,
    val thighCm: Double? = null,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class WaterDto(val id: Long, val epochDay: Long, val amountMl: Int, val createdAt: Long)

@Serializable
data class NutrientGoalDto(
    val id: Long,
    val nutrientKey: String,
    val target: Double? = null,
    val maximum: Double? = null,
    val unit: String,
    val source: String,
    val updatedAt: Long,
)

@Serializable
data class FavoriteDto(val id: Long, val foodId: Long, val createdAt: Long)

@Serializable
data class SearchHistoryDto(val id: Long, val query: String, val lastUsedAt: Long)

@Serializable
data class MealTemplateDto(val id: Long, val name: String, val mealType: String? = null, val createdAt: Long)

@Serializable
data class MealTemplateItemDto(
    val id: Long,
    val templateId: Long,
    val foodId: Long? = null,
    val name: String,
    val brand: String? = null,
    val source: String,
    val amount: Double,
    val unit: String,
    val nutrientsPer100: Map<String, Double>,
    val incompleteNutrients: List<String> = emptyList(),
    val position: Int,
)

@Serializable
data class SettingsDto(
    val onlineSearchEnabled: Boolean = true,
    val usdaEnabled: Boolean = true,
    val openFoodFactsEnabled: Boolean = true,
    val autoRefreshEnabled: Boolean = true,
)
