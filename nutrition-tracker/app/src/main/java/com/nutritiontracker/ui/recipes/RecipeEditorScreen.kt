package com.nutritiontracker.ui.recipes

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.RecipeCalculation
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.data.RecipeDraft
import com.nutritiontracker.data.RecipeIngredient
import com.nutritiontracker.data.displayName
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.ConfirmDialog
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.foods.NutrientTable
import com.nutritiontracker.ui.foods.PickedIngredient
import com.nutritiontracker.ui.navigation.NONE
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

const val PICKED_INGREDIENT_KEY = "picked_ingredient"

data class RecipeEditorState(
    val draft: RecipeDraft = RecipeDraft(),
    val amounts: Map<Int, String> = emptyMap(),
    val cookedText: String = "",
    val servingsText: String = "",
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val savedFoodId: Long? = null,
    val dirty: Boolean = false,
) {
    val calculation: RecipeCalculation? get() = runCatching { draft.calculate() }.getOrNull()
    val cookedError: String? get() = cookedText.takeIf { it.isNotBlank() }?.let { t ->
        val v = parseDecimal(t)
        if (v == null || v <= 0 || v > 100_000) "Введите вес больше нуля" else null
    }
    val servingsError: String? get() = servingsText.takeIf { it.isNotBlank() }?.let { t ->
        val v = parseDecimal(t)
        if (v == null || v < 1 || v > 100 || v != Math.floor(v)) "Целое число от 1 до 100" else null
    }
    val amountErrors: Boolean get() = draft.ingredients.indices.any { i -> amounts[i]?.let { parseDecimal(it)?.let { v -> v <= 0 } ?: true } == true }
    val canSave: Boolean get() = loaded && !saving && draft.name.isNotBlank() && draft.ingredients.isNotEmpty() && cookedError == null && servingsError == null && !amountErrors
}

class RecipeEditorViewModel(private val c: AppContainer, private val recipeId: Long) : ViewModel() {
    private val _state = MutableStateFlow(RecipeEditorState())
    val state: StateFlow<RecipeEditorState> = _state.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private var currentId = recipeId

    init {
        viewModelScope.launch {
            if (recipeId == NONE) {
                _state.value = RecipeEditorState(loaded = true)
                return@launch
            }
            val d = c.recipes.loadDraft(recipeId)
            val r = c.recipes.observe(recipeId)
            if (d == null) {
                _state.value = RecipeEditorState(loaded = true)
            } else {
                _state.value = RecipeEditorState(
                    draft = d,
                    amounts = d.ingredients.mapIndexed { i, ing -> i to formatInput(ing.amount) }.toMap(),
                    cookedText = formatInput(d.cookedWeightG),
                    servingsText = d.servingsCount?.toString() ?: "",
                    loaded = true,
                )
            }
            r.collect { rec -> _state.update { it.copy(savedFoodId = rec?.foodId) } }
        }
    }

    private fun recompute(s: RecipeEditorState): RecipeEditorState {
        val ingredients = s.draft.ingredients.mapIndexed { i, ing ->
            val v = s.amounts[i]?.let { parseDecimal(it) }
            if (v != null && v > 0) ing.copy(amount = v) else ing
        }
        val cooked = parseDecimal(s.cookedText)?.takeIf { it > 0 && it <= 100_000 }
        val servings = parseDecimal(s.servingsText)?.takeIf { it >= 1 && it <= 100 && it == Math.floor(it) }?.toInt()
        return s.copy(draft = s.draft.copy(ingredients = ingredients, cookedWeightG = cooked, servingsCount = servings), dirty = true)
    }

    fun setName(n: String) = _state.update { it.copy(draft = it.draft.copy(name = n), dirty = true) }
    fun setNotes(n: String) = _state.update { it.copy(draft = it.draft.copy(notes = n), dirty = true) }
    fun setCooked(t: String) = _state.update { recompute(it.copy(cookedText = t)) }
    fun setServings(t: String) = _state.update { recompute(it.copy(servingsText = t)) }
    fun setAmount(i: Int, t: String) = _state.update { recompute(it.copy(amounts = it.amounts + (i to t))) }

