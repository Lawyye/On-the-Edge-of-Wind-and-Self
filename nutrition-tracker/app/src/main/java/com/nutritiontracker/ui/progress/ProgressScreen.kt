package com.nutritiontracker.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.nutritiontracker.core.calc.WeightSummary
import com.nutritiontracker.core.calc.WeightTrend
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.data.BodyEntry
import com.nutritiontracker.data.Profile
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.DangerTextButton
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.LineChart
import com.nutritiontracker.ui.components.LinePoint
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.ProgressBar
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.diary.DiaryDatePicker
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProgressState(
    val loading: Boolean = true,
    val entries: List<BodyEntry> = emptyList(),
    val profile: Profile? = null,
    val summary: WeightSummary? = null,
    val forecast: LocalDate? = null,
)

class ProgressViewModel(private val c: AppContainer) : ViewModel() {
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 2)
    val messages = _messages.asSharedFlow()

    val state: StateFlow<ProgressState> = combine(c.body.observeBody(), c.profile.observe(), c.body.observeWeights()) { entries, profile, weights ->
        ProgressState(
            loading = false,
            entries = entries.sortedByDescending { it.date },
            profile = profile,
            summary = if (weights.isEmpty()) null else WeightTrend.summary(weights, profile?.startWeightKg, profile?.targetWeightKg, LocalDate.now()),
            forecast = profile?.let { WeightTrend.forecastGoalDate(weights, it.targetWeightKg, LocalDate.now()) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressState())

    fun save(e: BodyEntry, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                c.body.saveBody(e)
                onDone()
            } catch (ex: IllegalArgumentException) {
                _messages.emit(ex.message ?: "Не удалось сохранить")
            }
        }
    }

    fun delete(id: Long) { viewModelScope.launch { c.body.deleteBody(id) } }
}

private enum class Range(val title: String, val days: Long?) { MONTH("Месяц", 30), QUARTER("3 месяца", 90), YEAR("Год", 365), ALL("Всё", null) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> ProgressViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<BodyEntry?>(null) }
    var range by remember { mutableStateOf(Range.QUARTER) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(
        containerColor = c.background,
        topBar = { AppTopBar("Вес и измерения", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editing = BodyEntry(0, LocalDate.now(), null, null, null, null, null, null, null, null) },
                containerColor = c.accent, contentColor = c.onAccent,
            ) { Icon(Icons.Filled.Add, contentDescription = "Добавить запись") }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Dimens.screenPadding, end = Dimens.screenPadding, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            val weights = s.entries.filter { it.weightKg != null }
            if (!s.loading && s.entries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.MonitorWeight, "Записей пока нет",
                        "Взвешивайтесь в одно и то же время, например утром. Приложение покажет тенденцию по среднему за 7 дней.",
                    )
                }
            }
            s.summary?.let { sum -> item { SummaryCard(sum, s.profile, s.forecast) } }
            if (weights.size >= 2) {
                item {
                    AppCard {
                        Text("График", style = AppTheme.type.section, color = c.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        SegmentedControl(Range.entries, range, { it.title }, { range = it })
                        Spacer(Modifier.height(12.dp))
                        val from = range.days?.let { LocalDate.now().minusDays(it) }
                        val points = weights.filter { from == null || !it.date.isBefore(from) }.map { com.nutritiontracker.core.calc.WeightPoint(it.date, it.weightKg!!) }
                        val trend = WeightTrend.smoothed(weights.map { com.nutritiontracker.core.calc.WeightPoint(it.date, it.weightKg!!) })
                            .filter { from == null || !it.date.isBefore(from) }
                        if (points.size >= 2) {
                            LineChart(
                                raw = points.map { LinePoint(it.date.toEpochDay(), it.weightKg) },
                                trend = trend.map { LinePoint(it.date.toEpochDay(), it.weightKg) },
                                target = s.profile?.targetWeightKg,
                                description = "График веса",
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Точки — взвешивания, линия — среднее за 7 дней, пунктир — цель.", style = AppTheme.type.caption, color = c.textTertiary)
                        } else {
                            Text("За выбранный период мало взвешиваний.", style = AppTheme.type.caption, color = c.textSecondary)
                        }
                    }
                }
            }
            if (s.entries.isNotEmpty()) {
                item { Text("История", style = AppTheme.type.section, color = c.textPrimary, modifier = Modifier.padding(top = 4.dp)) }
                items(s.entries, key = { it.id }) { e ->
                    AppCard(onClick = { editing = e }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(Dates.full(e.date), style = AppTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
                            Text(e.weightKg?.let { "${NutrientFormat.kg(it)} кг" } ?: "—", style = AppTheme.type.number, color = c.textPrimary)
                        }
                        val m = measurementsText(e)
                        if (m.isNotEmpty()) Text(m, style = AppTheme.type.caption, color = c.textSecondary)
                        e.note?.let { Text(it, style = AppTheme.type.caption, color = c.textTertiary) }
                    }
                }
            }
        }
    }

    editing?.let { e ->
        BodyEntrySheet(
            initial = e,
            onDismiss = { editing = null },
            onSave = { vm.save(it) { editing = null } },
            onDelete = if (e.id != 0L) ({ vm.delete(e.id); editing = null }) else null,
        )
    }
}

private fun measurementsText(e: BodyEntry): String = listOfNotNull(
    e.waistCm?.let { "талия ${NutrientFormat.kg(it)}" },
    e.hipsCm?.let { "бёдра ${NutrientFormat.kg(it)}" },
    e.chestCm?.let { "грудь ${NutrientFormat.kg(it)}" },
    e.neckCm?.let { "шея ${NutrientFormat.kg(it)}" },
    e.armCm?.let { "рука ${NutrientFormat.kg(it)}" },
    e.thighCm?.let { "бедро (нога) ${NutrientFormat.kg(it)}" },
).joinToString(" · ").let { if (it.isEmpty()) it else "$it см" }

