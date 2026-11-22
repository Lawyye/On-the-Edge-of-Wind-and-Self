package com.nutritiontracker.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MonitorWeight
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.CalorieRing
import com.nutritiontracker.ui.components.ChoiceChip
import com.nutritiontracker.ui.components.MacroRow
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.ProgressBar
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SectionHeader
import com.nutritiontracker.ui.components.SkeletonBox
import com.nutritiontracker.ui.components.StatusPill
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.navigation.mealForNow
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import java.time.LocalDate

@Composable
fun HomeScreen(
    onAddFood: (LocalDate, MealType) -> Unit,
    onVoice: (LocalDate, MealType) -> Unit,
    onOpenDiary: () -> Unit,
    onOpenAnalysis: (LocalDate) -> Unit,
    onOpenProgress: () -> Unit,
) {
    val vm = appViewModel { c, _ -> HomeViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var waterDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().background(c.background).statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(Dimens.gap),
    ) {
        item {
            Column(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
                Text("Сегодня", style = AppTheme.type.title, color = c.textPrimary)
                Text(Dates.subtitle(s.today), style = AppTheme.type.caption, color = c.textSecondary)
            }
        }
        if (s.loading) {
            item { AppCard { SkeletonBox(Modifier.fillMaxWidth(), height = 168.dp, shapeRadius = 20.dp) } }
            item { AppCard { SkeletonBox(Modifier.fillMaxWidth(), height = 90.dp) } }
            return@LazyColumn
        }
        item { CaloriesCard(s, onClick = { onOpenAnalysis(s.today) }) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Добавить продукт", onClick = { onAddFood(s.today, mealForNow()) }, icon = Icons.Filled.Add, modifier = Modifier.weight(1f))
                Box(
                    Modifier.size(52.dp).background(c.surface, RoundedCornerShape(Dimens.radiusControl)).clickable { onVoice(s.today, mealForNow()) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Outlined.Mic, contentDescription = "Голосовой ввод", tint = c.accent) }
            }
        }
        item { MacrosCard(s) }
        item { MealsCard(s, onAdd = { meal -> onAddFood(s.today, meal) }, onOpenDiary = onOpenDiary) }
        item { WaterCard(s.waterMl, s.targets?.waterGoalMl ?: s.profile?.waterGoalMl ?: 0, onAdd = vm::addWater, onCustom = { waterDialog = true }) }
        item { WeightCard(s, onOpenProgress) }
        item { GoalStatusCard(s) }
        item { Spacer(Modifier.height(8.dp)) }
    }

    if (waterDialog) {
        WaterDialog(onDismiss = { waterDialog = false }, onAdd = { vm.addWater(it); waterDialog = false })
    }
}

@Composable
private fun CaloriesCard(s: HomeState, onClick: () -> Unit) {
    val c = AppTheme.colors
    val energy = s.summary.energy
    val eaten = energy.value ?: 0.0
    val goal = s.targets?.calorie?.goalKcal
    val remaining = goal?.let { it - eaten }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalorieRing(eaten = eaten, goal = goal, size = 148.dp, stroke = 12.dp) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (remaining != null && remaining >= 0) {
                        Text(NutrientFormat.kcal(remaining), style = AppTheme.type.numberLarge, color = c.textPrimary)
                        Text("осталось", style = AppTheme.type.caption, color = c.textSecondary)
                    } else if (remaining != null) {
                        Text("+" + NutrientFormat.kcal(-remaining), style = AppTheme.type.numberLarge, color = c.warning)
                        Text("сверх цели", style = AppTheme.type.caption, color = c.textSecondary)
                    } else {
                        Text(NutrientFormat.kcal(eaten), style = AppTheme.type.numberLarge, color = c.textPrimary)
                        Text("ккал", style = AppTheme.type.caption, color = c.textSecondary)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Stat("Цель", goal?.let { "${NutrientFormat.kcal(it)} ккал" } ?: "—")
                Stat("Съедено", (if (energy.isLowerBound) "≥ " else "") + "${NutrientFormat.kcal(eaten)} ккал")
                s.targets?.calorie?.let { g ->
                    val actualDeficit = g.tdeeKcal - eaten
                    Stat("Расход ≈", "${NutrientFormat.kcal(g.tdeeKcal)} ккал")
                    Stat(
                        if (actualDeficit >= 0) "Дефицит сейчас" else "Профицит сейчас",
                        "${NutrientFormat.kcal(kotlin.math.abs(actualDeficit))} из ${NutrientFormat.kcal(g.deficitKcal)}",
                    )
                }
            }
        }
        if (energy.isLowerBound) {
            Spacer(Modifier.height(10.dp))
            Text(
                "У ${energy.itemsWithoutData} записей нет данных о калориях — реальная сумма больше.",
                style = AppTheme.type.caption, color = c.warning,
            )
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = AppTheme.type.label, color = AppTheme.colors.textSecondary)
        Text(value, style = AppTheme.type.number, color = AppTheme.colors.textPrimary)
    }
}

