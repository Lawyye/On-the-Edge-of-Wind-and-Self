package com.nutritiontracker.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.AdaptiveTdee
import com.nutritiontracker.core.calc.AdaptiveTdeeResult
import com.nutritiontracker.core.calc.GoalWarning
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.data.Profile
import com.nutritiontracker.domain.DailyTargets
import com.nutritiontracker.domain.Targets
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.ListRow
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SectionHeader
import com.nutritiontracker.ui.components.SkeletonList
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ProfileOverview(
    val loading: Boolean = true,
    val profile: Profile? = null,
    val targets: DailyTargets? = null,
)

class ProfileViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<ProfileOverview> = combine(c.profile.observe(), c.body.observeWeights(), c.profile.observeUserGoals()) { p, w, g ->
        ProfileOverview(false, p, p?.let { runCatching { Targets.compute(it, w, g, LocalDate.now()) }.getOrNull() })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileOverview())

    private val _adaptive = MutableStateFlow<AdaptiveTdeeResult?>(null)
    val adaptive: StateFlow<AdaptiveTdeeResult?> = _adaptive.asStateFlow()

    fun computeAdaptive() {
        viewModelScope.launch { _adaptive.value = c.profile.estimateAdaptive(LocalDate.now()) }
    }

    fun setAdaptive(enabled: Boolean) {
        viewModelScope.launch {
            val p = c.profile.get() ?: return@launch
            c.profile.save(p.copy(adaptiveEnabled = enabled, appliedAdaptiveTdee = if (enabled) p.appliedAdaptiveTdee else null, adaptiveUpdated = if (enabled) p.adaptiveUpdated else null))
            if (enabled) c.profile.maybeUpdateAdaptive(LocalDate.now())
        }
    }

    /** Применяет текущую оценку вручную (с тем же ограничением шага). */
    fun applyAdaptiveNow() {
        viewModelScope.launch {
            val p = c.profile.get() ?: return@launch
            val est = c.profile.estimateAdaptive(LocalDate.now())
            _adaptive.value = est
            if (est is AdaptiveTdeeResult.Estimate) {
                c.profile.save(p.copy(adaptiveEnabled = true, appliedAdaptiveTdee = AdaptiveTdee.nextApplied(p.appliedAdaptiveTdee, est.tdeeKcal), adaptiveUpdated = LocalDate.now()))
            }
        }
    }
}

@Composable
fun ProfileScreen(
    onEdit: () -> Unit,
    onNorms: () -> Unit,
    onSettings: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    onProgress: () -> Unit,
) {
    val vm = appViewModel { c, _ -> ProfileViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val adaptive by vm.adaptive.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    LaunchedEffect(Unit) { vm.computeAdaptive() }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item { Text("Профиль", style = AppTheme.type.title, color = c.textPrimary) }
        if (s.loading) {
            item { SkeletonList(3) }
            return@LazyColumn
        }
        val p = s.profile
        val t = s.targets
        if (p != null) {
            item {
                AppCard(onClick = onEdit) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${p.sex.titleRu} · ${t?.ageYears ?: "—"} лет · ${NutrientFormat.amount(p.heightCm)} см", style = AppTheme.type.bodyStrong, color = c.textPrimary)
                            Text(
                                "Вес ${t?.let { NutrientFormat.kg(it.weightUsedKg) } ?: "—"} кг → цель ${NutrientFormat.kg(p.targetWeightKg)} кг · ${p.activity.titleRu.lowercase()} активность",
                                style = AppTheme.type.caption, color = c.textSecondary,
                            )
                        }
                        Icon(Icons.Outlined.Edit, contentDescription = "Изменить профиль", tint = c.accent)
                    }
                }
            }
            t?.let { item { EnergyCard(p, it) } }
            item { AdaptiveCard(p, adaptive, onToggle = vm::setAdaptive, onApply = vm::applyAdaptiveNow) }
        }
        item {
            AppCard(padding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                NavRow(Icons.Outlined.Science, "Нормы питательных веществ", "NASEM DRI и ваши цели", onNorms)
                HorizontalDivider(color = c.outline.copy(alpha = 0.5f))
                NavRow(Icons.Outlined.MonitorWeight, "Вес и измерения", "История и тенденция", onProgress)
                HorizontalDivider(color = c.outline.copy(alpha = 0.5f))
                NavRow(Icons.Outlined.Settings, "Настройки", "Источники данных, ключ USDA", onSettings)
                HorizontalDivider(color = c.outline.copy(alpha = 0.5f))
                NavRow(Icons.Outlined.Backup, "Резервная копия", "Экспорт JSON и CSV, импорт", onBackup)
                HorizontalDivider(color = c.outline.copy(alpha = 0.5f))
                NavRow(Icons.Outlined.Info, "О приложении", "Формулы, источники, приватность", onAbout)
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    val c = AppTheme.colors
    ListRow(
        title = title,
        subtitle = subtitle,
        leading = { Icon(icon, null, tint = c.textSecondary) },
        onClick = onClick,
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = c.textTertiary) },
    )
}

