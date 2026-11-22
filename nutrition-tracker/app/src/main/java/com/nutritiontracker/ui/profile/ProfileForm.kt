package com.nutritiontracker.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.calc.ActivityLevel
import com.nutritiontracker.core.calc.CalorieGoal
import com.nutritiontracker.core.calc.DeficitMode
import com.nutritiontracker.core.calc.EnergyCalculator
import com.nutritiontracker.core.calc.EnergyProfile
import com.nutritiontracker.core.calc.GoalWarning
import com.nutritiontracker.core.calc.MacroCalculator
import com.nutritiontracker.core.calc.MacroGoals
import com.nutritiontracker.core.calc.MacroPlan
import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.data.Profile
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ProfileForm(
    val sex: Sex? = null,
    val birthDate: LocalDate? = null,
    val height: String = "",
    val weight: String = "",
    val target: String = "",
    val activity: ActivityLevel? = null,
    val deficitMode: DeficitMode = DeficitMode.SMALL,
    val customDeficit: String = "500",
    val manualGoal: String = "",
    val proteinGPerKg: Double = MacroPlan.DEFAULT_PROTEIN_G_PER_KG,
    val fatPercent: Int = MacroPlan.DEFAULT_FAT_PERCENT,
    val waterGoal: String = "2000",
    val adaptiveEnabled: Boolean = false,
    val existing: Profile? = null,
    val loaded: Boolean = false,
    val saving: Boolean = false,
) {
    val heightCm get() = parseDecimal(height)
    val weightKg get() = parseDecimal(weight)
    val targetKg get() = parseDecimal(target)
    val age: Int? get() = birthDate?.let { EnergyCalculator.ageYears(it, LocalDate.now()) }

    val heightError: String? get() = heightCm?.let { if (it !in 100.0..250.0) "Рост от 100 до 250 см" else null }
    val weightError: String? get() = weightKg?.let { if (it !in 30.0..300.0) "Вес от 30 до 300 кг" else null }
    val targetError: String? get() = targetKg?.let { if (it !in 30.0..300.0) "Вес от 30 до 300 кг" else null }
    val ageError: String? get() = age?.let { if (it < EnergyCalculator.MIN_AGE) "Приложение рассчитано на взрослых (от 18 лет)" else if (it > 100) "Проверьте дату рождения" else null }
    val waterError: String? get() = parseDecimal(waterGoal)?.let { if (it !in 0.0..10000.0) "От 0 до 10 000 мл" else null }
    val macroPlan: MacroPlan get() = MacroPlan(proteinGPerKg, fatPercent)

    val customDeficitError: String? get() = if (deficitMode == DeficitMode.CUSTOM_DEFICIT) {
        when (val d = parseDecimal(customDeficit)) {
            null -> "Укажите дефицит"
            else -> if (d < 0 || d > 2000) "От 0 до 2000 ккал" else null
        }
    } else null

    val manualGoalError: String? get() = if (deficitMode == DeficitMode.MANUAL_GOAL) {
        when (val g = parseDecimal(manualGoal)) {
            null -> "Укажите калорийность"
            else -> if (g < 500 || g > 6000) "От 500 до 6000 ккал" else null
        }
    } else null

    val bodyValid: Boolean
        get() = sex != null && birthDate != null && ageError == null &&
            heightCm != null && heightError == null && weightKg != null && weightError == null &&
            targetKg != null && targetError == null && activity != null

    val valid: Boolean
        get() = bodyValid && customDeficitError == null && manualGoalError == null && waterError == null

    /** Предварительный расчёт цели; null, пока данных не хватает. */
    fun preview(): CalorieGoal? {
        if (!bodyValid) return null
        return runCatching {
            EnergyCalculator.calorieGoal(
                EnergyProfile(sex!!, birthDate!!, heightCm!!, weightKg!!, activity!!),
                LocalDate.now(),
                deficitMode,
                customDeficitKcal = parseDecimal(customDeficit),
                manualGoalKcal = parseDecimal(manualGoal),
                adaptiveTdeeKcal = if (adaptiveEnabled) existing?.appliedAdaptiveTdee else null,
                targetWeightKg = targetKg,
            )
        }.getOrNull()
    }

    fun toProfile(): Profile = Profile(
        sex = sex!!,
        birthDate = birthDate!!,
        heightCm = heightCm!!,
        startWeightKg = existing?.startWeightKg ?: weightKg!!,
        targetWeightKg = targetKg!!,
        activity = activity!!,
        deficitMode = deficitMode,
        customDeficitKcal = parseDecimal(customDeficit),
        manualGoalKcal = parseDecimal(manualGoal),
        macroPlan = macroPlan,
        waterGoalMl = parseDecimal(waterGoal)?.toInt() ?: 2000,
        adaptiveEnabled = adaptiveEnabled,
        appliedAdaptiveTdee = existing?.appliedAdaptiveTdee,
        adaptiveUpdated = existing?.adaptiveUpdated,
        createdAt = existing?.createdAt ?: System.currentTimeMillis(),
    )
}

