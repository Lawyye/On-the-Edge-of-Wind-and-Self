package com.nutritiontracker.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.data.DiaryEntry
import com.nutritiontracker.data.MealTemplate
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.DangerTextButton
import com.nutritiontracker.ui.components.DateSwitcher
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.sourceShort
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.launch
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryScreen(
    onAddFood: (LocalDate, MealType) -> Unit,
    onVoice: (LocalDate, MealType) -> Unit,
    onOpenAnalysis: (LocalDate) -> Unit,
    onOpenFood: (Long) -> Unit,
) {
    val vm = appViewModel { c, _ -> DiaryViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<DiaryEntry?>(null) }
    var pickDate by remember { mutableStateOf(false) }
    var templateFor by remember { mutableStateOf<MealType?>(null) }
    var applyTemplateFor by remember { mutableStateOf<MealType?>(null) }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DiaryEvent.Deleted -> scope.launch {
                    val r = snackbar.showSnackbar("«${e.entity.foodName}» удалено", actionLabel = "Отменить", duration = SnackbarDuration.Long)
                    if (r == SnackbarResult.ActionPerformed) vm.restore(e.entity)
                }
                is DiaryEvent.Message -> scope.launch { snackbar.showSnackbar(e.text) }
            }
        }
    }

    Scaffold(
        containerColor = c.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding(),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                DateSwitcher(
                    title = Dates.title(s.date),
                    subtitle = Dates.subtitle(s.date),
                    onPrev = { vm.shiftDate(-1) },
                    onNext = { vm.shiftDate(1) },
                    onTitleClick = { pickDate = true },
                    nextEnabled = s.date.isBefore(LocalDate.now().plusDays(7)),
                )
            }
            item { DayTotalsCard(s, onAnalysis = { onOpenAnalysis(s.date) }) }
            MealType.entries.forEach { meal ->
                val entries = s.summary.entries.filter { it.meal == meal }
                item(key = "meal-${meal.key}") {
                    MealSection(
                        meal = meal,
                        entries = entries,
                        kcal = s.summary.byMeal.getValue(meal)[Nutrient.ENERGY],
                        onAdd = { onAddFood(s.date, meal) },
                        onVoice = { onVoice(s.date, meal) },
                        onEntry = { editing = it },
                        onCopyYesterday = { vm.copyFromPreviousDay(meal) },
                        onSaveTemplate = { templateFor = meal },
                        onApplyTemplate = { applyTemplateFor = meal },
                        hasTemplates = s.templates.isNotEmpty(),
                    )
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    editing?.let { entry ->
        EntryEditSheet(
            entry = entry,
            onDismiss = { editing = null },
            onSave = { amount, meal, date -> vm.update(entry, amount, meal, date); editing = null },
            onDelete = { vm.delete(entry); editing = null },
            onRepeatToday = { vm.repeat(entry, LocalDate.now(), entry.meal); editing = null },
            onOpenFood = entry.foodId?.let { id -> { editing = null; onOpenFood(id) } },
        )
    }

    if (pickDate) {
        DiaryDatePicker(s.date, onDismiss = { pickDate = false }, onPick = { vm.setDate(it); pickDate = false })
    }

    templateFor?.let { meal ->
        NameDialog(
            title = "Сохранить «${meal.titleRu}» как шаблон",
            initial = "${meal.titleRu} ${Dates.dayMonth(s.date)}",
            onDismiss = { templateFor = null },
            onSave = { vm.saveTemplate(it, meal); templateFor = null },
        )
    }

    applyTemplateFor?.let { meal ->
        TemplatePickerDialog(
            templates = s.templates,
            onDismiss = { applyTemplateFor = null },
            onPick = { vm.applyTemplate(it, meal); applyTemplateFor = null },
            onDelete = { vm.deleteTemplate(it) },
        )
    }
}

@Composable
private fun DayTotalsCard(s: DiaryState, onAnalysis: () -> Unit) {
    val c = AppTheme.colors
    val t = s.summary.totals
    AppCard(onClick = onAnalysis) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                val e = t[Nutrient.ENERGY]
                Text(
                    (if (e.isLowerBound) "≥ " else "") + "${NutrientFormat.kcal(e.value ?: 0.0)} ккал",
                    style = AppTheme.type.numberLarge, color = c.textPrimary,
                )
                s.targets?.let { Text("цель ${NutrientFormat.kcal(it.calorie.goalKcal)} ккал", style = AppTheme.type.caption, color = c.textSecondary) }
            }
            Icon(Icons.Outlined.Analytics, contentDescription = "Полный анализ", tint = c.accent)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MacroMini("Б", t[Nutrient.PROTEIN].value, t[Nutrient.PROTEIN].isLowerBound, c.protein)
            MacroMini("Ж", t[Nutrient.FAT].value, t[Nutrient.FAT].isLowerBound, c.fat)
            MacroMini("У", t[Nutrient.CARBS].value, t[Nutrient.CARBS].isLowerBound, c.carbs)
            MacroMini("Кл", t[Nutrient.FIBER].value, t[Nutrient.FIBER].isLowerBound, c.fiber)
        }
        Spacer(Modifier.height(6.dp))
        Text("Нажмите для полного анализа витаминов и минералов", style = AppTheme.type.caption, color = c.textTertiary)
    }
}

