package com.nutritiontracker.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * Пищевая ценность хранится в колонках nutrients* как JSON-словарь
 * «ключ нутриента → значение». Отсутствующий ключ = нет данных (NULL),
 * значение 0 = содержание действительно нулевое.
 */

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = 1,
    val sex: String,
    val birthEpochDay: Long,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activityLevel: String,
    val deficitMode: String,
    val customDeficitKcal: Double?,
    val manualGoalKcal: Double?,
    val proteinPercent: Int,
    val fatPercent: Int,
    val carbsPercent: Int,
    val waterGoalMl: Int,
    val adaptiveEnabled: Boolean,
    val appliedAdaptiveTdee: Double?,
    val adaptiveUpdatedEpochDay: Long?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "foods",
    indices = [
        Index(value = ["source", "externalId"], unique = true),
        Index(value = ["recipeId"]),
        Index(value = ["lastUsedAt"]),
    ],
)
data class FoodEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val nameEn: String?,
    /** JSON-список альтернативных названий. */
    val aliases: String,
    val brand: String?,
    val category: String?,
    val source: String,
    val externalId: String?,
    val basis: String,
    val nutrients: String,
    val incompleteNutrients: String,
    /** JSON-список справочных порций. */
    val servings: String,
    val sourceUpdated: String?,
    val dataNotes: String,
    val recipeId: Long?,
    /** false — запись из результатов поиска USDA, полная карточка ещё не загружена. */
    val detailsLoaded: Boolean,
    val fetchedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val useCount: Int,
    val lastUsedAt: Long?,
    /** Основы слов для локального поиска. */
    val searchIndex: String,
)

/** Лёгкая проекция для поиска по локальной базе. */
data class FoodSearchRow(
    val id: Long,
    val name: String,
    val searchIndex: String,
    val source: String,
    val useCount: Int,
    val lastUsedAt: Long?,
)

/**
 * Запись дневника хранит снимок продукта на момент добавления: если продукт
 * потом обновится или будет удалён, история не изменится.
 */
@Entity(
    tableName = "food_entries",
    indices = [Index(value = ["epochDay"]), Index(value = ["foodId"])],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long?,
    val epochDay: Long,
    val mealType: String,
    val amount: Double,
    val unit: String,
    val foodName: String,
    val brand: String?,
    val source: String,
    val nutrientsPer100: String,
    val incompleteNutrients: String,
    val servingLabel: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "recipes")
data class RecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cookedWeightG: Double?,
    val servingsCount: Int?,
    /** Продукт-представление рецепта для поиска и дневника. */
    val foodId: Long?,
    val notes: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "recipe_ingredients",
    indices = [Index(value = ["recipeId"])],
    foreignKeys = [
        ForeignKey(
            entity = RecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class RecipeIngredientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val foodId: Long?,
    val name: String,
    val amount: Double,
    val unit: String,
    val nutrientsPer100: String,
    val incompleteNutrients: String,
    val position: Int,
)

@Entity(tableName = "weight_entries", indices = [Index(value = ["epochDay"], unique = true)])
data class WeightEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val weightKg: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val chestCm: Double?,
    val neckCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
    val note: String?,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "water_entries", indices = [Index(value = ["epochDay"])])
data class WaterEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val epochDay: Long,
    val amountMl: Int,
    val createdAt: Long,
)

@Entity(tableName = "nutrient_goals", indices = [Index(value = ["nutrientKey"], unique = true)])
data class NutrientGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val nutrientKey: String,
    val target: Double?,
    val maximum: Double?,
    val unit: String,
    val source: String,
    val updatedAt: Long,
)

@Entity(
    tableName = "favorites",
    indices = [Index(value = ["foodId"], unique = true)],
    foreignKeys = [
        ForeignKey(
            entity = FoodEntity::class,
            parentColumns = ["id"],
            childColumns = ["foodId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class FavoriteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val foodId: Long,
    val createdAt: Long,
)

@Entity(tableName = "search_history", indices = [Index(value = ["query"], unique = true)])
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val lastUsedAt: Long,
)

@Entity(tableName = "meal_templates")
data class MealTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val mealType: String?,
    val createdAt: Long,
)

@Entity(
    tableName = "meal_template_items",
    indices = [Index(value = ["templateId"])],
    foreignKeys = [
        ForeignKey(
            entity = MealTemplateEntity::class,
            parentColumns = ["id"],
            childColumns = ["templateId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class MealTemplateItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val templateId: Long,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val source: String,
    val amount: Double,
    val unit: String,
    val nutrientsPer100: String,
    val incompleteNutrients: String,
    val position: Int,
)

/** Сумма воды по дням (для статистики). */
data class DayWater(
    @ColumnInfo(name = "epochDay") val epochDay: Long,
    @ColumnInfo(name = "total") val total: Int,
)
