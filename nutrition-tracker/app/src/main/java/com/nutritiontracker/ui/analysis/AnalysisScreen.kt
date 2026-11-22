package com.nutritiontracker.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.NutrientTotals
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.norms.AssessmentStatus
import com.nutritiontracker.core.norms.DriNorms
import com.nutritiontracker.core.norms.NormEvaluator
import com.nutritiontracker.core.norms.NutrientAssessment
import com.nutritiontracker.domain.PeriodSummary
import com.nutritiontracker.domain.Targets
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.AssessmentRow
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.SkeletonList
import com.nutritiontracker.ui.navigation.AnalysisRoute
import com.nutritiontracker.ui.plural
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class AnalysisState(
    val loading: Boolean = true,
    val days: Int = 1,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val period: PeriodSummary? = null,
    val totals: NutrientTotals? = null,
    val assessments: Map<Nutrient, NutrientAssessment> = emptyMap(),
    val hasProfile: Boolean = true,
)

val ANALYSIS_GROUPS: List<Pair<String, List<Nutrient>>> = listOf(
    "Энергия и макронутриенты" to listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.FAT, Nutrient.CARBS, Nutrient.FIBER, Nutrient.SUGARS, Nutrient.ADDED_SUGARS),
    "Жиры" to Nutrient.fatDetails,
    "Витамины" to Nutrient.vitamins,
    "Минералы" to Nutrient.minerals,
)

@OptIn(ExperimentalCoroutinesApi::class)
class AnalysisViewModel(private val c: AppContainer, route: AnalysisRoute) : ViewModel() {
    private val end = LocalDate.ofEpochDay(route.epochDay)
    private val days = MutableStateFlow(route.days.coerceIn(1, 31))

    val state: StateFlow<AnalysisState> = days.flatMapLatest { n ->
        val from = end.minusDays(n - 1L)
        combine(
            c.diary.observeRange(from, end),
            c.profile.observe(),
            c.body.observeWeights(),
            c.profile.observeUserGoals(),
        ) { entries, profile, weights, goals ->
            val period = PeriodSummary.of(from, end, entries)
            val totals = if (n == 1) period.days.single().totals else period.dailyAverage
            val norms = profile?.let { runCatching { Targets.compute(it, weights, goals, end).norms }.getOrNull() } ?: emptyMap()
            val assessments = if (totals == null) emptyMap() else Nutrient.entries.associateWith { NormEvaluator.assess(totals[it], norms[it]) }
            AnalysisState(false, n, from, end, period, totals, assessments, profile != null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisState())

    fun setDays(n: Int) { days.value = n }
}

@Composable
fun AnalysisScreen(route: AnalysisRoute, onBack: () -> Unit) {
    val vm = appViewModel(key = "analysis-${route.epochDay}") { c, _ -> AnalysisViewModel(c, route) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    Scaffold(containerColor = c.background, topBar = { AppTopBar("Анализ питания", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                Column {
                    SegmentedControl(listOf(1, 7), s.days, { if (it == 1) "День" else "Неделя" }, vm::setDays)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (s.days == 1) Dates.title(s.to) + ", " + Dates.dayMonth(s.to) else "${Dates.dayMonth(s.from)} — ${Dates.dayMonth(s.to)}",
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                }
            }
            if (s.loading) {
                item { SkeletonList(4) }
                return@LazyColumn
            }
            val period = s.period
            if (period == null || s.totals == null) {
                item {
                    EmptyState(
                        Icons.Outlined.Info, "Записей нет",
                        if (s.days == 1) "В этот день в дневнике ничего нет." else "За эти 7 дней в дневнике ничего нет.",
                    )
                }
                return@LazyColumn
            }
            item { CoverageCard(s) }
            if (!s.hasProfile) item { InfoBanner("Заполните профиль, чтобы видеть нормы.", kind = BannerKind.WARNING) }
            ANALYSIS_GROUPS.forEach { (title, list) ->
                item(key = title) {
                    AppCard {
                        Text(title, style = AppTheme.type.section, color = c.textPrimary)
                        list.forEachIndexed { i, n ->
                            if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.4f))
                            s.assessments[n]?.let { a ->
                                AssessmentRow(a, periodNote = if (s.days > 1) "Среднее за день по ${plural(period.loggedDays, "дню", "дням", "дням")} с записями." else null)
                            }
                        }
                    }
                }
            }
            item {
                InfoBanner(
                    "${DriNorms.SYSTEM_DESCRIPTION} Нажмите на строку, чтобы увидеть норму, предел и источник. " +
                        "Дневник питания не позволяет судить о дефиците витаминов в организме — это может определить только врач по анализам.",
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun CoverageCard(s: AnalysisState) {
    val c = AppTheme.colors
    val totals = s.totals ?: return
    val period = s.period ?: return
    val micro = Nutrient.vitamins + Nutrient.minerals
    val complete = micro.count { totals[it].isComplete }
    val partial = micro.count { totals[it].isLowerBound }
    val unknown = micro.count { totals[it].isUnknown }
    val below = s.assessments.values.count { it.status == AssessmentStatus.BELOW }
    val above = s.assessments.values.count { it.status == AssessmentStatus.ABOVE_MAXIMUM }
    AppCard {
        Text("Полнота данных", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            if (s.days == 1) "Записей: ${totals.itemCount}" else "Дней с записями: ${period.loggedDays} из ${s.days}, записей: ${totals.itemCount}",
            style = AppTheme.type.caption, color = c.textSecondary,
        )
        Text(
            "Витамины и минералы: полные данные — $complete, неполные — $partial, нет данных — $unknown из ${micro.size}.",
            style = AppTheme.type.caption, color = c.textSecondary,
        )
        if (partial + unknown > 0) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Где данные неполные, показана нижняя граница: реальное потребление может быть выше, поэтому недостаток там не утверждается.",
                style = AppTheme.type.caption, color = c.warning,
            )
        }
        if (below + above > 0) {
            Spacer(Modifier.height(6.dp))
            Text("Ниже нормы при полных данных: $below. Выше верхнего предела: $above.", style = AppTheme.type.caption, color = c.textPrimary)
        }
    }
}