    fun remove(i: Int) = _state.update { s ->
        val list = s.draft.ingredients.filterIndexed { j, _ -> j != i }
        val amounts = s.draft.ingredients.indices.filter { it != i }.mapIndexed { newIdx, old -> newIdx to (s.amounts[old] ?: formatInput(s.draft.ingredients[old].amount)) }.toMap()
        recompute(s.copy(draft = s.draft.copy(ingredients = list), amounts = amounts))
    }

    fun onPicked(json: String) {
        val picked = runCatching { Json.decodeFromString(PickedIngredient.serializer(), json) }.getOrNull() ?: return
        viewModelScope.launch {
            val food = c.foods.get(picked.foodId) ?: return@launch
            val ing = RecipeIngredient(0, food.id, food.displayName, picked.amount, food.basis, food.nutrients)
            _state.update { s ->
                val idx = s.draft.ingredients.size
                recompute(s.copy(draft = s.draft.copy(ingredients = s.draft.ingredients + ing), amounts = s.amounts + (idx to formatInput(picked.amount))))
            }
        }
    }

    fun refreshIngredients() {
        viewModelScope.launch {
            val d = c.recipes.refreshIngredientData(_state.value.draft)
            _state.update { recompute(it.copy(draft = d)) }
            _messages.emit("Данные ингредиентов обновлены из базы продуктов")
        }
    }

