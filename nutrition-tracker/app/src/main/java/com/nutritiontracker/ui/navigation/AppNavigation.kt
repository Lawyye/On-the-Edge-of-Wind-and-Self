package com.nutritiontracker.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.ui.analysis.AnalysisScreen
import com.nutritiontracker.ui.appContainer
import com.nutritiontracker.ui.diary.DiaryScreen
import com.nutritiontracker.ui.foods.AddFoodScreen
import com.nutritiontracker.ui.foods.FoodDetailScreen
import com.nutritiontracker.ui.foods.FoodEditorScreen
import com.nutritiontracker.ui.foods.FoodsScreen
import com.nutritiontracker.ui.home.HomeScreen
import com.nutritiontracker.ui.onboarding.OnboardingScreen
import com.nutritiontracker.ui.profile.AboutScreen
import com.nutritiontracker.ui.profile.BackupScreen
import com.nutritiontracker.ui.profile.NormsScreen
import com.nutritiontracker.ui.profile.ProfileEditScreen
import com.nutritiontracker.ui.profile.ProfileScreen
import com.nutritiontracker.ui.profile.SettingsScreen
import com.nutritiontracker.ui.progress.ProgressScreen
import com.nutritiontracker.ui.recipes.PICKED_INGREDIENT_KEY
import com.nutritiontracker.ui.recipes.RecipeEditorScreen
import com.nutritiontracker.ui.stats.StatsScreen
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.voice.VoiceScreen
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalTime

private data class TopLevel(val route: Any, val label: String, val icon: ImageVector)

private val topLevel = listOf(
    TopLevel(HomeRoute, "Главная", Icons.Outlined.Home),
    TopLevel(DiaryRoute, "Дневник", Icons.AutoMirrored.Outlined.MenuBook),
    TopLevel(FoodsRoute, "Продукты", Icons.Outlined.Restaurant),
    TopLevel(StatsRoute, "Статистика", Icons.Outlined.BarChart),
    TopLevel(ProfileRoute, "Профиль", Icons.Outlined.Person),
)

/** Приём пищи по времени суток — для кнопок «добавить» без явного выбора. */
fun mealForNow(time: LocalTime = LocalTime.now()): MealType = when (time.hour) {
    in 4..10 -> MealType.BREAKFAST
    in 11..15 -> MealType.LUNCH
    in 16..21 -> MealType.DINNER
    else -> MealType.SNACK
}

private sealed interface StartState {
    data object Loading : StartState
    data class Ready(val hasProfile: Boolean) : StartState
}

@Composable
fun AppRoot() {
    val container = appContainer()
    val flow = remember { container.profile.observe().map<_, StartState> { StartState.Ready(it != null) } }
    val start by flow.collectAsStateWithLifecycle(initialValue = StartState.Loading)
    when (val s = start) {
        StartState.Loading -> Box(Modifier.fillMaxSize().background(AppTheme.colors.background))
        is StartState.Ready -> {
            val startRoute: Any = remember { if (s.hasProfile) HomeRoute else OnboardingRoute }
            AppNavHost(startRoute)
        }
    }
}