class ProfileFormViewModel(private val container: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow(ProfileForm())
    val form: StateFlow<ProfileForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val p = container.profile.get()
            val latestWeight = container.body.observeLatestWeight().first()?.weightKg
            _form.value = if (p == null) {
                ProfileForm(loaded = true)
            } else {
                ProfileForm(
                    sex = p.sex, birthDate = p.birthDate, height = formatInput(p.heightCm),
                    weight = formatInput(latestWeight ?: p.startWeightKg), target = formatInput(p.targetWeightKg),
                    activity = p.activity, deficitMode = p.deficitMode,
                    customDeficit = formatInput(p.customDeficitKcal ?: 500.0), manualGoal = formatInput(p.manualGoalKcal),
                    proteinGPerKg = p.macroPlan.proteinGPerKg, fatPercent = p.macroPlan.fatPercent,
                    waterGoal = p.waterGoalMl.toString(), adaptiveEnabled = p.adaptiveEnabled, existing = p, loaded = true,
                )
            }
        }
    }

    fun edit(transform: (ProfileForm) -> ProfileForm) = _form.update(transform)

    /**
     * Сохраняет профиль. При первом заполнении текущий вес становится начальным
     * и записывается первым взвешиванием.
     */
    fun save(onDone: () -> Unit) {
        val f = _form.value
        if (!f.valid || f.saving) return
        _form.update { it.copy(saving = true) }
        viewModelScope.launch {
            val profile = f.toProfile()
            // Появление профиля сразу уводит с экрана онбординга, поэтому сначала
            // записывается вес, а сама запись не прерывается закрытием экрана.
            withContext(NonCancellable) {
                val weight = f.weightKg!!
                val latest = container.body.observeLatestWeight().first()
                if (latest == null || (f.existing != null && latest.weightKg != weight)) {
                    container.body.saveBody(
                        com.nutritiontracker.data.BodyEntry(0, LocalDate.now(), weight, null, null, null, null, null, null, null),
                    )
                }
                container.profile.save(profile)
            }
            _form.update { it.copy(saving = false, existing = profile) }
            onDone()
        }
    }
}

