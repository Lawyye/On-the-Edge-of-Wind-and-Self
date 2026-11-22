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
    /** «Масло» без уточнения: варианты, из которых выбирает пользователь. */
    val clarifications: List<String> = emptyList(),
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

    /** Оценка кандидата для автоматического выбора — общая с проверками в CI. */
    private fun candidateScore(query: String, englishQuery: String?, data: com.nutritiontracker.core.model.FoodData, aliases: List<String>, useCount: Int): Double =
        FoodSearch.autoPickScore(query, englishQuery, data, aliases, useCount)

    /** Ищет продукт: сначала в локальной базе, затем, если есть сеть, в интернете. */
    fun findFood(id: Int, query: String) {
        val translation = com.nutritiontracker.core.text.FoodQueryTranslator.translate(query)
        updateItem(id) {
            it.copy(searching = true, searchQuery = query, searchError = null, selected = null, clarifications = translation.clarifications)
        }
        viewModelScope.launch {
            val englishQuery = translation.englishQuery
            val local = c.foods.searchLocal(query, limit = 8).map { it.food }
            val localScored = local.map { f -> f to candidateScore(query, englishQuery, f.data, f.aliases, f.useCount) }
                .sortedByDescending { it.second }
            updateItem(id) { it.copy(localCandidates = localScored.map { p -> p.first }) }
            val settings = c.settings.current()
            val online = if (settings.onlineSearchEnabled && c.connectivity.online.value) c.foods.searchOnline(query, settings) else null
            val cachedKeys = local.mapNotNull { f -> f.data.externalId?.let { f.source to it } }.toSet()
            val remoteScored = online?.let { o ->
                (o.openFoodFacts + o.usda)
                    .filter { r -> r.food.externalId == null || (r.food.source to r.food.externalId) !in cachedKeys }
                    .map { r -> r to candidateScore(query, o.usdaQuery, r.food, emptyList(), 0) }
                    .sortedByDescending { it.second }
            } ?: emptyList()
            val bestLocal = localScored.firstOrNull { it.second > 0 }
            val bestRemote = remoteScored.firstOrNull { it.second > 0 }
            updateItem(id) {
                it.copy(
                    remoteCandidates = remoteScored.take(8).map { p -> p.first },
                    searching = false,
                    searchError = when {
                        translation.clarifications.isNotEmpty() && !(bestLocal?.first?.useCount?.let { it > 0 } ?: false) ->
                            translation.clarifyQuestion ?: "Уточните, какой именно продукт"
                        online == null && local.isEmpty() -> "Нет интернета — поиск только по сохранённым продуктам"
                        online != null && online.errors.isNotEmpty() && bestLocal == null && bestRemote == null ->
                            online.errors.first().userMessage
                        bestLocal == null && bestRemote == null -> "Точного совпадения не нашлось — выберите продукт из вариантов или уточните название"
                        else -> null
                    },
                )
            }
            when {
                // Неоднозначное слово: не выбираем за пользователя — кроме продукта,
                // который он сам уже выбирал раньше по такому запросу.
                translation.clarifications.isNotEmpty() -> bestLocal?.takeIf { it.first.useCount > 0 }?.let { select(id, it.first) }
                bestRemote != null && (bestLocal == null || bestRemote.second > bestLocal.second) -> selectRemote(id, bestRemote.first)
                bestLocal != null -> select(id, bestLocal.first)
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
