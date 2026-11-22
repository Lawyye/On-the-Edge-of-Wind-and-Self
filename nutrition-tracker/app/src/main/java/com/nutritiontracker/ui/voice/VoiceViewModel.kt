package com.nutritiontracker.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.voice.ParsedFoodItem
import com.nutritiontracker.core.voice.ParsedQuantity
import com.nutritiontracker.core.voice.QuantityResolver
import com.nutritiontracker.core.voice.ResolvedAmount
import com.nutritiontracker.core.voice.VoiceInputParser
import com.nutritiontracker.core.text.FoodSearch
import com.nutritiontracker.data.FoodItem
import com.nutritiontracker.data.toNewEntry
import com.nutritiontracker.remote.RemoteFood
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.navigation.VoiceRoute
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class VoiceItem(
    val id: Int,
    val parsed: ParsedFoodItem,
    val searchQuery: String,
    val localCandidates: List<FoodItem> = emptyList(),
    val remoteCandidates: List<RemoteFood> = emptyList(),
    val searching: Boolean = true,
    val selected: FoodItem? = null,
    val amountText: String = "",
    val explanation: String? = null,
    val needs: String? = null,
    val included: Boolean = true,
    val searchError: String? = null,
) {
    val amount: Double? get() = parseDecimal(amountText)?.takeIf { it > 0 && it <= 10_000 }
    val ready: Boolean get() = !included || (selected != null && amount != null)
    val kcal: Double? get() = selected?.let { f -> amount?.let { a -> NutrientMath.valueForAmount(f.nutrients, Nutrient.ENERGY, a) } }
}

data class VoiceState(
    val text: String = "",
    val items: List<VoiceItem> = emptyList(),
    val meal: MealType = MealType.SNACK,
    val date: LocalDate = LocalDate.now(),
    val parsedOnce: Boolean = false,
    val saving: Boolean = false,
) {
    val canSave: Boolean get() = !saving && items.any { it.included } && items.all { it.ready }
}

class VoiceViewModel(private val c: AppContainer, route: VoiceRoute) : ViewModel() {
    private val baseDate = LocalDate.ofEpochDay(route.epochDay)
    private val _state = MutableStateFlow(VoiceState(meal = MealType.fromKey(route.meal) ?: MealType.SNACK, date = baseDate))
    val state: StateFlow<VoiceState> = _state.asStateFlow()
    private val _saved = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val saved = _saved.asSharedFlow()
    private val _errors = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val errors = _errors.asSharedFlow()
    private var nextId = 0

    fun setText(t: String) = _state.update { it.copy(text = t) }

    fun onRecognized(t: String) {
        val merged = listOf(_state.value.text.trim(), t.trim()).filter { it.isNotEmpty() }.joinToString(", ")
        _state.update { it.copy(text = merged) }
        parse()
    }

    /** Разбирает текст заново. Уже подтверждённые позиции заменяются. */
    fun parse() {
        val text = _state.value.text
        val u = VoiceInputParser.parse(text)
        val items = u.items.filter { it.name.isNotBlank() }.map { p ->
            VoiceItem(id = nextId++, parsed = p, searchQuery = p.name, needs = p.notes.firstOrNull())
        }
        _state.update { s ->
            s.copy(
                items = items,
                parsedOnce = true,
                meal = u.mealHint ?: s.meal,
                date = u.dayOffset?.let { LocalDate.now().plusDays(it.toLong()) } ?: s.date,
            )
        }
        items.forEach { findFood(it.id, it.searchQuery) }
    }

    private fun updateItem(id: Int, t: (VoiceItem) -> VoiceItem) =
        _state.update { s -> s.copy(items = s.items.map { if (it.id == id) t(it) else it }) }

