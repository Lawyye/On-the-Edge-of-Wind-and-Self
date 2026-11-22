package com.nutritiontracker.ui.foods

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.data.FoodItem
import com.nutritiontracker.data.toNewEntry
import com.nutritiontracker.remote.RemoteError
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.navigation.FoodDetailRoute
import com.nutritiontracker.ui.navigation.NONE
import com.nutritiontracker.ui.navigation.mealForNow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class PickedIngredient(val foodId: Long, val amount: Double)

data class AmountInput(
    val text: String = "100",
    val servingLabel: String? = null,
) {
    val value: Double? get() = parseDecimal(text)
    val valid: Boolean get() = value?.let { it > 0 && it <= 10_000 } == true
}

data class DetailUi(
    val loadingDetails: Boolean = false,
    val detailsError: RemoteError? = null,
    val refreshing: Boolean = false,
    val saving: Boolean = false,
)

sealed interface DetailEvent {
    data object Added : DetailEvent
    data class IngredientPicked(val json: String) : DetailEvent
    data class Message(val text: String) : DetailEvent
    data class OpenOther(val foodId: Long) : DetailEvent
    data object Deleted : DetailEvent
}

class FoodDetailViewModel(private val c: AppContainer, val route: FoodDetailRoute) : ViewModel() {
    val food: StateFlow<FoodItem?> = c.foods.observeFood(route.foodId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val isFavorite: StateFlow<Boolean> = c.foods.observeIsFavorite(route.foodId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _amount = MutableStateFlow(AmountInput())
    val amount: StateFlow<AmountInput> = _amount.asStateFlow()
    private val _meal = MutableStateFlow(MealType.fromKey(route.meal) ?: mealForNow())
    val meal: StateFlow<MealType> = _meal.asStateFlow()
    val date: LocalDate = if (route.epochDay != NONE) LocalDate.ofEpochDay(route.epochDay) else LocalDate.now()

    private val _ui = MutableStateFlow(DetailUi())
    val ui: StateFlow<DetailUi> = _ui.asStateFlow()
    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    private var defaultApplied = false

    init {
        viewModelScope.launch {
            val f = c.foods.get(route.foodId) ?: return@launch
            applyDefaultAmount(f)
            if (!f.detailsLoaded) {
                _ui.update { it.copy(loadingDetails = true) }
                val err = c.foods.ensureDetails(route.foodId)
                _ui.update { it.copy(loadingDetails = false, detailsError = err) }
                c.foods.get(route.foodId)?.let { applyDefaultAmount(it, force = false) }
            }
        }
    }

    /** Для рецептов и продуктов с порцией по умолчанию предлагается порция, иначе 100 г. */
    private fun applyDefaultAmount(f: FoodItem, force: Boolean = true) {
        if (defaultApplied && !force) return
        if (_amount.value.text != "100" && defaultApplied) return
        val serving = f.data.servings.firstOrNull { it.kind == com.nutritiontracker.core.model.ServingKind.PORTION && f.source == com.nutritiontracker.core.model.FoodSource.RECIPE }
        _amount.value = if (serving != null) AmountInput(formatInput(serving.amount), serving.label) else AmountInput("100")
        defaultApplied = true
    }

    fun setAmountText(t: String) = _amount.update { AmountInput(t, null) }

    fun useServing(s: ServingSize, count: Double = 1.0) {
        _amount.value = AmountInput(formatInput(s.amount * count), if (count == 1.0) s.label else null)
    }

    fun setMeal(m: MealType) { _meal.value = m }

    fun toggleFavorite() {
        viewModelScope.launch { c.foods.setFavorite(route.foodId, !isFavorite.value) }
    }

    fun addToDiary() {
        val f = food.value ?: return
        val a = _amount.value
        if (!a.valid || _ui.value.saving) return
        _ui.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                c.diary.add(f.toNewEntry(a.value!!, a.servingLabel), date, _meal.value)
                if (route.searchQuery.isNotBlank()) c.foods.rememberQuery(route.searchQuery)
                _events.emit(DetailEvent.Added)
            } catch (e: IllegalArgumentException) {
                _events.emit(DetailEvent.Message(e.message ?: "Не удалось добавить"))
            } finally {
                _ui.update { it.copy(saving = false) }
            }
        }
    }

    fun pickAsIngredient() {
        val a = _amount.value
        if (!a.valid) return
        val json = kotlinx.serialization.json.Json.encodeToString(PickedIngredient.serializer(), PickedIngredient(route.foodId, a.value!!))
        viewModelScope.launch { _events.emit(DetailEvent.IngredientPicked(json)) }
    }

    fun refresh() {
        if (_ui.value.refreshing) return
        _ui.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            val err = c.foods.refreshFromSource(route.foodId)
            _ui.update { it.copy(refreshing = false, detailsError = err) }
            _events.emit(DetailEvent.Message(if (err == null) "Данные обновлены. Прошлые записи дневника не изменились." else "${err.source}: ${err.kind.messageRu}"))
        }
    }

    fun retryDetails() {
        viewModelScope.launch {
            _ui.update { it.copy(loadingDetails = true, detailsError = null) }
            val err = c.foods.ensureDetails(route.foodId)
            _ui.update { it.copy(loadingDetails = false, detailsError = err) }
        }
    }

    fun setRussianName(name: String) {
        viewModelScope.launch {
            val f = food.value ?: return@launch
            val rest = f.aliases.drop(1)
            c.foods.setAliases(route.foodId, listOf(name.trim()).filter { it.isNotEmpty() } + rest)
        }
    }

    fun duplicate() {
        viewModelScope.launch {
            val id = c.foods.duplicateAsUserFood(route.foodId) ?: return@launch
            _events.emit(DetailEvent.OpenOther(id))
        }
    }

    fun delete() {
        viewModelScope.launch {
            c.foods.delete(route.foodId)
            _events.emit(DetailEvent.Deleted)
        }
    }
}
