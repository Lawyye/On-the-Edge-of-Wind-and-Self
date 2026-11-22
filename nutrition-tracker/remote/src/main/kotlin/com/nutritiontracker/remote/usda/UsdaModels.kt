package com.nutritiontracker.remote.usda

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// Модели ответов USDA FoodData Central API v1. Описаны только используемые поля;
// остальные пропускаются (ignoreUnknownKeys).

@Serializable
data class UsdaSearchResponse(
    val totalHits: Int = 0,
    val currentPage: Int = 1,
    val totalPages: Int = 0,
    val foods: List<UsdaSearchFood> = emptyList(),
)

@Serializable
data class UsdaSearchFood(
    val fdcId: Long,
    val description: String,
    val dataType: String? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val foodCategory: String? = null,
    val publishedDate: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaSearchNutrient> = emptyList(),
)

@Serializable
data class UsdaSearchNutrient(
    val nutrientId: Int? = null,
    val nutrientName: String? = null,
    val nutrientNumber: String? = null,
    val unitName: String? = null,
    val value: Double? = null,
)

@Serializable
data class UsdaFoodDetail(
    val fdcId: Long,
    val description: String,
    val dataType: String? = null,
    val publicationDate: String? = null,
    val modifiedDate: String? = null,
    /** У Foundation/SR Legacy — объект {description}, у других типов может отсутствовать. */
    val foodCategory: JsonElement? = null,
    val brandedFoodCategory: String? = null,
    val wweiaFoodCategory: JsonElement? = null,
    val brandOwner: String? = null,
    val brandName: String? = null,
    val servingSize: Double? = null,
    val servingSizeUnit: String? = null,
    val householdServingFullText: String? = null,
    val foodNutrients: List<UsdaDetailNutrient> = emptyList(),
    val foodPortions: List<UsdaPortion> = emptyList(),
)

@Serializable
data class UsdaDetailNutrient(
    val nutrient: UsdaNutrientInfo? = null,
    val amount: Double? = null,
)

@Serializable
data class UsdaNutrientInfo(
    val id: Int? = null,
    val number: String? = null,
    val name: String? = null,
    val unitName: String? = null,
)

@Serializable
data class UsdaPortion(
    val id: Long? = null,
    val amount: Double? = null,
    val gramWeight: Double? = null,
    val modifier: String? = null,
    val portionDescription: String? = null,
    val measureUnit: UsdaMeasureUnit? = null,
    val sequenceNumber: Int? = null,
)

@Serializable
data class UsdaMeasureUnit(
    val name: String? = null,
    val abbreviation: String? = null,
)

@Serializable
data class UsdaSearchRequest(
    val query: String,
    val dataType: List<String>,
    val pageSize: Int,
    val pageNumber: Int = 1,
)

@Serializable
data class UsdaFoodsRequest(
    val fdcIds: List<Long>,
    val format: String = "full",
)
