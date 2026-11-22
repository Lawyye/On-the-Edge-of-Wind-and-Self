package com.nutritiontracker.ui.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.core.calc.EnergyConsistency
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.NutrientGroup
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.data.FoodItem
import com.nutritiontracker.data.displayName
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.ChoiceChip
import com.nutritiontracker.ui.components.ConfirmDialog
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.NutrientValueRow
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.SkeletonBox
import com.nutritiontracker.ui.navigation.FoodDetailRoute
import com.nutritiontracker.ui.navigation.PickMode
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import java.time.Instant
import java.time.ZoneId

@Composable
fun FoodDetailScreen(
    route: FoodDetailRoute,
    onBack: () -> Unit,
    onAddedToDiary: () -> Unit,
    onIngredientPicked: (String) -> Unit,
    onEdit: (Long) -> Unit,
    onOpenFood: (Long) -> Unit,
    onOpenRecipe: (Long) -> Unit,
) {
    val vm = appViewModel(key = "food-${route.foodId}-${route.mode}") { c, _ -> FoodDetailViewModel(c, route) }
    val food by vm.food.collectAsStateWithLifecycle()
    val fav by vm.isFavorite.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val meal by vm.meal.collectAsStateWithLifecycle()
    val ui by vm.ui.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var perAmount by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                DetailEvent.Added -> onAddedToDiary()
                is DetailEvent.IngredientPicked -> onIngredientPicked(e.json)
                is DetailEvent.Message -> snackbar.showSnackbar(e.text)
                is DetailEvent.OpenOther -> onOpenFood(e.foodId)
                DetailEvent.Deleted -> onBack()
            }
        }
    }

    val f = food
    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(f?.displayName ?: "Продукт", onBack = onBack) {
                IconButton(onClick = vm::toggleFavorite) {
                    Icon(if (fav) Icons.Filled.Star else Icons.Outlined.StarBorder, if (fav) "Убрать из избранного" else "В избранное", tint = if (fav) c.warning else c.textSecondary)
                }
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "Действия", tint = c.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    val source = f?.source
                    if (source == FoodSource.USER) {
                        DropdownMenuItem(text = { Text("Изменить") }, onClick = { menu = false; onEdit(route.foodId) })
                    }
                    val recipeId = f?.recipeId
                    if (source == FoodSource.RECIPE && recipeId != null) {
                        DropdownMenuItem(text = { Text("Открыть рецепт") }, onClick = { menu = false; onOpenRecipe(recipeId) })
                    }
                    if (source?.isOnline == true) {
                        DropdownMenuItem(text = { Text("Русское название") }, onClick = { menu = false; renaming = true })
                        DropdownMenuItem(text = { Text(if (ui.refreshing) "Обновление…" else "Обновить из источника") }, onClick = { menu = false; vm.refresh() })
                        DropdownMenuItem(text = { Text("Скопировать как свой продукт") }, onClick = { menu = false; vm.duplicate() })
                    }
                    if (source != FoodSource.RECIPE) {
                        DropdownMenuItem(text = { Text("Удалить", color = c.error) }, onClick = { menu = false; confirmDelete = true })
                    }
                }
            }
        },
        bottomBar = {
            if (f != null) {
                val label = when (route.mode) {
                    PickMode.INGREDIENT -> "Добавить в рецепт"
                    else -> "Добавить в «${meal.titleRu}»"
                }
                Column(Modifier.background(c.background).navigationBarsPadding().imePadding().padding(Dimens.screenPadding)) {
                    PrimaryButton(
                        label,
                        onClick = { if (route.mode == PickMode.INGREDIENT) vm.pickAsIngredient() else vm.addToDiary() },
                        enabled = amount.valid && !ui.saving,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    ) { padding ->
        if (f == null) {
            Column(Modifier.padding(padding).padding(Dimens.screenPadding)) {
                SkeletonBox(Modifier.fillMaxWidth(), height = 120.dp)
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item { HeaderCard(f) }
            item {
                AmountCard(
                    f = f, amount = amount, meal = meal, mode = route.mode, dateTitle = Dates.title(vm.date),
                    loadingServings = ui.loadingDetails,
                    onAmount = vm::setAmountText, onServing = { s, n -> vm.useServing(s, n) }, onMeal = vm::setMeal,
                )
            }
            ui.detailsError?.let { err ->
                item {
                    InfoBanner(
                        "Полная карточка не загрузилась (${err.kind.messageRu.lowercase()}). Показаны данные из результатов поиска; справочные порции появятся после загрузки.",
                        kind = BannerKind.WARNING, action = "Повторить", onAction = vm::retryDetails,
                    )
                }
            }
            item { ConsistencyNotice(f.nutrients) }
            item {
                SegmentedControl(
                    listOf(false, true), perAmount,
                    { if (it) "На ${amount.value?.let { v -> NutrientFormat.amount(v) } ?: "—"} ${f.basis.symbol}" else "На 100 ${f.basis.symbol}" },
                    { perAmount = it },
                )
            }
            val shown = if (perAmount && amount.valid) NutrientMath.forAmount(f.nutrients, amount.value!!) else f.nutrients
            item { NutrientTable(shown) }
            item { SourceCard(f) }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }

    if (renaming) {
        var name by remember { mutableStateOf(f?.aliases?.firstOrNull() ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = c.surface,
            title = { Text("Русское название", style = AppTheme.type.section, color = c.textPrimary) },
            text = {
                Column {
                    Text("Будет показываться в дневнике и находиться в поиске без интернета. Исходное название сохранится.", style = AppTheme.type.caption, color = c.textSecondary)
                    Spacer(Modifier.height(10.dp))
                    AppTextField(name, { name = it }, "Название")
                }
            },
            confirmButton = { TextButton(onClick = { vm.setRussianName(name); renaming = false }) { Text("Сохранить", color = c.accent) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Отмена", color = c.textSecondary) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить продукт?",
            text = "Продукт исчезнет из поиска и избранного. Записи дневника с ним сохранятся — в них хранится снимок пищевой ценности.",
            confirm = "Удалить",
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun HeaderCard(f: FoodItem) {
    val c = AppTheme.colors
    AppCard {
        Text(f.displayName, style = AppTheme.type.title, color = c.textPrimary)
        if (f.displayName != f.data.name) Text(f.data.name, style = AppTheme.type.caption, color = c.textSecondary)
        val meta = listOfNotNull(f.data.brand, f.data.category).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(meta, style = AppTheme.type.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(12.dp))
        val n = f.nutrients
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            BigValue(n[Nutrient.ENERGY]?.let { NutrientFormat.kcal(it) } ?: "—", "ккал")
            BigValue(n[Nutrient.PROTEIN]?.let { NutrientFormat.amount(it) } ?: "—", "белки")
            BigValue(n[Nutrient.FAT]?.let { NutrientFormat.amount(it) } ?: "—", "жиры")
            BigValue(n[Nutrient.CARBS]?.let { NutrientFormat.amount(it) } ?: "—", "углеводы")
        }
        Spacer(Modifier.height(4.dp))
        Text(f.basis.per100Ru, style = AppTheme.type.caption, color = c.textTertiary)
    }
}

@Composable
private fun BigValue(value: String, label: String) {
    Column {
        Text(value, style = AppTheme.type.numberLarge, color = AppTheme.colors.textPrimary)
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun AmountCard(
    f: FoodItem,
    amount: AmountInput,
    meal: MealType,
    mode: String,
    dateTitle: String,
    loadingServings: Boolean,
    onAmount: (String) -> Unit,
    onServing: (com.nutritiontracker.core.model.ServingSize, Double) -> Unit,
    onMeal: (MealType) -> Unit,
) {
    val c = AppTheme.colors
    AppCard {
        Text(if (mode == PickMode.INGREDIENT) "Сколько в рецепте" else "Сколько съедено · $dateTitle", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        AppTextField(
            amount.text, onAmount, "Количество", suffix = f.basis.symbol, numeric = true,
            error = if (amount.text.isNotEmpty() && !amount.valid) "Введите число больше нуля (до 10 000)" else null,
            supporting = amount.servingLabel,
        )
        if (loadingServings) {
            Spacer(Modifier.height(8.dp))
            SkeletonBox(Modifier.fillMaxWidth(0.7f), height = 36.dp)
        } else if (f.data.servings.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Справочные порции", style = AppTheme.type.label, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                f.data.servings.forEach { s ->
                    ChoiceChip(
                        "${s.label} · ${NutrientFormat.amount(s.amount)} ${f.basis.symbol}",
                        selected = amount.servingLabel == s.label,
                        onClick = { onServing(s, 1.0) },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text("Вес порции взят из источника: ${f.data.servings.map { it.sourceNote }.distinct().joinToString(", ")}. Проверьте, подходит ли он вам.", style = AppTheme.type.caption, color = c.textTertiary)
        }
        amount.value?.takeIf { amount.valid }?.let { v ->
            Spacer(Modifier.height(10.dp))
            val eaten = NutrientMath.forAmount(f.nutrients, v)
            Text(
                "${NutrientFormat.withUnit(eaten[Nutrient.ENERGY], Nutrient.ENERGY)} · Б ${eaten[Nutrient.PROTEIN]?.let { NutrientFormat.amount(it) } ?: "—"} · " +
                    "Ж ${eaten[Nutrient.FAT]?.let { NutrientFormat.amount(it) } ?: "—"} · У ${eaten[Nutrient.CARBS]?.let { NutrientFormat.amount(it) } ?: "—"}",
                style = AppTheme.type.number, color = c.textPrimary,
            )
        }
        if (mode != PickMode.INGREDIENT) {
            Spacer(Modifier.height(12.dp))
            SegmentedControl(MealType.entries, meal, { it.titleRu }, onMeal)
        }
    }
}

@Composable
private fun ConsistencyNotice(n: Nutrients) {
    val r = EnergyConsistency.check(n)
    if (r.status == EnergyConsistency.Status.MISMATCH) {
        InfoBanner(
            "Калорийность (${NutrientFormat.kcal(r.declaredKcal ?: 0.0)} ккал) заметно расходится с БЖУ " +
                "(ожидалось ${NutrientFormat.kcal(r.expectedMinKcal ?: 0.0)}–${NutrientFormat.kcal(r.expectedMaxKcal ?: 0.0)} ккал). " +
                "Возможна ошибка в источнике — например, кДж вместо ккал или значения на порцию. Калорийность не пересчитывается автоматически.",
            kind = BannerKind.WARNING,
        )
    }
}

@Composable
fun NutrientTable(n: Nutrients) {
    val c = AppTheme.colors
    val known = Nutrient.entries.count { n.isKnown(it) }
    AppCard {
        Text("Состав", style = AppTheme.type.section, color = c.textPrimary)
        Text("Известно $known из ${Nutrient.entries.size} показателей. «Нет данных» — источник не сообщает значение; это не ноль.", style = AppTheme.type.caption, color = c.textSecondary)
        Spacer(Modifier.height(8.dp))
        GroupTitle("Энергия и макронутриенты")
        NutrientValueRow(Nutrient.ENERGY, n)
        NutrientValueRow(Nutrient.PROTEIN, n)
        NutrientValueRow(Nutrient.FAT, n)
        Nutrient.fatDetails.forEach { NutrientValueRow(it, n, indent = true) }
        NutrientValueRow(Nutrient.CARBS, n)
        NutrientValueRow(Nutrient.SUGARS, n, indent = true)
        NutrientValueRow(Nutrient.ADDED_SUGARS, n, indent = true)
        NutrientValueRow(Nutrient.FIBER, n)
        if (n.isKnown(Nutrient.ALCOHOL)) NutrientValueRow(Nutrient.ALCOHOL, n)
        GroupTitle(NutrientGroup.VITAMIN.titleRu)
        Nutrient.vitamins.forEach { NutrientValueRow(it, n) }
        GroupTitle(NutrientGroup.MINERAL.titleRu)
        Nutrient.minerals.forEach { NutrientValueRow(it, n) }
        if (n.incomplete.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("«≥» — данные есть не по всем ингредиентам рецепта, значение может быть больше.", style = AppTheme.type.caption, color = c.textTertiary)
        }
    }
}

@Composable
private fun GroupTitle(text: String) {
    Text(text, style = AppTheme.type.label, color = AppTheme.colors.accent, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun SourceCard(f: FoodItem) {
    val c = AppTheme.colors
    AppCard {
        Text("Источник данных", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        val lines = buildList {
            add(f.source.titleRu + (f.data.externalId?.let { if (f.source.isUsda) " · FDC ID $it" else if (f.source == FoodSource.OPEN_FOOD_FACTS) " · код $it" else "" } ?: ""))
            f.data.sourceUpdated?.let { add("Данные источника от: $it") }
            f.fetchedAt?.let { add("Загружено: ${Dates.full(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate())}") }
            if (f.source == FoodSource.USER) add("Значения введены вами.")
            addAll(f.data.dataNotes)
            if (f.aliases.isNotEmpty()) add("Другие названия: ${f.aliases.joinToString(", ")}")
        }
        lines.forEach { Text("• $it", style = AppTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(vertical = 2.dp)) }
        if (f.source.isUsda) {
            Spacer(Modifier.height(4.dp))
            Text("Проверить: fdc.nal.usda.gov/food-details/${f.data.externalId}", style = AppTheme.type.caption, color = c.textTertiary)
        } else if (f.source == FoodSource.OPEN_FOOD_FACTS) {
            Spacer(Modifier.height(4.dp))
            Text("Проверить: world.openfoodfacts.org/product/${f.data.externalId}", style = AppTheme.type.caption, color = c.textTertiary)
        }
    }
}
