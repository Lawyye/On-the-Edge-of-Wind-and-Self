package com.nutritiontracker.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.WeightSummary
import com.nutritiontracker.core.calc.WeightTrend
import com.nutritiontracker.data.Profile
import com.nutritiontracker.domain.DailyTargets
import com.nutritiontracker.domain.DaySummary
import com.nutritiontracker.domain.Targets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/** Текущая дата, обновляется после полуночи без перезапуска приложения. */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()

data class HomeState(
    val loading: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val profile: Profile? = null,
    val targets: DailyTargets? = null,
    val summary: DaySummary = DaySummary.of(LocalDate.now(), emptyList()),
    val waterMl: Int = 0,
    val weight: WeightSummary? = null,
    /** Прогноз даты достижения желаемого веса при текущем темпе. */
    val goalForecast: LocalDate? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val today = todayFlow()

    private val dayData = today.flatMapLatest { d ->
        combine(c.diary.observeDay(d), c.body.observeWater(d)) { entries, water ->
            Triple(d, DaySummary.of(d, entries), water.sumOf { it.amountMl })
        }
    }

    val state: StateFlow<HomeState> = combine(
        c.profile.observe(),
        c.body.observeWeights(),
        c.profile.observeUserGoals(),
        dayData,
    ) { profile, weights, goals, (day, summary, water) ->
        HomeState(
            loading = false,
            today = day,
            profile = profile,
            targets = profile?.let { runCatching { Targets.compute(it, weights, goals, day) }.getOrNull() },
            summary = summary,
            waterMl = water,
            weight = if (weights.isEmpty()) null else WeightTrend.summary(weights, profile?.startWeightKg, profile?.targetWeightKg, day),
            goalForecast = profile?.let { WeightTrend.forecastGoalDate(weights, it.targetWeightKg, day) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private val _messages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages: kotlinx.coroutines.flow.SharedFlow<String> = _messages

    fun addWater(ml: Int) {
        viewModelScope.launch {
            try {
                c.body.addWater(state.value.today, ml)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("Не удалось добавить воду: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }
}