@Composable
fun SexSelector(selected: Sex?, onSelect: (Sex) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Sex.entries.forEach { s ->
            SelectCard(
                title = s.titleRu,
                subtitle = null,
                selected = selected == s,
                onClick = { onSelect(s) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun SelectCard(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .background(if (selected) c.accentMuted else c.surface, RoundedCornerShape(Dimens.radiusControl))
            .border(1.dp, if (selected) c.accent else c.outline, RoundedCornerShape(Dimens.radiusControl))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Text(title, style = AppTheme.type.bodyStrong, color = c.textPrimary)
        if (subtitle != null) {
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit, error: String? = null, maxDate: LocalDate = LocalDate.now()) {
    val c = AppTheme.colors
    var open by remember { mutableStateOf(false) }
    Column {
        Column(
            Modifier
                .fillMaxWidth()
                .background(c.surface, RoundedCornerShape(Dimens.radiusControl))
                .border(1.dp, if (error != null) c.error else c.outline, RoundedCornerShape(Dimens.radiusControl))
                .clickable { open = true }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(label, style = AppTheme.type.label, color = c.textSecondary)
            Text(date?.let { Dates.full(it) } ?: "Выбрать", style = AppTheme.type.body, color = if (date != null) c.textPrimary else c.textTertiary)
        }
        if (error != null) Text(error, style = AppTheme.type.caption, color = c.error, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = Dates.toMillis(date ?: LocalDate.now().minusYears(30)),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        val d = Dates.fromMillis(ms)
                        onPick(if (d.isAfter(maxDate)) maxDate else d)
                    }
                    open = false
                }) { Text("Готово", color = c.accent) }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена", color = c.textSecondary) } },
        ) {
            DatePicker(state = state, showModeToggle = true)
        }
    }
}

@Composable
fun BodyFields(f: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit, weightLabel: String = "Текущий вес") {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTextField(f.height, { v -> onChange { it.copy(height = v) } }, "Рост", suffix = "см", numeric = true, error = f.heightError)
        AppTextField(f.weight, { v -> onChange { it.copy(weight = v) } }, weightLabel, suffix = "кг", numeric = true, error = f.weightError)
        AppTextField(f.target, { v -> onChange { it.copy(target = v) } }, "Желаемый вес", suffix = "кг", numeric = true, error = f.targetError)
        val w = f.weightKg
        val t = f.targetKg
        if (w != null && t != null && t > w && f.weightError == null && f.targetError == null) {
            InfoBanner("Желаемый вес больше текущего. Расчёт дефицита рассчитан на снижение веса — для набора выберите режим «Поддержание» или свою калорийность.", kind = BannerKind.WARNING)
        }
    }
}

@Composable
fun ActivitySelector(selected: ActivityLevel?, onSelect: (ActivityLevel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ActivityLevel.entries.forEach { a ->
            SelectCard(
                title = "${a.titleRu} · ×${NutrientFormat.number(a.factor, 3)}",
                subtitle = a.descriptionRu,
                selected = selected == a,
                onClick = { onSelect(a) },
            )
        }
    }
}

@Composable
fun DeficitSelector(f: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(DeficitMode.SMALL, DeficitMode.MODERATE, DeficitMode.CUSTOM_DEFICIT, DeficitMode.MANUAL_GOAL, DeficitMode.MAINTAIN).forEach { m ->
            val subtitle = when (m) {
                DeficitMode.SMALL -> "Около −10% от расхода. Мягкий темп, легче соблюдать."
                DeficitMode.MODERATE -> "Около −20% от расхода. Быстрее, но требует дисциплины."
                DeficitMode.CUSTOM_DEFICIT -> "Укажите дефицит в ккал в сутки."
                DeficitMode.MANUAL_GOAL -> "Задайте суточную калорийность сами."
                DeficitMode.MAINTAIN -> "Калорийность на уровне расчётного расхода."
            }
            SelectCard(m.titleRu, subtitle, f.deficitMode == m, onClick = { onChange { it.copy(deficitMode = m) } })
        }
        if (f.deficitMode == DeficitMode.CUSTOM_DEFICIT) {
            AppTextField(
                f.customDeficit, { v -> onChange { it.copy(customDeficit = v) } }, "Дефицит", suffix = "ккал/сут", numeric = true,
                error = f.customDeficitError, supporting = "≈ ${NutrientFormat.number((parseDecimal(f.customDeficit) ?: 0.0) * 7 / EnergyCalculator.KCAL_PER_KG_BODY_WEIGHT, 2)} кг в неделю (приблизительно)",
            )
        }
        if (f.deficitMode == DeficitMode.MANUAL_GOAL) {
            AppTextField(f.manualGoal, { v -> onChange { it.copy(manualGoal = v) } }, "Калорийность", suffix = "ккал", numeric = true, error = f.manualGoalError)
        }
    }
}

