package com.nutritiontracker.ui.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.data.Recipe
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SectionHeader
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.navigation.AddFoodRoute
import com.nutritiontracker.ui.navigation.PickMode
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import java.time.LocalDate

private enum class FoodTab(val title: String) { SEARCH("Поиск"), FAVORITES("Избранное"), MINE("Мои"), RECIPES("Рецепты") }

@Composable
fun FoodsScreen(
    onOpenFood: (Long, String) -> Unit,
    onCreateFood: () -> Unit,
    onCreateRecipe: () -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> FoodSearchViewModel(c) }
    val c = AppTheme.colors
    var tab by rememberSaveable { mutableStateOf(FoodTab.SEARCH) }
    val query by vm.query.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().background(c.background).statusBarsPadding().imePadding()) {
        Column(Modifier.padding(horizontal = Dimens.screenPadding).padding(top = 12.dp)) {
            Text("Продукты", style = AppTheme.type.title, color = c.textPrimary)
            Spacer(Modifier.height(12.dp))
            SegmentedControl(FoodTab.entries, tab, { it.title }, { tab = it })
            Spacer(Modifier.height(12.dp))
            if (tab == FoodTab.SEARCH) {
                SearchField(query, vm::setQuery)
                if (!isOnline) OfflineHint()
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
        ) {
            when (tab) {
                FoodTab.SEARCH -> {
                    if (query.trim().length < 2) {
                        item { HistoryChips(lists.history, vm::setQuery, vm::clearHistory) }
                        if (lists.recent.isNotEmpty()) {
                            item { SectionHeader("Недавние") }
                            foodList(lists.recent, lists.favoriteIds, "recent", { id -> vm.openLocal(id) { onOpenFood(it, "") } }, vm::toggleFavorite)
                        }
                        if (lists.recent.isEmpty() && lists.history.isEmpty()) {
                            item {
                                EmptyState(
                                    Icons.Outlined.Restaurant, "Найдите первый продукт",
                                    "Поиск идёт по сохранённым продуктам, а при наличии интернета — ещё и по USDA и Open Food Facts. Найденные продукты сохраняются и потом доступны офлайн.",
                                )
                            }
                        }
                    } else {
                        searchResults(
                            query = query,
                            local = local.map { it.food },
                            online = online,
                            favoriteIds = lists.favoriteIds,
                            onOpenLocal = { id -> vm.openLocal(id) { onOpenFood(it, query) } },
                            onOpenRemote = { rf -> vm.openRemote(rf) { onOpenFood(it, query) } },
                            onToggleFavorite = vm::toggleFavorite,
                            onRetry = vm::retryOnline,
                            onCreateFood = onCreateFood,
                        )
                    }
                }
                FoodTab.FAVORITES -> {
                    if (lists.favorites.isEmpty()) {
                        item { EmptyState(Icons.Outlined.StarBorder, "Избранного пока нет", "Отмечайте звёздочкой продукты, которые едите часто.") }
                    } else {
                        foodList(lists.favorites, lists.favoriteIds, "fav", { onOpenFood(it, "") }, vm::toggleFavorite)
                    }
                }
                FoodTab.MINE -> {
                    item {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            SecondaryButton("Создать продукт", onClick = onCreateFood, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (lists.userFoods.isEmpty()) {
                        item { EmptyState(Icons.Outlined.Restaurant, "Своих продуктов нет", "Создайте продукт, если его нет в базах, или скопируйте найденный и поправьте значения.") }
                    } else {
                        foodList(lists.userFoods, lists.favoriteIds, "mine", { onOpenFood(it, "") }, vm::toggleFavorite)
                    }
                }
                FoodTab.RECIPES -> {
                    item {
                        Column(Modifier.padding(bottom = 12.dp)) {
                            SecondaryButton("Новый рецепт", onClick = onCreateRecipe, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (lists.recipes.isEmpty()) {
                        item {
                            EmptyState(
                                Icons.AutoMirrored.Outlined.MenuBook, "Рецептов нет",
                                "Добавьте ингредиенты и вес готового блюда — приложение посчитает ценность на 100 г.",
                            )
                        }
                    } else {
                        items(lists.recipes, key = { "recipe-${it.id}" }) { r ->
                            Column(Modifier.padding(bottom = 8.dp)) { RecipeRow(r, onClick = { onOpenRecipe(r.id) }) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun RecipeRow(r: Recipe, onClick: () -> Unit) {
    FoodRow(
        title = r.name,
        subtitle = r.cookedWeightG?.let { "Готовое блюдо ${NutrientFormat.amount(it)} г" + (r.servingsCount?.let { n -> " · $n порц." } ?: "") }
            ?: "Не указан вес готового блюда",
        nutrition = if (r.foodId == null) "Укажите вес готового блюда, чтобы добавлять порции в дневник" else "Открыть рецепт",
        onClick = onClick,
    )
}

/** Выбор продукта для дневника или для рецепта. */
@Composable
fun AddFoodScreen(
    route: AddFoodRoute,
    onBack: () -> Unit,
    onOpenFood: (Long, String) -> Unit,
    onCreateFood: () -> Unit,
    onVoice: () -> Unit,
) {
    val vm = appViewModel { c, _ -> FoodSearchViewModel(c) }
    val c = AppTheme.colors
    val query by vm.query.collectAsStateWithLifecycle()
    val local by vm.local.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val isOnline by vm.isOnline.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    val forRecipe = route.mode == PickMode.INGREDIENT
    val meal = MealType.fromKey(route.meal)
    val title = if (forRecipe) "Ингредиент" else "${meal?.titleRu ?: "Добавить"} · ${Dates.title(LocalDate.ofEpochDay(route.epochDay))}"

    Column(Modifier.fillMaxSize().background(c.background).imePadding()) {
        AppTopBar(title, onBack = onBack)
        Column(Modifier.padding(horizontal = Dimens.screenPadding)) {
            SearchField(query, vm::setQuery)
            if (!isOnline) OfflineHint()
            Spacer(Modifier.height(10.dp))
            if (!forRecipe) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Голосом", onClick = onVoice, icon = Icons.Outlined.Mic, modifier = Modifier.weight(1f))
                    SecondaryButton("Свой продукт", onClick = onCreateFood, icon = Icons.Filled.Add, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, bottom = 24.dp),
        ) {
            if (query.trim().length < 2) {
                item {
                    Column(Modifier.padding(bottom = 10.dp)) {
                        SegmentedControl(listOf(0, 1, 2, 3), tab, { listOf("Недавние", "Избранное", "Мои", "Рецепты")[it] }, { tab = it })
                    }
                }
                val list = when (tab) {
                    0 -> lists.recent
                    1 -> lists.favorites
                    2 -> lists.userFoods
                    else -> emptyList()
                }
                if (tab == 3) {
                    val usable = lists.recipes.filter { it.foodId != null }
                    if (usable.isEmpty()) item { EmptyState(Icons.AutoMirrored.Outlined.MenuBook, "Нет готовых рецептов", "Рецепт появится здесь, когда в нём указан вес готового блюда.") }
                    items(usable, key = { "r-${it.id}" }) { r ->
                        Column(Modifier.padding(bottom = 8.dp)) { RecipeRow(r, onClick = { r.foodId?.let { onOpenFood(it, "") } }) }
                    }
                } else if (list.isEmpty()) {
                    item { HistoryChips(lists.history, vm::setQuery, vm::clearHistory) }
                    item { EmptyState(Icons.Outlined.Restaurant, "Здесь пока пусто", "Начните вводить название продукта.") }
                } else {
                    foodList(list, lists.favoriteIds, "add-$tab", { onOpenFood(it, "") }, vm::toggleFavorite)
                }
            } else {
                searchResults(
                    query = query,
                    local = local.map { it.food },
                    online = online,
                    favoriteIds = lists.favoriteIds,
                    onOpenLocal = { id -> vm.openLocal(id) { onOpenFood(it, query) } },
                    onOpenRemote = { rf -> vm.openRemote(rf) { onOpenFood(it, query) } },
                    onToggleFavorite = vm::toggleFavorite,
                    onRetry = vm::retryOnline,
                    onCreateFood = onCreateFood,
                )
            }
        }
    }
}