@Composable
private fun MacroMini(label: String, value: Double?, lowerBound: Boolean, color: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        com.nutritiontracker.ui.components.Dot(color, 7.dp)
        Spacer(Modifier.width(5.dp))
        Text(
            "$label ${if (lowerBound) "≥" else ""}${value?.let { NutrientFormat.amount(it) } ?: "—"}",
            style = AppTheme.type.number, color = AppTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun MealSection(
    meal: MealType,
    entries: List<DiaryEntry>,
    kcal: com.nutritiontracker.core.calc.NutrientTotal,
    onAdd: () -> Unit,
    onVoice: () -> Unit,
    onEntry: (DiaryEntry) -> Unit,
    onCopyYesterday: () -> Unit,
    onSaveTemplate: () -> Unit,
    onApplyTemplate: () -> Unit,
    hasTemplates: Boolean,
) {
    val c = AppTheme.colors
    var menu by remember { mutableStateOf(false) }
    AppCard(padding = PaddingValues(start = Dimens.cardPadding, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(meal.titleRu, style = AppTheme.type.section, color = c.textPrimary)
                if (entries.isNotEmpty()) {
                    Text(
                        (if (kcal.isLowerBound) "≥ " else "") + "${NutrientFormat.kcal(kcal.value ?: 0.0)} ккал",
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                }
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = c.accent) }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Ещё", tint = c.textSecondary) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Добавить голосом") }, onClick = { menu = false; onVoice() })
                    DropdownMenuItem(text = { Text("Скопировать из вчера") }, onClick = { menu = false; onCopyYesterday() })
                    if (entries.isNotEmpty()) DropdownMenuItem(text = { Text("Сохранить как шаблон") }, onClick = { menu = false; onSaveTemplate() })
                    if (hasTemplates) DropdownMenuItem(text = { Text("Добавить из шаблона") }, onClick = { menu = false; onApplyTemplate() })
                }
            }
        }
        if (entries.isEmpty()) {
            Text("Нет записей", style = AppTheme.type.caption, color = c.textTertiary, modifier = Modifier.padding(bottom = 6.dp))
        }
        entries.forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.5f), modifier = Modifier.padding(end = 12.dp))
            EntryRow(e, onClick = { onEntry(e) })
        }
    }
}