@Composable
fun GoalPreview(goal: CalorieGoal?) {
    val c = AppTheme.colors
    if (goal == null) {
        InfoBanner("Заполните пол, дату рождения, рост, вес и активность — здесь появится расчёт.")
        return
    }
    AppCard {
        Text("Ваша цель", style = AppTheme.type.label, color = c.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(NutrientFormat.kcal(goal.goalKcal), style = AppTheme.type.hero, color = c.textPrimary)
            Spacer(Modifier.width(6.dp))
            Text("ккал в день", style = AppTheme.type.caption, color = c.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
        }
        Spacer(Modifier.height(10.dp))
        PreviewLine("Основной обмен (BMR, Миффлин — Сан Жеор)", "${NutrientFormat.kcal(goal.bmrKcal)} ккал")
        PreviewLine(if (goal.tdeeIsAdaptive) "Расход по реальной динамике веса" else "Расход энергии (TDEE)", "${NutrientFormat.kcal(goal.tdeeKcal)} ккал")
        PreviewLine("Дефицит", "${NutrientFormat.kcal(goal.deficitKcal)} ккал")
        PreviewLine("Ожидаемый темп", "${NutrientFormat.number(goal.expectedWeeklyChangeKg, 2)} кг/нед")
    }
    val shown = goal.warnings.filter { it != GoalWarning.ESTIMATE_ONLY }
    if (shown.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shown.forEach { w ->
                val severe = w == GoalWarning.VERY_LOW_CALORIE || w == GoalWarning.BELOW_BMR || w == GoalWarning.TARGET_BMI_LOW
                InfoBanner(w.messageRu, kind = if (severe) BannerKind.ERROR else BannerKind.WARNING)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    InfoBanner(
        "Расход энергии — оценка по формуле, а не измерение; темп снижения веса рассчитан приблизительно (≈7700 ккал на 1 кг). " +
            "Это не медицинская рекомендация.",
    )
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = AppTheme.type.number, color = AppTheme.colors.textPrimary)
    }
}

/** Граммы БЖУ по текущим данным формы; null — данных для расчёта не хватает. */
fun ProfileForm.macroPreview(): MacroGoals? {
    val goal = preview() ?: return null
    val weight = weightKg ?: return null
    val target = targetKg ?: return null
    val losing = deficitMode != com.nutritiontracker.core.calc.DeficitMode.MAINTAIN && goal.deficitKcal > 0
    return runCatching {
        MacroCalculator.goals(goal.goalKcal, MacroCalculator.referenceWeight(weight, target, heightCm ?: return null, losing), macroPlan)
    }.getOrNull()
}

@Composable
fun MacroPlanEditor(f: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    val c = AppTheme.colors
    val m = f.macroPreview()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Stepper(
            label = "Белки",
            value = "${NutrientFormat.number(f.proteinGPerKg, 1)} г/кг",
            hint = m?.let { "${NutrientFormat.amount(it.proteinG)} г · от ${NutrientFormat.kg(it.referenceWeightKg)} кг" + if (it.referenceWeightKg != f.weightKg) " (желаемый вес)" else "" }
                ?: "При снижении веса обычно 1,2–1,6 г/кг",
            color = c.protein,
            onMinus = { onChange { it.copy(proteinGPerKg = (Math.round((it.proteinGPerKg - 0.1) * 10) / 10.0).coerceAtLeast(MacroPlan.MIN_PROTEIN_G_PER_KG)) } },
            onPlus = { onChange { it.copy(proteinGPerKg = (Math.round((it.proteinGPerKg + 0.1) * 10) / 10.0).coerceAtMost(MacroPlan.MAX_PROTEIN_G_PER_KG)) } },
        )
        Stepper(
            label = "Жиры",
            value = "${f.fatPercent}%",
            hint = m?.let { "${NutrientFormat.amount(it.fatG)} г · допустимо 20–35% калорийности" } ?: "Допустимо 20–35% калорийности",
            color = c.fat,
            onMinus = { onChange { it.copy(fatPercent = (it.fatPercent - 5).coerceAtLeast(MacroPlan.FAT_PERCENT_RANGE.first)) } },
            onPlus = { onChange { it.copy(fatPercent = (it.fatPercent + 5).coerceAtMost(MacroPlan.FAT_PERCENT_RANGE.last)) } },
        )
        Row(
            Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(Dimens.radiusControl)).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            com.nutritiontracker.ui.components.Dot(c.carbs)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Углеводы", style = AppTheme.type.body, color = c.textPrimary)
                Text("Оставшиеся калории", style = AppTheme.type.caption, color = c.textSecondary)
            }
            Text(m?.let { "${NutrientFormat.amount(it.carbsG)} г" } ?: "—", style = AppTheme.type.number, color = c.textPrimary)
        }
        m?.warnings?.forEach { InfoBanner(it, kind = BannerKind.WARNING) }
        InfoBanner(
            "Минимум белка по NASEM (RDA) — 0,8 г/кг для здорового человека без дефицита. При снижении веса для сохранения мышц " +
                "обычно рекомендуют 1,2–1,6 г/кг; приложение считает от желаемого веса, чтобы при большом весе норма не завышалась.",
        )
    }
}

@Composable
private fun Stepper(label: String, value: String, hint: String, color: androidx.compose.ui.graphics.Color, onMinus: () -> Unit, onPlus: () -> Unit) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(Dimens.radiusControl)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        com.nutritiontracker.ui.components.Dot(color)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = AppTheme.type.body, color = c.textPrimary)
            Text(hint, style = AppTheme.type.caption, color = c.textSecondary)
        }
        TextButton(onClick = onMinus) { Text("−", style = AppTheme.type.title, color = c.textPrimary) }
        Text(value, style = AppTheme.type.number, color = c.textPrimary)
        TextButton(onClick = onPlus) { Text("+", style = AppTheme.type.title, color = c.textPrimary) }
    }
}

@Composable
fun WaterGoalField(f: ProfileForm, onChange: ((ProfileForm) -> ProfileForm) -> Unit) {
    AppTextField(
        f.waterGoal, { v -> onChange { it.copy(waterGoal = v) } }, "Цель по воде", suffix = "мл", numeric = true, error = f.waterError,
        supporting = "Настройте под себя. Для справки: NASEM указывает адекватное потребление всей воды (с напитками и пищей) 2,7 л для женщин и 3,7 л для мужчин.",
    )
}
