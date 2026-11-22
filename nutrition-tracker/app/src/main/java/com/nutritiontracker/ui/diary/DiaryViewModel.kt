package com.nutritiontracker.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.data.DiaryEntry
import com.nutritiontracker.data.MealTemplate
import com.nutritiontracker.data.db.FoodEntryEntity
import com.nutritiontracker.domain.DailyTargets
import com.nutritiontracker.domain.DaySummary
import com.nutritiontracker.domain.Targets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class DiaryState(
    val date: LocalDate = LocalDate.now(),
    val summary: DaySummary = DaySummary.of(LocalDate.now(), emptyList()),
    val targets: DailyTargets? = null,
    val templates: List<MealTemplate> = emptyList(),
    val loading: Boolean = true,
)

sealed interface DiaryEvent {
    data class Deleted(val entity: FoodEntryEntity) : DiaryEvent
    data class Message(val text: String) : DiaryEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModel(private val c: AppContainer) : ViewModel() {
    private val date = MutableStateFlow(LocalDate.now())
    private val _events = MutableSharedFlow<DiaryEvent>(extraBufferCapacity = 4)
    val events: SharedFlow<DiaryEvent> = _events.asSharedFlow()

    val state: StateFlow<DiaryState> = combine(
        date.flatMapLatest { d -> c.diary.observeDay(d) },
        date,
        c.profile.observe(),
        c.body.observeWeights(),
        c.profile.observeUserGoals(),
    ) { entries, d, profile, weights, goals ->
        DiaryState(
            date = d,
            summary = DaySummary.of(d, entries.filter { it.date == d }),
            targets = profile?.let { runCatching { Targets.compute(it, weights, goals, d) }.getOrNull() },
            loading = false,
        )
    }.combine(c.diary.observeTemplates()) { s, t -> s.copy(templates = t) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryState())

    fun setDate(d: LocalDate) { date.value = d }
    fun shiftDate(days: Long) { date.value = date.value.plusDays(days) }

    fun update(entry: DiaryEntry, amount: Double, meal: MealType, newDate: LocalDate) {
        viewModelScope.launch {
            runCatching { c.diary.update(entry.id, amount, meal, newDate) }
                .onFailure { _events.tryEmit(DiaryEvent.Message(it.message ?: "Не удалось сохранить")) }
        }
    }

    fun delete(entry: DiaryEntry) {
        viewModelScope.launch {
            c.diary.delete(entry.id)?.let { _events.tryEmit(DiaryEvent.Deleted(it)) }
        }
    }

    fun restore(entity: FoodEntryEntity) {
        viewModelScope.launch { c.diary.restore(entity) }
    }

    fun repeat(entry: DiaryEntry, toDate: LocalDate, meal: MealType) {
        viewModelScope.launch {
            c.diary.duplicate(entry.id, toDate, meal)
            _events.tryEmit(DiaryEvent.Message("«${entry.foodName}» добавлено ещё раз"))
        }
    }

    fun copyFromPreviousDay(meal: MealType) {
        viewModelScope.launch {
            val d = date.value
            val n = c.diary.copyMeal(d.minusDays(1), meal, d, meal)
            _events.tryEmit(DiaryEvent.Message(if (n == 0) "Вчера в «${meal.titleRu}» ничего не было" else "Скопировано записей: $n"))
        }
    }

    fun saveTemplate(name: String, meal: MealType) {
        viewModelScope.launch {
            val id = c.diary.saveMealAsTemplate(name, date.value, meal)
            _events.tryEmit(DiaryEvent.Message(if (id == null) "В этом приёме пищи нет записей" else "Шаблон «$name» сохранён"))
        }
    }

    fun applyTemplate(template: MealTemplate, meal: MealType) {
        viewModelScope.launch {
            val n = c.diary.applyTemplate(template.id, date.value, meal)
            _events.tryEmit(DiaryEvent.Message("Добавлено из шаблона: $n"))
        }
    }

    fun deleteTemplate(template: MealTemplate) {
        viewModelScope.launch { c.diary.deleteTemplate(template.id) }
    }
}
