package com.nutritiontracker.ui.foods

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.EnergyConsistency
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.navigation.NONE
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ServingInput(val label: String = "1 шт", val amount: String = "", val kind: ServingKind = ServingKind.PIECE)

data class FoodForm(
    val name: String = "",
    val brand: String = "",
    val category: String = "",
    val aliases: String = "",
    val basis: AmountUnit = AmountUnit.GRAM,
    val values: Map<Nutrient, String> = emptyMap(),
    val servings: List<ServingInput> = emptyList(),
    val loaded: Boolean = false,
    val saving: Boolean = false,
    val existingSource: FoodSource = FoodSource.USER,
    val existingNotes: List<String> = emptyList(),
) {
    fun parsed(n: Nutrient): Double? = values[n]?.let { parseDecimal(it) }
    fun fieldError(n: Nutrient): String? {
        val t = values[n] ?: return null
        if (t.isBlank()) return null
        val v = parseDecimal(t) ?: return "Не число"
        return if (v < 0) "Не может быть отрицательным" else null
    }

    fun nutrients(): Nutrients = Nutrients.of(Nutrient.entries.mapNotNull { n -> parsed(n)?.takeIf { it >= 0 }?.let { n to it } }.toMap())

    val errors: List<String>
        get() = buildList {
            if (name.isBlank()) add("Укажите название")
            if (Nutrient.entries.any { fieldError(it) != null }) add("Исправьте поля с ошибками")
            if (basis == AmountUnit.GRAM) {
                val sum = listOf(Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS).sumOf { parsed(it) ?: 0.0 }
                if (sum > 102) add("Белки, жиры и углеводы в сумме больше 100 г на 100 г — так не бывает")
            }
            servings.forEachIndexed { i, s ->
                val a = parseDecimal(s.amount)
                if (s.label.isBlank() || a == null || a <= 0) add("Порция ${i + 1}: укажите название и вес")
            }
        }

    val warnings: List<String>
        get() = buildList {
            val n = nutrients()
            n[Nutrient.ENERGY]?.let { if (basis == AmountUnit.GRAM && it > 902) add("Больше 900 ккал на 100 г бывает только у чистого жира — проверьте значение") }
            val sug = n[Nutrient.SUGARS]; val carbs = n[Nutrient.CARBS]
            if (sug != null && carbs != null && sug > carbs + 0.5) add("Сахаров больше, чем углеводов")
            val sat = n[Nutrient.SATURATED_FAT]; val fat = n[Nutrient.FAT]
            if (sat != null && fat != null && sat > fat + 0.5) add("Насыщенных жиров больше, чем жиров всего")
            val r = EnergyConsistency.check(n)
            if (r.status == EnergyConsistency.Status.MISMATCH) {
                add("Калорийность расходится с БЖУ: ожидалось ${NutrientFormat.kcal(r.expectedMinKcal ?: 0.0)}–${NutrientFormat.kcal(r.expectedMaxKcal ?: 0.0)} ккал")
            }
        }
}

class FoodEditorViewModel(private val c: AppContainer, private val foodId: Long) : ViewModel() {
    private val _form = MutableStateFlow(FoodForm())
    val form: StateFlow<FoodForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            if (foodId == NONE) {
                _form.value = FoodForm(loaded = true)
                return@launch
            }
            val f = c.foods.get(foodId)
            _form.value = if (f == null) FoodForm(loaded = true) else FoodForm(
                name = f.data.name,
                brand = f.data.brand ?: "",
                category = f.data.category ?: "",
                aliases = f.aliases.joinToString(", "),
                basis = f.basis,
                values = Nutrient.entries.mapNotNull { n -> f.nutrients[n]?.let { n to formatInput(it) } }.toMap(),
                servings = f.data.servings.map { ServingInput(it.label, formatInput(it.amount), it.kind) },
                loaded = true,
                existingSource = f.source,
                existingNotes = f.data.dataNotes,
            )
        }
    }

    fun edit(t: (FoodForm) -> FoodForm) = _form.update(t)
    fun setValue(n: Nutrient, text: String) = _form.update { it.copy(values = it.values + (n to text)) }

    fun save(onSaved: (Long) -> Unit) {
        val f = _form.value
        if (f.errors.isNotEmpty() || f.saving) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val data = FoodData(
                name = f.name.trim(),
                brand = f.brand.trim().ifEmpty { null },
                category = f.category.trim().ifEmpty { null },
                source = FoodSource.USER,
                basis = f.basis,
                nutrients = f.nutrients(),
                servings = f.servings.map { ServingSize(it.label.trim(), parseDecimal(it.amount)!!, it.kind, "Ваши данные") },
                dataNotes = f.existingNotes,
            )
            val aliases = f.aliases.split(',').map { it.trim() }.filter { it.isNotEmpty() }
            val id = if (foodId == NONE) c.foods.createUserFood(data, aliases) else { c.foods.updateUserFood(foodId, data, aliases); foodId }
            _form.update { it.copy(saving = false) }
            onSaved(id)
        }
    }
}