@Composable
private fun EntryRow(e: DiaryEntry, onClick: () -> Unit) {
    val c = AppTheme.colors
    val kcal = e.eaten[Nutrient.ENERGY]
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp).padding(end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(e.foodName, style = AppTheme.type.body, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    "${NutrientFormat.amount(e.amount)} ${e.unit.symbol}",
                    e.servingLabel,
                    e.brand,
                    sourceShort(e.source),
                ).joinToString(" · "),
                style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            kcal?.let { (if (Nutrient.ENERGY in e.eaten.incomplete) "≥ " else "") + "${NutrientFormat.kcal(it)} ккал" } ?: "нет данных",
            style = AppTheme.type.number,
            color = if (kcal == null) c.textTertiary else c.textPrimary,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditSheet(
    entry: DiaryEntry,
    onDismiss: () -> Unit,
    onSave: (Double, MealType, LocalDate) -> Unit,
    onDelete: () -> Unit,
    onRepeatToday: () -> Unit,
    onOpenFood: (() -> Unit)?,
) {
    val c = AppTheme.colors
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var amountText by remember { mutableStateOf(formatInput(entry.amount)) }
    var meal by remember { mutableStateOf(entry.meal) }
    var date by remember { mutableStateOf(entry.date) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val amount = parseDecimal(amountText)
    val valid = amount != null && amount > 0 && amount <= 10_000
    val preview = amount?.takeIf { valid }?.let { NutrientMath.forAmount(entry.per100, it) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = c.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding).navigationBarsPadding().padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(entry.foodName, style = AppTheme.type.section, color = c.textPrimary)
            Text(
                "Значения из снимка на момент добавления (${sourceShort(entry.source)}). Изменения продукта в базе на эту запись не влияют.",
                style = AppTheme.type.caption, color = c.textSecondary,
            )
            AppTextField(
                amountText, { amountText = it }, "Количество", suffix = entry.unit.symbol, numeric = true,
                error = if (amountText.isNotEmpty() && !valid) "Введите число больше нуля (до 10 000)" else null,
            )
            preview?.let { p ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(NutrientFormat.withUnit(p[Nutrient.ENERGY], Nutrient.ENERGY), style = AppTheme.type.number, color = c.textPrimary)
                    Text("Б ${p[Nutrient.PROTEIN]?.let { NutrientFormat.amount(it) } ?: "—"}", style = AppTheme.type.caption, color = c.textSecondary)
                    Text("Ж ${p[Nutrient.FAT]?.let { NutrientFormat.amount(it) } ?: "—"}", style = AppTheme.type.caption, color = c.textSecondary)
                    Text("У ${p[Nutrient.CARBS]?.let { NutrientFormat.amount(it) } ?: "—"}", style = AppTheme.type.caption, color = c.textSecondary)
                }
            }
            SegmentedControl(MealType.entries, meal, { it.titleRu.take(8) }, { meal = it })
            SecondaryButton("Дата: ${Dates.title(date)}", onClick = { pickDate = true }, icon = Icons.Outlined.CalendarMonth, modifier = Modifier.fillMaxWidth())
            PrimaryButton("Сохранить", onClick = { onSave(amount!!, meal, date) }, enabled = valid, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Повторить сегодня", onClick = onRepeatToday, modifier = Modifier.weight(1f))
                if (onOpenFood != null) SecondaryButton("Продукт", onClick = onOpenFood, modifier = Modifier.weight(1f))
            }
            DangerTextButton("Удалить запись", onClick = { confirmDelete = true })
        }
    }
    if (pickDate) DiaryDatePicker(date, onDismiss = { pickDate = false }, onPick = { date = it; pickDate = false })
    if (confirmDelete) {
        com.nutritiontracker.ui.components.ConfirmDialog(
            title = "Удалить запись?",
            text = "«${entry.foodName}» будет удалено из дневника. Удаление можно отменить сразу после него.",
            confirm = "Удалить",
            destructive = true,
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryDatePicker(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val c = AppTheme.colors
    val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toMillis(initial))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(Dates.fromMillis(it)) } ?: onDismiss() }) { Text("Выбрать", color = c.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.textSecondary) } },
    ) { DatePicker(state = state) }
}

@Composable
private fun NameDialog(title: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = AppTheme.colors
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = AppTheme.type.section, color = c.textPrimary) },
        text = { AppTextField(name, { name = it }, "Название") },
        confirmButton = { TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Сохранить", color = c.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.textSecondary) } },
    )
}

@Composable
private fun TemplatePickerDialog(templates: List<MealTemplate>, onDismiss: () -> Unit, onPick: (MealTemplate) -> Unit, onDelete: (MealTemplate) -> Unit) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text("Шаблоны", style = AppTheme.type.section, color = c.textPrimary) },
        text = {
            if (templates.isEmpty()) {
                EmptyState(Icons.Filled.Add, "Шаблонов нет", "Сохраните приём пищи как шаблон через меню «Ещё».")
            } else {
                LazyColumn {
                    items(templates, key = { it.id }) { t ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(t) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(t.name, style = AppTheme.type.body, color = c.textPrimary)
                                val kcal = NutrientMath.total(t.items.map { NutrientMath.forAmount(it.per100, it.amount) })[Nutrient.ENERGY]
                                Text(
                                    "${com.nutritiontracker.ui.plural(t.items.size, "продукт", "продукта", "продуктов")} · ${NutrientFormat.total(kcal)}",
                                    style = AppTheme.type.caption, color = c.textSecondary,
                                )
                            }
                            TextButton(onClick = { onDelete(t) }) { Text("Удалить", color = c.error, style = AppTheme.type.label) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть", color = c.textSecondary) } },
    )
}