@Composable
private fun EnergyCard(p: Profile, t: DailyTargets) {
    val c = AppTheme.colors
    val g = t.calorie
    AppCard {
        Text("Энергия", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(8.dp))
        Line("BMR (Миффлин — Сан Жеор)", "${NutrientFormat.kcal(g.bmrKcal)} ккал")
        Line("Коэффициент активности", "×${NutrientFormat.number(p.activity.factor, 3)}")
        Line(if (g.tdeeIsAdaptive) "Расход (по динамике веса)" else "Расход TDEE (оценка)", "${NutrientFormat.kcal(g.tdeeKcal)} ккал")
        Line("Режим", p.deficitMode.titleRu)
        Line("Дефицит", "${NutrientFormat.kcal(g.deficitKcal)} ккал/сут")
        Line("Цель", "${NutrientFormat.kcal(g.goalKcal)} ккал/сут")
        Line("Ожидаемый темп", "${NutrientFormat.number(g.expectedWeeklyChangeKg, 2)} кг/нед (≈)")
        Line("Белки", "${NutrientFormat.amount(t.macros.proteinG)} г (${NutrientFormat.number(p.macroPlan.proteinGPerKg, 1)} г/кг × ${NutrientFormat.kg(t.macros.referenceWeightKg)} кг)")
        Line("Жиры", "${NutrientFormat.amount(t.macros.fatG)} г (${p.macroPlan.fatPercent}%)")
        Line("Углеводы", "${NutrientFormat.amount(t.macros.carbsG)} г (остаток)")
        val warnings = g.warnings.filter { it != GoalWarning.ESTIMATE_ONLY }.map { it.messageRu } + t.macros.warnings
        if (warnings.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                warnings.forEach { InfoBanner(it, kind = BannerKind.WARNING) }
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AppTheme.type.number, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun AdaptiveCard(p: Profile, result: AdaptiveTdeeResult?, onToggle: (Boolean) -> Unit, onApply: () -> Unit) {
    val c = AppTheme.colors
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Адаптивная калорийность", style = AppTheme.type.section, color = c.textPrimary)
                Text("Расход оценивается по записям питания и тенденции веса за 2–4 недели", style = AppTheme.type.caption, color = c.textSecondary)
            }
            Switch(
                checked = p.adaptiveEnabled, onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent),
            )
        }
        Spacer(Modifier.height(8.dp))
        when (result) {
            null -> Text("Считаем…", style = AppTheme.type.caption, color = c.textSecondary)
            is AdaptiveTdeeResult.NotEnoughData -> Text("Пока не хватает данных: ${result.reasonRu}", style = AppTheme.type.caption, color = c.textSecondary)
            is AdaptiveTdeeResult.Estimate -> {
                Line("Оценка расхода", "${NutrientFormat.kcal(result.tdeeKcal)} ккал")
                Line("Среднее потребление", "${NutrientFormat.kcal(result.averageIntakeKcal)} ккал")
                Line("Тенденция веса", "${NutrientFormat.number(result.weightSlopeKgPerWeek, 2)} кг/нед")
                Line("Период", "${Dates.short(result.windowStart)}–${Dates.short(result.windowEnd)}, ${result.loggedDays} дн.")
            }
        }
        if (p.adaptiveEnabled) {
            Spacer(Modifier.height(6.dp))
            Text(
                p.appliedAdaptiveTdee?.let { "Применено: ${NutrientFormat.kcal(it)} ккал" + (p.adaptiveUpdated?.let { d -> " (${Dates.full(d)})" } ?: "") }
                    ?: "Оценка ещё не применялась",
                style = AppTheme.type.caption, color = c.textPrimary,
            )
            Text(
                "Цель обновляется не чаще раза в неделю и не больше чем на ${AdaptiveTdee.MAX_STEP_KCAL.toInt()} ккал за раз. Ежедневные колебания веса не учитываются.",
                style = AppTheme.type.caption, color = c.textTertiary,
            )
            if (result is AdaptiveTdeeResult.Estimate) {
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Применить оценку сейчас", onClick = onApply, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
fun ProfileEditScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> ProfileFormViewModel(c) }
    val f by vm.form.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    Scaffold(
        containerColor = c.background,
        topBar = { AppTopBar("Профиль", onBack = onBack) },
        bottomBar = {
            Column(Modifier.navigationBarsPadding().imePadding().padding(Dimens.screenPadding)) {
                PrimaryButton("Сохранить", onClick = { vm.save(onBack) }, enabled = f.loaded && f.valid && !f.saving, modifier = Modifier.fillMaxWidth())
            }
        },
    ) { padding ->
        if (!f.loaded) {
            Column(Modifier.padding(padding).padding(Dimens.screenPadding)) { SkeletonList(3) }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader("О вас")
            SexSelector(f.sex) { sex -> vm.edit { it.copy(sex = sex) } }
            DateField("Дата рождения", f.birthDate, { d -> vm.edit { it.copy(birthDate = d) } }, error = f.ageError)
            BodyFields(f, vm::edit, weightLabel = "Текущий вес (будет записан взвешиванием за сегодня)")
            SectionHeader("Активность")
            ActivitySelector(f.activity) { a -> vm.edit { it.copy(activity = a) } }
            SectionHeader("Калорийность")
            DeficitSelector(f, vm::edit)
            SectionHeader("Белки, жиры, углеводы")
            MacroPlanEditor(f, vm::edit)
            SectionHeader("Вода")
            WaterGoalField(f, vm::edit)
            SectionHeader("Расчёт")
            GoalPreview(f.preview())
            Spacer(Modifier.height(12.dp))
        }
    }
}