private fun NavHostController.switchTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun AppNavHost(startRoute: Any) {
    val nav = rememberNavController()
    val c = AppTheme.colors
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val showBar = topLevel.any { t -> destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true }

    fun addFood(day: LocalDate, meal: MealType) = nav.navigate(AddFoodRoute(day.toEpochDay(), meal.key, PickMode.DIARY))
    fun voice(day: LocalDate, meal: MealType) = nav.navigate(VoiceRoute(day.toEpochDay(), meal.key))
    fun analysis(day: LocalDate, days: Int) = nav.navigate(AnalysisRoute(day.toEpochDay(), days))

    Scaffold(
        containerColor = c.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            if (showBar) {
                NavigationBar(containerColor = c.surface, tonalElevation = androidx.compose.ui.unit.Dp(0f)) {
                    topLevel.forEach { t ->
                        val selected = destination?.hierarchy?.any { it.hasRoute(t.route::class) } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.switchTab(t.route) },
                            icon = { Icon(t.icon, contentDescription = null) },
                            label = { Text(t.label, style = AppTheme.type.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = c.accent,
                                selectedTextColor = c.textPrimary,
                                indicatorColor = c.accentMuted,
                                unselectedIconColor = c.textSecondary,
                                unselectedTextColor = c.textSecondary,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = startRoute,
            modifier = Modifier.padding(padding),
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable<OnboardingRoute>(enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None }) {
                OnboardingScreen(
                    onDone = { nav.navigate(HomeRoute) { popUpTo<OnboardingRoute> { inclusive = true } } },
                    onRestore = { nav.navigate(BackupRoute) },
                )
            }
            composable<HomeRoute> {
                HomeScreen(
                    onAddFood = ::addFood,
                    onVoice = ::voice,
                    onOpenDiary = { nav.switchTab(DiaryRoute) },
                    onOpenAnalysis = { analysis(it, 1) },
                    onOpenProgress = { nav.navigate(ProgressRoute) },
                )
            }
            composable<DiaryRoute> {
                DiaryScreen(
                    onAddFood = ::addFood,
                    onVoice = ::voice,
                    onOpenAnalysis = { analysis(it, 1) },
                    onOpenFood = { nav.navigate(FoodDetailRoute(it)) },
                )
            }
            composable<FoodsRoute> {
                FoodsScreen(
                    onOpenFood = { id, q -> nav.navigate(FoodDetailRoute(id, PickMode.BROWSE, searchQuery = q)) },
                    onCreateFood = { nav.navigate(FoodEditorRoute()) },
                    onCreateRecipe = { nav.navigate(RecipeEditorRoute()) },
                    onOpenRecipe = { nav.navigate(RecipeEditorRoute(it)) },
                )
            }
            composable<StatsRoute> {
                StatsScreen(
                    onOpenAnalysis = { day, days -> analysis(day, days) },
                    onOpenProgress = { nav.navigate(ProgressRoute) },
                )
            }
            composable<ProfileRoute> {
                ProfileScreen(
                    onEdit = { nav.navigate(ProfileEditRoute) },
                    onNorms = { nav.navigate(NormsRoute) },
                    onSettings = { nav.navigate(SettingsRoute) },
                    onBackup = { nav.navigate(BackupRoute) },
                    onAbout = { nav.navigate(AboutRoute) },
                    onProgress = { nav.navigate(ProgressRoute) },
                )
            }
            composable<AddFoodRoute> { entry ->
                val route = entry.toRoute<AddFoodRoute>()
                AddFoodScreen(
                    route = route,
                    onBack = { nav.popBackStack() },
                    onOpenFood = { id, q -> nav.navigate(FoodDetailRoute(id, route.mode, route.epochDay, route.meal, q)) },
                    onCreateFood = { nav.navigate(FoodEditorRoute()) },
                    onVoice = { nav.navigate(VoiceRoute(route.epochDay, route.meal)) },
                )
            }
            composable<FoodDetailRoute> { entry ->
                val route = entry.toRoute<FoodDetailRoute>()
                FoodDetailScreen(
                    route = route,
                    onBack = { nav.popBackStack() },
                    onAddedToDiary = {
                        if (!nav.popBackStack<AddFoodRoute>(inclusive = true)) nav.popBackStack()
                    },
                    onIngredientPicked = { json ->
                        val editor = runCatching { nav.getBackStackEntry<RecipeEditorRoute>() }.getOrNull()
                        editor?.savedStateHandle?.set(PICKED_INGREDIENT_KEY, json)
                        if (!nav.popBackStack<RecipeEditorRoute>(inclusive = false)) nav.popBackStack()
                    },
                    onEdit = { nav.navigate(FoodEditorRoute(it)) },
                    onOpenFood = { nav.navigate(FoodDetailRoute(it, route.mode, route.epochDay, route.meal)) { popUpTo<FoodDetailRoute> { inclusive = true } } },
                    onOpenRecipe = { nav.navigate(RecipeEditorRoute(it)) },
                )
            }
            composable<FoodEditorRoute> { entry ->
                val route = entry.toRoute<FoodEditorRoute>()
                FoodEditorScreen(
                    foodId = route.foodId,
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable<RecipeEditorRoute> { entry ->
                val route = entry.toRoute<RecipeEditorRoute>()
                RecipeEditorScreen(
                    recipeId = route.recipeId,
                    savedStateHandle = entry.savedStateHandle,
                    onBack = { nav.popBackStack() },
                    onPickIngredient = { nav.navigate(AddFoodRoute(LocalDate.now().toEpochDay(), "", PickMode.INGREDIENT)) },
                    onAddPortion = { foodId ->
                        nav.navigate(FoodDetailRoute(foodId, PickMode.DIARY, LocalDate.now().toEpochDay(), mealForNow().key))
                    },
                )
            }
            composable<VoiceRoute> { entry ->
                val route = entry.toRoute<VoiceRoute>()
                VoiceScreen(
                    route = route,
                    onBack = { nav.popBackStack() },
                    onSaved = {
                        if (!nav.popBackStack<AddFoodRoute>(inclusive = true)) nav.popBackStack()
                    },
                )
            }
            composable<AnalysisRoute> { entry ->
                AnalysisScreen(route = entry.toRoute(), onBack = { nav.popBackStack() })
            }
            composable<ProgressRoute> { ProgressScreen(onBack = { nav.popBackStack() }) }
            composable<ProfileEditRoute> { ProfileEditScreen(onBack = { nav.popBackStack() }) }
            composable<NormsRoute> { NormsScreen(onBack = { nav.popBackStack() }) }
            composable<SettingsRoute> { SettingsScreen(onBack = { nav.popBackStack() }) }
            composable<BackupRoute> { BackupScreen(onBack = { nav.popBackStack() }) }
            composable<AboutRoute> { AboutScreen(onBack = { nav.popBackStack() }) }
        }
    }
}