@Composable
fun FoodEditorScreen(foodId: Long, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val vm = appViewModel(key = "editor-$foodId") { c, _ -> FoodEditorViewModel(c, foodId) }
    val f by vm.form.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    Scaffold(
        containerColor = c.background,
        topBar = { AppTopBar(if (foodId == NONE) "Новый продукт" else "Изменить продукт", onBack = onBack) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(Dimens.screenPadding)) {
                PrimaryButton("Сохранить", onClick = { vm.save(onSaved) }, enabled = f.loaded && f.errors.isEmpty() && !f.saving, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AppTextField(f.name, { v -> vm.edit { it.copy(name = v) } }, "Название *", error = if (f.loaded && f.name.isBlank()) "Обязательно" else null)
            }
            item { AppTextField(f.brand, { v -> vm.edit { it.copy(brand = v) } }, "Производитель") }
            item { AppTextField(f.category, { v -> vm.edit { it.copy(category = v) } }, "Категория") }
            item { AppTextField(f.aliases, { v -> vm.edit { it.copy(aliases = v) } }, "Другие названия (через запятую)", supporting = "Помогают находить продукт в поиске") }
            item {
                Column {
                    Text("Пищевая ценность указана", style = AppTheme.type.label, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    SegmentedControl(AmountUnit.entries, f.basis, { it.per100Ru }, { b -> vm.edit { it.copy(basis = b) } })
                }
            }
            item {
                InfoBanner("Пустое поле — нет данных. Ноль — значит, вещества в продукте действительно нет. Заполняйте только то, что знаете точно.")
            }
            item { NutrientInputGroup("Энергия и макронутриенты", listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS, Nutrient.FIBER, Nutrient.SUGARS, Nutrient.ADDED_SUGARS), f, vm::setValue) }
            item { NutrientInputGroup("Жиры подробно", Nutrient.fatDetails, f, vm::setValue) }
            item { NutrientInputGroup("Витамины", Nutrient.vitamins, f, vm::setValue) }
            item { NutrientInputGroup("Минералы", Nutrient.minerals, f, vm::setValue) }
            item { NutrientInputGroup("Прочее", listOf(Nutrient.ALCOHOL), f, vm::setValue) }
            item {
                Column {
                    Text("Порции и штуки", style = AppTheme.type.section, color = c.textPrimary)
                    Text("Например: «1 шт» — 60 г. Тогда в голосовом вводе «два яйца» посчитается по этому весу.", style = AppTheme.type.caption, color = c.textSecondary)
                }
            }
            itemsIndexed(f.servings) { i, s ->
                AppCard(padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Порция ${i + 1}", style = AppTheme.type.label, color = c.textSecondary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.edit { it.copy(servings = it.servings.filterIndexed { j, _ -> j != i }) } }) {
                            Icon(Icons.Filled.Close, "Удалить порцию", tint = c.textSecondary)
                        }
                    }
                    SegmentedControl(
                        listOf(ServingKind.PIECE, ServingKind.PORTION, ServingKind.PACKAGE), s.kind,
                        { when (it) { ServingKind.PIECE -> "Штука"; ServingKind.PORTION -> "Порция"; else -> "Упаковка" } },
                        { k -> vm.edit { it.copy(servings = it.servings.mapIndexed { j, x -> if (j == i) x.copy(kind = k) else x }) } },
                    )
                    Spacer(Modifier.height(8.dp))
                    AppTextField(s.label, { v -> vm.edit { it.copy(servings = it.servings.mapIndexed { j, x -> if (j == i) x.copy(label = v) else x }) } }, "Название")
                    Spacer(Modifier.height(8.dp))
                    AppTextField(s.amount, { v -> vm.edit { it.copy(servings = it.servings.mapIndexed { j, x -> if (j == i) x.copy(amount = v) else x }) } }, "Вес", suffix = f.basis.symbol, numeric = true)
                }
            }
            item {
                SecondaryButton("Добавить порцию", onClick = { vm.edit { it.copy(servings = it.servings + ServingInput()) } }, icon = Icons.Filled.Add, modifier = Modifier.fillMaxWidth())
            }
            f.warnings.forEach { w -> item { InfoBanner(w, kind = BannerKind.WARNING) } }
            if (f.loaded) f.errors.filter { it != "Укажите название" }.forEach { e -> item { InfoBanner(e, kind = BannerKind.ERROR) } }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun NutrientInputGroup(title: String, list: List<Nutrient>, f: FoodForm, onValue: (Nutrient, String) -> Unit) {
    AppCard(padding = PaddingValues(12.dp)) {
        Text(title, style = AppTheme.type.section, color = AppTheme.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            list.forEach { n ->
                AppTextField(
                    f.values[n] ?: "", { onValue(n, it) }, n.titleRu, suffix = n.unit.symbol, numeric = true,
                    error = f.fieldError(n), placeholder = "нет данных",
                )
            }
        }
    }
}
