package com.nutritiontracker.ui.navigation

import kotlinx.serialization.Serializable

/*
 * Маршруты навигации. Необязательные числовые параметры передаются как -1:
 * так навигация не зависит от поддержки nullable-типов в аргументах.
 */

const val NONE = -1L

@Serializable object OnboardingRoute
@Serializable object HomeRoute
@Serializable object DiaryRoute
@Serializable object FoodsRoute
@Serializable object StatsRoute
@Serializable object ProfileRoute

/** Режим выбора продукта: в дневник или ингредиентом в рецепт. */
object PickMode {
    const val BROWSE = "browse"
    const val DIARY = "diary"
    const val INGREDIENT = "ingredient"
}

@Serializable
data class AddFoodRoute(val epochDay: Long, val meal: String, val mode: String = PickMode.DIARY)

@Serializable
data class FoodDetailRoute(
    val foodId: Long,
    val mode: String = PickMode.BROWSE,
    val epochDay: Long = NONE,
    val meal: String = "",
    val searchQuery: String = "",
)

@Serializable
data class FoodEditorRoute(val foodId: Long = NONE)

@Serializable
data class RecipeEditorRoute(val recipeId: Long = NONE)

@Serializable
data class VoiceRoute(val epochDay: Long, val meal: String)

@Serializable
data class AnalysisRoute(val epochDay: Long, val days: Int = 1)

@Serializable object ProgressRoute
@Serializable object ProfileEditRoute
@Serializable object NormsRoute
@Serializable object SettingsRoute
@Serializable object BackupRoute
@Serializable object AboutRoute
