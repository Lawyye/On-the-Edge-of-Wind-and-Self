package com.nutritiontracker.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.WeightPoint
import com.nutritiontracker.core.calc.WeightTrend
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.norms.AssessmentStatus
import com.nutritiontracker.core.norms.NormEvaluator
import com.nutritiontracker.domain.DailyTargets
import com.nutritiontracker.domain.PeriodSummary
import com.nutritiontracker.domain.Targets
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.BarChart
import com.nutritiontracker.ui.components.BarPoint
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.LineChart
import com.nutritiontracker.ui.components.LinePoint
import com.nutritiontracker.ui.components.MacroRow
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.SkeletonList
import com.nutritiontracker.ui.components.StatusPill
import com.nutritiontracker.ui.diary.DiaryDatePicker
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
import java.time.temporal.ChronoUnit

enum class StatsPeriod(val title: String) { DAY("День"), WEEK("Неделя"), MONTH("Месяц"), CUSTOM("Период") }

data class LowNutrient(val nutrient: Nutrient, val daysBelow: Int, val daysWithCompleteData: Int)

data class StatsState(
    val loading: Boolean = true,
    val period: StatsPeriod = StatsPeriod.WEEK,
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val summary: PeriodSummary? = null,
    val targets: DailyTargets? = null,
    val weights: List<WeightPoint> = emptyList(),
    val water: Map<LocalDate, Int> = emptyMap(),
    val lowNutrients: List<LowNutrient> = emptyList(),
    val aboveLimit: List<Pair<Nutrient, Int>> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(private val c: AppContainer) : ViewModel() {
    private val range = MutableStateFlow(Triple(StatsPeriod.WEEK, LocalDate.now().minusDays(6), LocalDate.now()))

    val state: StateFlow<StatsState> = range.flatMapLatest { (p, from, to) ->
        combine(
            c.diary.observeRange(from, to),
            c.profile.observe(),
            c.body.observeWeights(),
            c.profile.observeUserGoals(),
            c.body.observeWaterTotals(from, to),
        ) { entries, profile, weights, goals, water ->
            val summary = PeriodSummary.of(from, to, entries)
            val targets = profile?.let { runCatching { Targets.compute(it, weights, goals, to) }.getOrNull() }
            val norms = targets?.norms ?: emptyMap()
            val logged = summary.days.filter { it.entries.isNotEmpty() }
            val micro = Nutrient.vitamins + Nutrient.minerals + listOf(Nutrient.FIBER)
            val low = micro.mapNotNull { n ->
                val norm = norms[n] ?: return@mapNotNull null
                val assessed = logged.map { NormEvaluator.assess(it.totals[n], norm) }
                val complete = assessed.count { it.total.isComplete }
                val below = assessed.count { it.status == AssessmentStatus.BELOW }
                if (complete >= 3 && below * 2 > complete) LowNutrient(n, below, complete) else null
            }.sortedByDescending { it.daysBelow.toDouble() / it.daysWithCompleteData }
            val above = Nutrient.entries.mapNotNull { n ->
                val norm = norms[n] ?: return@mapNotNull null
                val days = logged.count { NormEvaluator.assess(it.totals[n], norm).status == AssessmentStatus.ABOVE_MAXIMUM }
                if (days > 0) n to days else null
            }
            StatsState(false, p, from, to, summary, targets, weights, water, low, above)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsState())

    fun setPeriod(p: StatsPeriod) {
        val today = LocalDate.now()
        range.value = when (p) {
            StatsPeriod.DAY -> Triple(p, today, today)
            StatsPeriod.WEEK -> Triple(p, today.minusDays(6), today)
            StatsPeriod.MONTH -> Triple(p, today.minusDays(29), today)
            StatsPeriod.CUSTOM -> Triple(p, range.value.second, range.value.third)
        }
    }

    fun setCustom(from: LocalDate, to: LocalDate) {
        val (a, b) = if (from.isAfter(to)) to to from else from to to
        val limited = if (ChronoUnit.DAYS.between(a, b) > 365) b.minusDays(365) else a
        range.value = Triple(StatsPeriod.CUSTOM, limited, b)
    }
}

@Composable
fun StatsScreen(onOpenAnalysis: (LocalDate, Int) -> Unit, onOpenProgress: () -> Unit) {
    val vm = appViewModel { c, _ -> StatsViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var pickFrom by remember { mutableStateOf(false) }
    var pickTo by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item {
            Column {
                Text("Статистика", style = AppTheme.type.title, color = c.textPrimary)
                Spacer(Modifier.height(12.dp))
                SegmentedControl(StatsPeriod.entries, s.period, { it.title }, vm::setPeriod)
                Spacer(Modifier.height(8.dp))
                if (s.period == StatsPeriod.CUSTOM) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SecondaryButton("С ${Dates.short(s.from)}", onClick = { pickFrom = true }, modifier = Modifier.weight(1f))
                        SecondaryButton("По ${Dates.short(s.to)}", onClick = { pickTo = true }, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        if (s.from == s.to) Dates.full(s.to) else "${Dates.dayMonth(s.from)} — ${Dates.dayMonth(s.to)}",
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                }
            }
        }
        val summary = s.summary
        if (s.loading || summary == null) {
            item { SkeletonList(3) }
            return@LazyColumn
        }
        if (summary.loggedDays == 0) {
            item { EmptyState(Icons.Outlined.BarChart, "Нет записей за период", "Статистика появится, когда в дневнике будут записи.") }
        } else {
            item { CaloriesCard(s, summary) }
            item { MacrosCard(s, summary) }
            item { NutrientsCard(s, summary, onDetails = { onOpenAnalysis(s.to, ChronoUnit.DAYS.between(s.from, s.to).toInt().plus(1).coerceAtMost(31)) }) }
            item { GoalsCard(s, summary) }
        }
        item { WeightCard(s, onOpenProgress) }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (pickFrom) DiaryDatePicker(s.from, onDismiss = { pickFrom = false }, onPick = { vm.setCustom(it, s.to); pickFrom = false })
    if (pickTo) DiaryDatePicker(s.to, onDismiss = { pickTo = false }, onPick = { vm.setCustom(s.from, it); pickTo = false })
}

@Composable
private fun CaloriesCard(s: StatsState, summary: PeriodSummary) {
    val c = AppTheme.colors
    val avg = summary.dailyAverage?.get(Nutrient.ENERGY)
    AppCard {
        Text("Калории", style = AppTheme.type.section, color = c.textPrimary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text((if (avg?.isLowerBound == true) "≥ " else "") + NutrientFormat.kcal(avg?.value ?: 0.0), style = AppTheme.type.hero, color = c.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text("ккал в среднем за день", style = AppTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Text(
            "По ${plural(summary.loggedDays, "дню", "дням", "дням")} с записями из ${summary.days.size}" +
                (s.targets?.let { " · цель ${NutrientFormat.kcal(it.calorie.goalKcal)} ккал" } ?: ""),
            style = AppTheme.type.caption, color = c.textSecondary,
        )
        if (summary.days.size > 1) {
            Spacer(Modifier.height(12.dp))
            BarChart(
                points = summary.days.map { d ->
                    BarPoint(
                        label = if (summary.days.size <= 7) Dates.weekdayShort(d.date) else d.date.dayOfMonth.toString(),
                        value = if (d.entries.isEmpty()) null else d.energy.value,
                        highlight = d.date == s.to,
                    )
                },
                goal = s.targets?.calorie?.goalKcal,
                description = "Калории по дням",
            )
        }
    }
}

@Composable
private fun MacrosCard(s: StatsState, summary: PeriodSummary) {
    val c = AppTheme.colors
    val avg = summary.dailyAverage ?: return
    val m = s.targets?.macros
    AppCard {
        Text("БЖУ и клетчатка в среднем за день", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroRow("Белки", avg[Nutrient.PROTEIN], m?.proteinG, c.protein)
            MacroRow("Жиры", avg[Nutrient.FAT], m?.fatG, c.fat)
            MacroRow("Углеводы", avg[Nutrient.CARBS], m?.carbsG, c.carbs)
            MacroRow("Клетчатка", avg[Nutrient.FIBER], s.targets?.norms?.get(Nutrient.FIBER)?.target, c.fiber)
        }
    }
}

@Composable
private fun NutrientsCard(s: StatsState, summary: PeriodSummary, onDetails: () -> Unit) {
    val c = AppTheme.colors
    AppCard {
        Text("Витамины и минералы", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        if (s.lowNutrients.isEmpty()) {
            Text(
                if (summary.loggedDays < 3) "Нужно хотя бы 3 дня записей, чтобы увидеть устойчивые закономерности."
                else "Устойчивого недобора по веществам с полными данными не видно.",
                style = AppTheme.type.caption, color = c.textSecondary,
            )
        } else {
            Text("Чаще ниже нормы (по дням с полными данными):", style = AppTheme.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            s.lowNutrients.take(8).forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(l.nutrient.titleRu, style = AppTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                    StatusPill("${l.daysBelow} из ${l.daysWithCompleteData} дн.", c.warning)
                }
            }
        }
        if (s.aboveLimit.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("Выше верхнего предела:", style = AppTheme.type.caption, color = c.textSecondary)
            s.aboveLimit.forEach { (n, days) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(n.titleRu, style = AppTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                    StatusPill(plural(days, "день", "дня", "дней"), c.error)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        InfoBanner(
            "Это оценка по дневнику и неполным базам продуктов, а не диагноз. О дефиците витамина или минерала в организме можно судить только по анализам.",
            kind = BannerKind.INFO,
        )
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Полный анализ за период", onClick = onDetails, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun GoalsCard(s: StatsState, summary: PeriodSummary) {
    val c = AppTheme.colors
    val t = s.targets ?: return
    val logged = summary.days.filter { it.entries.isNotEmpty() }
    val withinKcal = logged.count { d -> d.energy.value?.let { it <= t.calorie.goalKcal * 1.03 } == true }
    val protein = logged.count { d -> d.totals[Nutrient.PROTEIN].value?.let { it >= t.macros.proteinG * 0.9 } == true }
    val fiberTarget = t.norms[Nutrient.FIBER]?.target
    val fiber = if (fiberTarget == null) null else logged.count { d -> d.totals[Nutrient.FIBER].value?.let { it >= fiberTarget } == true }
    val waterDays = if (t.waterGoalMl > 0) summary.days.count { (s.water[it.date] ?: 0) >= t.waterGoalMl } else null
    AppCard {
        Text("Выполнение целей", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        GoalLine("Калории в пределах цели", withinKcal, logged.size)
        GoalLine("Белок не меньше 90% цели", protein, logged.size)
        fiber?.let { GoalLine("Клетчатка по норме", it, logged.size) }
        waterDays?.let { GoalLine("Вода по цели", it, summary.days.size) }
    }
}

@Composable
private fun GoalLine(label: String, done: Int, of: Int) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.type.body, color = c.textSecondary, modifier = Modifier.weight(1f))
        Text("$done из $of", style = AppTheme.type.number, color = if (of > 0 && done * 2 >= of) c.success else c.textPrimary)
    }
}

@Composable
private fun WeightCard(s: StatsState, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val inRange = WeightTrend.daily(s.weights).filter { !it.date.isBefore(s.from.minusDays(6)) && !it.date.isAfter(s.to) }
    AppCard(onClick = onOpen) {
        Text("Динамика веса", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(6.dp))
        if (inRange.size < 2) {
            Text("Недостаточно взвешиваний за период. Нажмите, чтобы добавить вес.", style = AppTheme.type.caption, color = c.textSecondary)
        } else {
            val trend = WeightTrend.smoothed(s.weights).filter { it.date in inRange.first().date..inRange.last().date }
            LineChart(
                raw = inRange.map { LinePoint(it.date.toEpochDay(), it.weightKg) },
                trend = trend.map { LinePoint(it.date.toEpochDay(), it.weightKg) },
                description = "Вес за период",
            )
            val change = (trend.lastOrNull()?.weightKg ?: 0.0) - (trend.firstOrNull()?.weightKg ?: 0.0)
            Spacer(Modifier.height(6.dp))
            Text(
                "Изменение по скользящему среднему за 7 дней: ${NutrientFormat.signedKg(change)} кг. Колебания отдельных дней — это в основном вода, а не жир.",
                style = AppTheme.type.caption, color = c.textSecondary,
            )
        }
    }
}