    /** Ищет продукт: сначала в локальной базе, затем, если есть сеть, в интернете. */
    fun findFood(id: Int, query: String) {
        updateItem(id) { it.copy(searching = true, searchQuery = query, searchError = null) }
        viewModelScope.launch {
            // В локальном поиске все результаты совпадают с запросом (с учётом опечаток).
            val local = c.foods.searchLocal(query, limit = 6).map { it.food }
            updateItem(id) { it.copy(localCandidates = local) }
            if (local.isNotEmpty()) {
                select(id, local.first())
            }
            val settings = c.settings.current()
            if (settings.onlineSearchEnabled && c.connectivity.online.value) {
                val online = c.foods.searchOnline(query, settings)
                // Результаты из интернета ранжируются по совпадению с запросом: Open Food Facts —
                // с русским запросом, USDA — с переводом. При равенстве выше продукт с более полным составом.
                val scored = online.openFoodFacts.map { r ->
                    r to FoodSearch.relevance(query, r.food.name, r.food.brand, r.food.category, r.food.nameEn)
                } + online.usda.map { r ->
                    r to (online.usdaQuery?.let { q -> FoodSearch.relevance(q, r.food.name, r.food.category) } ?: 0.0)
                }
                val ranked = scored
                    .map { (r, score) -> Triple(r, score, if (score > 0) score + r.food.nutrients.known.size / 20.0 else 0.0) }
                    .sortedByDescending { it.third }
                val remote = ranked.take(8).map { it.first }
                val best = ranked.firstOrNull { it.second > 0 }?.first
                updateItem(id) {
                    it.copy(
                        remoteCandidates = remote,
                        searching = false,
                        searchError = when {
                            online.errors.isNotEmpty() -> online.errors.first().let { e -> "${e.source}: ${e.kind.messageRu}" }
                            local.isEmpty() && best == null -> "Точного совпадения не нашлось — выберите продукт из вариантов или уточните название"
                            else -> null
                        },
                    )
                }
                if (local.isEmpty() && best != null) selectRemote(id, best)
            } else {
                updateItem(id) { it.copy(searching = false, searchError = if (local.isEmpty()) "Нет интернета — поиск только по сохранённым продуктам" else null) }
            }
        }
    }

    fun select(id: Int, food: FoodItem) {
        viewModelScope.launch {
            var f = food
            val item = _state.value.items.firstOrNull { it.id == id } ?: return@launch
            // Для штук и порций нужен справочный вес из полной карточки USDA.
            if (!f.detailsLoaded && item.parsed.quantity is ParsedQuantity.Count) {
                c.foods.ensureDetails(f.id)
                f = c.foods.get(f.id) ?: f
            }
            val resolved = QuantityResolver.resolve(item.parsed.quantity, f.basis, f.data.servings)
            updateItem(id) {
                when (resolved) {
                    is ResolvedAmount.Resolved -> it.copy(selected = f, amountText = formatInput(resolved.amount), explanation = resolved.explanation, needs = null)
                    is ResolvedAmount.NeedsInput -> it.copy(selected = f, amountText = "", explanation = null, needs = resolved.reasonRu)
                }
            }
        }
    }

    fun selectRemote(id: Int, remote: RemoteFood) {
        viewModelScope.launch {
            val item = _state.value.items.firstOrNull { it.id == id } ?: return@launch
            val foodId = c.foods.saveRemote(remote, item.searchQuery)
            c.foods.get(foodId)?.let { select(id, it) }
        }
    }

    fun setAmount(id: Int, text: String) = updateItem(id) { it.copy(amountText = text, explanation = null, needs = if (parseDecimal(text) == null) it.needs else null) }
    fun toggleIncluded(id: Int) = updateItem(id) { it.copy(included = !it.included) }
    fun setMeal(m: MealType) = _state.update { it.copy(meal = m) }
    fun setDate(d: LocalDate) = _state.update { it.copy(date = d) }

    /** Сохраняет все отмеченные позиции одной транзакцией. Повторное нажатие не создаёт дублей. */
    fun save() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val entries = s.items.filter { it.included }.map { item ->
                item.selected!!.toNewEntry(item.amount!!, item.explanation)
            }
            try {
                c.diary.addAll(entries, s.date, s.meal)
                _saved.emit(entries.size)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(saving = false) }
                _errors.emit("Не удалось сохранить: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