    fun save(onSaved: (() -> Unit)? = null) {
        val s = recompute(_state.value)
        if (!s.canSave) return
        _state.value = s.copy(saving = true)
        viewModelScope.launch {
            try {
                val id = c.recipes.save(s.draft.copy(id = if (currentId == NONE) 0 else currentId))
                currentId = id
                val r = c.recipes.loadDraft(id)
                _state.update { it.copy(saving = false, dirty = false, draft = it.draft.copy(id = id)) }
                val foodId = c.recipes.observe(id).first()?.foodId
                _state.update { it.copy(savedFoodId = foodId) }
                _messages.emit(if (foodId != null) "Рецепт сохранён" else "Рецепт сохранён. Укажите вес готового блюда, чтобы добавлять его в дневник.")
                if (r != null) onSaved?.invoke()
            } catch (e: IllegalArgumentException) {
                _state.update { it.copy(saving = false) }
                _messages.emit(e.message ?: "Не удалось сохранить")
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        viewModelScope.launch {
            if (currentId != NONE) c.recipes.delete(currentId)
            onDone()
        }
    }
}

@Composable
fun RecipeEditorScreen(
    recipeId: Long,
    savedStateHandle: SavedStateHandle,
    onBack: () -> Unit,
    onPickIngredient: () -> Unit,
    onAddPortion: (Long) -> Unit,
) {
    val vm = appViewModel(key = "recipe-$recipeId") { c, _ -> RecipeEditorViewModel(c, recipeId) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val picked by savedStateHandle.getStateFlow<String?>(PICKED_INGREDIENT_KEY, null).collectAsStateWithLifecycle()

    LaunchedEffect(picked) {
        picked?.let {
            vm.onPicked(it)
            savedStateHandle[PICKED_INGREDIENT_KEY] = null
        }
    }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = c.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            AppTopBar(if (recipeId == NONE && s.draft.id == 0L) "Новый рецепт" else "Рецепт", onBack = onBack) {
                if (s.draft.id != 0L || recipeId != NONE) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Удалить рецепт", tint = c.textSecondary) }
                }
            }
        },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(Dimens.screenPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val foodId = s.savedFoodId
                if (foodId != null && !s.dirty) {
                    PrimaryButton("Добавить порцию в дневник", onClick = { onAddPortion(foodId) }, modifier = Modifier.fillMaxWidth())
                } else {
                    PrimaryButton(if (s.saving) "Сохранение…" else "Сохранить рецепт", onClick = { vm.save() }, enabled = s.canSave, modifier = Modifier.fillMaxWidth())
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { AppTextField(s.draft.name, vm::setName, "Название рецепта *") }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ингредиенты", style = AppTheme.type.section, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (s.draft.ingredients.any { it.foodId != null }) {
                        IconButton(onClick = vm::refreshIngredients) { Icon(Icons.Outlined.Refresh, "Обновить данные ингредиентов", tint = c.textSecondary) }
                    }
                }
            }
            if (s.draft.ingredients.isEmpty()) {
                item { EmptyState(Icons.Filled.Add, "Пока нет ингредиентов", "Добавьте продукты и их количество в сыром виде.") }
            }
            itemsIndexed(s.draft.ingredients) { i, ing ->
                IngredientRow(ing, s.amounts[i] ?: formatInput(ing.amount), onAmount = { vm.setAmount(i, it) }, onRemove = { vm.remove(i) })
            }
            item { SecondaryButton("Добавить ингредиент", onClick = onPickIngredient, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth()) }
            item {
                AppCard {
                    Text("Готовое блюдо", style = AppTheme.type.section, color = c.textPrimary)
                    Text(
                        "Взвесьте блюдо после приготовления: при варке и запекании масса меняется, и без этого ценность на 100 г будет неверной.",
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    val raw = s.calculation?.rawMassGrams
                    AppTextField(
                        s.cookedText, vm::setCooked, "Вес готового блюда", suffix = "г", numeric = true, error = s.cookedError,
                        supporting = raw?.let { "Сумма сырых ингредиентов: ${NutrientFormat.amount(it)} г" }
                            ?: if (s.calculation?.hasVolumeIngredients == true) "Есть ингредиенты в мл — их массу без плотности не сложить" else null,
                    )
                    Spacer(Modifier.height(8.dp))
                    AppTextField(s.servingsText, vm::setServings, "Количество порций (необязательно)", numeric = true, error = s.servingsError)
                }
            }
            s.calculation?.let { calc -> item { RecipeTotals(calc) } }
            item { AppTextField(s.draft.notes ?: "", vm::setNotes, "Заметки", singleLine = false) }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить рецепт?",
            text = "Рецепт исчезнет из списка и поиска. Уже сделанные записи дневника сохранятся.",
            confirm = "Удалить",
            destructive = true,
            onConfirm = { confirmDelete = false; vm.delete(onBack) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun IngredientRow(ing: RecipeIngredient, amountText: String, onAmount: (String) -> Unit, onRemove: () -> Unit) {
    val c = AppTheme.colors
    val amount = parseDecimal(amountText)
    val kcal = amount?.let { a -> ing.per100[Nutrient.ENERGY]?.let { it * a / 100.0 } }
    AppCard(padding = PaddingValues(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(ing.name, style = AppTheme.type.bodyStrong, color = c.textPrimary)
                Text(
                    kcal?.let { "${NutrientFormat.kcal(it)} ккал" } ?: "калорийность: нет данных",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.width(120.dp)) {
                AppTextField(
                    amountText, onAmount, "Кол-во", suffix = ing.unit.symbol, numeric = true,
                    error = if (amount == null || amount <= 0) "> 0" else null,
                )
            }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Убрать ингредиент", tint = c.textSecondary) }
        }
    }
}

@Composable
private fun RecipeTotals(calc: RecipeCalculation) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    val c = AppTheme.colors
    val total = calc.total
    AppCard {
        Text("Всё блюдо", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS, Nutrient.FIBER).forEach { n ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                Text(n.titleRu, style = AppTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
                Text(NutrientFormat.total(total[n]), style = AppTheme.type.number, color = c.textPrimary)
            }
        }
        calc.yieldPercent?.let {
            Spacer(Modifier.height(6.dp))
            Text("Выход готового блюда: ${NutrientFormat.percent(it)} от массы сырья", style = AppTheme.type.caption, color = c.textSecondary)
        }
    }
    val per100 = calc.per100Cooked
    if (per100 == null) {
        InfoBanner("Ценность на 100 г появится после указания веса готового блюда.", kind = BannerKind.INFO)
    } else {
        Text("На 100 г готового блюда", style = AppTheme.type.section, color = c.textPrimary)
        NutrientTable(per100)
    }
    }
}