@Composable
private fun SummaryCard(sum: WeightSummary, profile: Profile?, forecast: LocalDate?) {
    val c = AppTheme.colors
    AppCard {
        Row {
            Metric("Последний", sum.latest?.let { "${NutrientFormat.kg(it.weightKg)} кг" } ?: "—", Modifier.weight(1f))
            Metric("Среднее за 7 дней", sum.average7?.let { "${NutrientFormat.kg(it)} кг" } ?: "—", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Metric("За 30 дней", sum.change30Days?.let { "${NutrientFormat.signedKg(it)} кг" } ?: "мало данных", Modifier.weight(1f))
            Metric("От начального", sum.changeFromStart?.let { "${NutrientFormat.signedKg(it)} кг" } ?: "—", Modifier.weight(1f))
        }
        if (profile != null) {
            Spacer(Modifier.height(12.dp))
            val p = sum.progressToTarget
            Text(
                "Цель ${NutrientFormat.kg(profile.targetWeightKg)} кг" + (sum.remainingToTargetKg?.let { r -> if (r > 0) " · осталось ${NutrientFormat.kg(r)} кг" else " · достигнута" } ?: ""),
                style = AppTheme.type.caption, color = c.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
            ProgressBar(p?.toFloat()?.coerceAtLeast(0f), c.success)
            forecast?.let { d ->
                Spacer(Modifier.height(6.dp))
                Text(
                    "При текущем темпе цель — примерно к ${Dates.full(d)}. Прогноз по тенденции за 4 недели, не обещание.",
                    style = AppTheme.type.caption, color = c.accent,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Сравнения идут по среднему за 7 дней: ежедневные колебания связаны в основном с водой и солью и не означают набор или потерю жира.",
            style = AppTheme.type.caption, color = c.textTertiary,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.textSecondary)
        Text(value, style = AppTheme.type.numberLarge, color = AppTheme.colors.textPrimary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BodyEntrySheet(initial: BodyEntry, onDismiss: () -> Unit, onSave: (BodyEntry) -> Unit, onDelete: (() -> Unit)?) {
    val c = AppTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(initial.date) }
    var weight by remember { mutableStateOf(formatInput(initial.weightKg)) }
    var waist by remember { mutableStateOf(formatInput(initial.waistCm)) }
    var hips by remember { mutableStateOf(formatInput(initial.hipsCm)) }
    var chest by remember { mutableStateOf(formatInput(initial.chestCm)) }
    var neck by remember { mutableStateOf(formatInput(initial.neckCm)) }
    var arm by remember { mutableStateOf(formatInput(initial.armCm)) }
    var thigh by remember { mutableStateOf(formatInput(initial.thighCm)) }
    var note by remember { mutableStateOf(initial.note ?: "") }
    var pickDate by remember { mutableStateOf(false) }

    fun err(t: String, range: ClosedFloatingPointRange<Double>): String? =
        if (t.isBlank()) null else parseDecimal(t)?.let { if (it in range) null else "От ${range.start.toInt()} до ${range.endInclusive.toInt()}" } ?: "Не число"

    val errors = listOf(err(weight, 20.0..400.0)) + listOf(waist, hips, chest, neck, arm, thigh).map { err(it, 5.0..300.0) }
    val anyValue = listOf(weight, waist, hips, chest, neck, arm, thigh).any { it.isNotBlank() }
    val valid = errors.all { it == null } && anyValue

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding).navigationBarsPadding().padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(if (initial.id == 0L) "Новая запись" else "Запись", style = AppTheme.type.section, color = c.textPrimary)
            SecondaryButton("Дата: ${Dates.full(date)}", onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth())
            AppTextField(weight, { weight = it }, "Вес", suffix = "кг", numeric = true, error = errors[0])
            HorizontalDivider(color = c.outline)
            Text("Измерения (необязательно)", style = AppTheme.type.label, color = c.textSecondary)
            AppTextField(waist, { waist = it }, "Талия", suffix = "см", numeric = true, error = errors[1])
            AppTextField(hips, { hips = it }, "Бёдра (обхват)", suffix = "см", numeric = true, error = errors[2])
            AppTextField(chest, { chest = it }, "Грудь", suffix = "см", numeric = true, error = errors[3])
            AppTextField(neck, { neck = it }, "Шея", suffix = "см", numeric = true, error = errors[4])
            AppTextField(arm, { arm = it }, "Рука", suffix = "см", numeric = true, error = errors[5])
            AppTextField(thigh, { thigh = it }, "Бедро (нога)", suffix = "см", numeric = true, error = errors[6])
            AppTextField(note, { note = it }, "Заметка", singleLine = false)
            if (!anyValue) Text("Укажите вес или хотя бы одно измерение", style = AppTheme.type.caption, color = c.textSecondary)
            PrimaryButton(
                "Сохранить",
                onClick = {
                    onSave(
                        BodyEntry(
                            initial.id, date, parseDecimal(weight), parseDecimal(waist), parseDecimal(hips), parseDecimal(chest),
                            parseDecimal(neck), parseDecimal(arm), parseDecimal(thigh), note,
                        ),
                    )
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Одна запись на дату: при сохранении на дату, где запись уже есть, она обновится.", style = AppTheme.type.caption, color = c.textTertiary)
            if (onDelete != null) DangerTextButton("Удалить запись", onClick = onDelete)
        }
    }
    if (pickDate) DiaryDatePicker(date, onDismiss = { pickDate = false }, onPick = { d -> date = if (d.isAfter(LocalDate.now())) LocalDate.now() else d; pickDate = false })
}