@Composable
private fun MacrosCard(s: HomeState) {
    val c = AppTheme.colors
    val t = s.summary.totals
    val m = s.targets?.macros
    AppCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MacroRow("Белки", t[Nutrient.PROTEIN], m?.proteinG, c.protein)
            MacroRow("Жиры", t[Nutrient.FAT], m?.fatG, c.fat)
            MacroRow("Углеводы", t[Nutrient.CARBS], m?.carbsG, c.carbs)
            MacroRow("Клетчатка", t[Nutrient.FIBER], s.targets?.norms?.get(Nutrient.FIBER)?.target, c.fiber)
        }
    }
}

@Composable
private fun MealsCard(s: HomeState, onAdd: (MealType) -> Unit, onOpenDiary: () -> Unit) {
    val c = AppTheme.colors
    AppCard(padding = PaddingValues(horizontal = Dimens.cardPadding, vertical = 6.dp)) {
        SectionHeader("Приёмы пищи", action = "Дневник", onAction = onOpenDiary)
        MealType.entries.forEach { meal ->
            val total = s.summary.byMeal.getValue(meal)
            val count = s.summary.entries.count { it.meal == meal }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(meal.titleRu, style = AppTheme.type.body, color = c.textPrimary)
                    Text(
                        if (count == 0) "Пока пусто" else com.nutritiontracker.ui.plural(count, "продукт", "продукта", "продуктов"),
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                }
                val e = total[Nutrient.ENERGY]
                Text(
                    if (count == 0) "" else (if (e.isLowerBound) "≥ " else "") + "${NutrientFormat.kcal(e.value ?: 0.0)} ккал",
                    style = AppTheme.type.number, color = c.textPrimary,
                )
                IconButton(onClick = { onAdd(meal) }) { Icon(Icons.Filled.Add, contentDescription = "Добавить в «${meal.titleRu}»", tint = c.accent) }
            }
        }
    }
}

@Composable
private fun WaterCard(ml: Int, goal: Int, onAdd: (Int) -> Unit, onCustom: () -> Unit) {
    val c = AppTheme.colors
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.WaterDrop, null, tint = c.water)
            Spacer(Modifier.width(10.dp))
            Text("Вода", style = AppTheme.type.section, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text("$ml" + (if (goal > 0) " / $goal" else "") + " мл", style = AppTheme.type.number, color = c.textPrimary)
        }
        Spacer(Modifier.height(10.dp))
        ProgressBar(if (goal > 0) ml.toFloat() / goal else null, c.water)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(100, 250, 500).forEach { v -> ChoiceChip("+$v", false, onClick = { onAdd(v) }) }
            ChoiceChip("Своё", false, onClick = onCustom)
        }
    }
}

@Composable
private fun WaterDialog(onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    val c = AppTheme.colors
    var text by remember { mutableStateOf("") }
    val v = parseDecimal(text)?.toInt()
    val valid = v != null && v in 1..5000
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Сколько воды?", style = AppTheme.type.section, color = c.textPrimary) },
        text = { AppTextField(text, { text = it }, "Объём", suffix = "мл", numeric = true, error = if (text.isNotEmpty() && !valid) "От 1 до 5000 мл" else null) },
        confirmButton = { TextButton(onClick = { if (valid) onAdd(v!!) }, enabled = valid) { Text("Добавить", color = if (valid) c.accent else c.textTertiary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.textSecondary) } },
    )
}

@Composable
private fun WeightCard(s: HomeState, onOpen: () -> Unit) {
    val c = AppTheme.colors
    val w = s.weight
    AppCard(onClick = onOpen) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.MonitorWeight, null, tint = c.textSecondary)
            Spacer(Modifier.width(10.dp))
            Text("Вес", style = AppTheme.type.section, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(w?.latest?.let { "${NutrientFormat.kg(it.weightKg)} кг" } ?: "нет записей", style = AppTheme.type.number, color = c.textPrimary)
        }
        if (w?.latest != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                listOfNotNull(
                    "запись: ${Dates.title(w.latest!!.date, s.today).lowercase()}",
                    w.average7?.let { "среднее за 7 дней ${NutrientFormat.kg(it)} кг" },
                    w.change30Days?.let { "за 30 дней ${NutrientFormat.signedKg(it)} кг" },
                ).joinToString(" · "),
                style = AppTheme.type.caption, color = c.textSecondary,
            )
            w.remainingToTargetKg?.let { r ->
                Spacer(Modifier.height(4.dp))
                Text(
                    if (r > 0) "До цели ${NutrientFormat.kg(r)} кг" else "Цель достигнута",
                    style = AppTheme.type.caption, color = if (r > 0) c.textSecondary else c.success,
                )
            }
        } else {
            Text("Записывайте вес, чтобы видеть тенденцию.", style = AppTheme.type.caption, color = c.textSecondary)
        }
    }
}

@Composable
private fun GoalStatusCard(s: HomeState) {
    val c = AppTheme.colors
    val t = s.targets ?: return
    val totals = s.summary.totals
    val kcal = totals[Nutrient.ENERGY].value ?: 0.0
    val protein = totals[Nutrient.PROTEIN]
    val fiber = totals[Nutrient.FIBER]
    AppCard {
        Text("Статус целей", style = AppTheme.type.section, color = c.textPrimary)
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusLine("Калории", when {
                kcal == 0.0 -> "ещё ничего не записано" to c.textTertiary
                kcal <= t.calorie.goalKcal * 1.03 -> "в пределах цели" to c.success
                else -> "выше цели на ${NutrientFormat.kcal(kcal - t.calorie.goalKcal)} ккал" to c.warning
            })
            StatusLine("Белки", percentStatus(protein.value, t.macros.proteinG, protein.isLowerBound, c))
            StatusLine("Клетчатка", percentStatus(fiber.value, t.norms[Nutrient.FIBER]?.target, fiber.isLowerBound, c))
            StatusLine("Вода", percentStatus(s.waterMl.toDouble(), t.waterGoalMl.toDouble().takeIf { it > 0 }, false, c))
        }
    }
}

private fun percentStatus(value: Double?, goal: Double?, lowerBound: Boolean, c: com.nutritiontracker.ui.theme.AppColors): Pair<String, androidx.compose.ui.graphics.Color> {
    if (value == null) return "нет данных" to c.textTertiary
    if (goal == null || goal <= 0) return "цель не задана" to c.textTertiary
    val p = value / goal * 100
    val text = (if (lowerBound) "не менее " else "") + NutrientFormat.percent(p)
    return text to if (p >= 100) c.success else if (lowerBound) c.textSecondary else c.textPrimary
}

@Composable
private fun StatusLine(label: String, status: Pair<String, androidx.compose.ui.graphics.Color>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = AppTheme.type.body, color = AppTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        StatusPill(status.first, status.second)
    }
}
