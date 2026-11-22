package com.nutritiontracker.ui.voice

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.voice.CountKind
import com.nutritiontracker.core.voice.ParsedQuantity
import com.nutritiontracker.data.displayName
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.ChoiceChip
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.components.SegmentedControl
import com.nutritiontracker.ui.components.SkeletonBox
import com.nutritiontracker.ui.diary.DiaryDatePicker
import com.nutritiontracker.ui.navigation.VoiceRoute
import com.nutritiontracker.ui.sourceShort
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens

@Composable
fun VoiceScreen(route: VoiceRoute, onBack: () -> Unit, onSaved: () -> Unit) {
    val vm = appViewModel { c, _ -> VoiceViewModel(c, route) }
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val c = AppTheme.colors
    val controller = remember { SpeechController(context.applicationContext, vm::onRecognized) }
    val speech by controller.state.collectAsStateWithLifecycle()
    var pickDate by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { controller.stop() } }
    LaunchedEffect(Unit) { vm.saved.collect { onSaved() } }
    var saveError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.errors.collect { saveError = it } }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) controller.start()
    }
    val systemDialog = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(vm::onRecognized)
        }
    }
    var systemDialogMissing by remember { mutableStateOf(false) }

    fun startListening() {
        controller.clearError()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            controller.start()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Scaffold(
        containerColor = c.background,
        topBar = { AppTopBar("Голосовой ввод", onBack = onBack) },
        bottomBar = {
            if (s.items.isNotEmpty()) {
                val total = s.items.filter { it.included }.mapNotNull { it.kcal }.sum()
                Column(Modifier.background(c.background).navigationBarsPadding().imePadding().padding(Dimens.screenPadding)) {
                    PrimaryButton(
                        if (s.saving) "Сохранение…" else "Сохранить ${s.items.count { it.included }} · ${NutrientFormat.kcal(total)} ккал",
                        onClick = vm::save,
                        enabled = s.canSave,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (!s.items.all { it.ready }) {
                        Text("Укажите продукт и количество для всех отмеченных позиций", style = AppTheme.type.caption, color = c.warning, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    MicButton(listening = speech.listening, level = speech.level, onClick = { if (speech.listening) controller.stop() else startListening() })
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            speech.listening -> speech.partial.ifEmpty { "Слушаю…" }
                            else -> "Например: «200 граммов куриного филе, три картофелины по 100 граммов и одно яблоко»"
                        },
                        style = AppTheme.type.caption, color = c.textSecondary,
                    )
                    TextButton(onClick = {
                        try {
                            systemDialog.launch(SpeechController.recognizerIntent())
                        } catch (e: ActivityNotFoundException) {
                            systemDialogMissing = true
                        }
                    }) { Text("Системный диалог распознавания", color = c.accent, style = AppTheme.type.caption) }
                    Text(
                        "Речь распознаёт система Android. Если на телефоне нет офлайн-пакета русского языка, звук обрабатывается сервисом распознавания (обычно Google) в интернете. Приложение получает только текст и аудио не сохраняет.",
                        style = AppTheme.type.label, color = c.textTertiary,
                    )
                }
            }
            speech.error?.let { item { InfoBanner(it, kind = BannerKind.WARNING) } }
            saveError?.let { item { InfoBanner(it, kind = BannerKind.ERROR) } }
            if (systemDialogMissing) item { InfoBanner("На устройстве нет системного распознавания речи. Введите текст вручную.", kind = BannerKind.WARNING) }
            item {
                Column {
                    AppTextField(s.text, vm::setText, "Текст (можно исправить)", singleLine = false)
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Разобрать текст", onClick = vm::parse, enabled = s.text.isNotBlank(), modifier = Modifier.fillMaxWidth())
                }
            }
            if (s.parsedOnce) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SegmentedControl(MealType.entries, s.meal, { it.titleRu }, vm::setMeal)
                        SecondaryButton("Дата: ${Dates.title(s.date)}", onClick = { pickDate = true }, modifier = Modifier.fillMaxWidth())
                    }
                }
                if (s.items.isEmpty()) {
                    item { InfoBanner("Не удалось выделить продукты. Перечислите их через запятую или «и», например: «яблоко 150 г, кефир 200 мл».", kind = BannerKind.WARNING) }
                }
            }
            items(s.items, key = { it.id }) { item ->
                VoiceItemCard(
                    item = item,
                    onToggle = { vm.toggleIncluded(item.id) },
                    onAmount = { vm.setAmount(item.id, it) },
                    onSelectLocal = { vm.select(item.id, it) },
                    onSelectRemote = { vm.selectRemote(item.id, it) },
                    onSearch = { vm.findFood(item.id, it) },
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    if (pickDate) DiaryDatePicker(s.date, onDismiss = { pickDate = false }, onPick = { vm.setDate(it); pickDate = false })
}

@Composable
private fun MicButton(listening: Boolean, level: Float, onClick: () -> Unit) {
    val c = AppTheme.colors
    val scale by animateFloatAsState(if (listening) 1f + level * 0.25f else 1f, label = "mic")
    Box(
        Modifier
            .size(96.dp)
            .scale(scale)
            .background(if (listening) c.accent else c.accentMuted, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (listening) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (listening) "Остановить" else "Говорить",
            tint = if (listening) c.onAccent else c.accent,
            modifier = Modifier.size(40.dp),
        )
    }
}

private fun quantityText(q: ParsedQuantity?): String = when (q) {
    null -> "количество не прозвучало"
    is ParsedQuantity.Mass -> "${NutrientFormat.amount(q.grams)} г"
    is ParsedQuantity.Volume -> "${NutrientFormat.amount(q.milliliters)} мл"
    is ParsedQuantity.Count -> {
        val n = NutrientFormat.number(q.count, 2)
        val unit = q.unitWord ?: when (q.kind) {
            CountKind.PIECE -> "шт"
            CountKind.PORTION -> "порц."
            CountKind.PACKAGE -> "уп."
            CountKind.HOUSEHOLD -> "мера"
        }
        val each = q.eachGrams?.let { " по ${NutrientFormat.amount(it)} г" } ?: q.eachMl?.let { " по ${NutrientFormat.amount(it)} мл" } ?: ""
        "$n $unit$each"
    }
}

@Composable
private fun VoiceItemCard(
    item: VoiceItem,
    onToggle: () -> Unit,
    onAmount: (String) -> Unit,
    onSelectLocal: (com.nutritiontracker.data.FoodItem) -> Unit,
    onSelectRemote: (com.nutritiontracker.remote.RemoteFood) -> Unit,
    onSearch: (String) -> Unit,
) {
    val c = AppTheme.colors
    var searchText by remember(item.id) { mutableStateOf(item.searchQuery) }
    var showSearch by remember(item.id) { mutableStateOf(false) }
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = item.included, onCheckedChange = { onToggle() }, colors = CheckboxDefaults.colors(checkedColor = c.accent))
            Column(Modifier.weight(1f)) {
                Text("«${item.parsed.sourceText}»", style = AppTheme.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(quantityText(item.parsed.quantity), style = AppTheme.type.label, color = c.textTertiary)
            }
            Icon(
                if (item.ready && item.included) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (item.ready && item.included) c.success else c.textTertiary,
            )
        }
        if (!item.included) return@AppCard
        Spacer(Modifier.height(8.dp))
        val sel = item.selected
        if (sel != null) {
            Text(sel.displayName, style = AppTheme.type.bodyStrong, color = c.textPrimary)
            Text(listOfNotNull(sel.data.brand, sourceShort(sel.source)).joinToString(" · "), style = AppTheme.type.caption, color = c.textSecondary)
            Spacer(Modifier.height(8.dp))
            AppTextField(
                item.amountText, onAmount, "Количество", suffix = sel.basis.symbol, numeric = true,
                error = if (item.amount == null) (item.needs ?: "Укажите количество") else null,
                supporting = item.explanation?.let { "Расчёт: $it" },
            )
            val kcal = item.kcal
            if (kcal != null) {
                Text("${NutrientFormat.kcal(kcal)} ккал", style = AppTheme.type.number, color = c.textPrimary, modifier = Modifier.padding(top = 4.dp))
            } else if (item.amount != null) {
                Text("Калорийность: нет данных", style = AppTheme.type.caption, color = c.textTertiary)
            }
        } else if (item.searching) {
            SkeletonBox(Modifier.fillMaxWidth(0.7f), height = 18.dp)
        } else {
            InfoBanner("Продукт «${item.parsed.name}» не найден. Уточните название ниже.", kind = BannerKind.WARNING)
        }
        item.searchError?.let { Text(it, style = AppTheme.type.caption, color = c.warning, modifier = Modifier.padding(top = 6.dp)) }

        val others = item.localCandidates.filter { it.id != sel?.id }
        if (others.isNotEmpty() || item.remoteCandidates.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Другие варианты", style = AppTheme.type.label, color = c.textSecondary)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                others.forEach { f -> ChoiceChip(f.displayName.take(40), false, onClick = { onSelectLocal(f) }) }
                item.remoteCandidates.filter { r -> sel == null || r.food.externalId != sel.data.externalId }.forEach { r ->
                    ChoiceChip("${r.food.name.take(36)} · ${sourceShort(r.food.source)}", false, onClick = { onSelectRemote(r) })
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (showSearch) {
            AppTextField(searchText, { searchText = it }, "Название для поиска", onDone = { onSearch(searchText); showSearch = false })
            TextButton(onClick = { onSearch(searchText); showSearch = false }) { Text("Искать", color = c.accent) }
        } else {
            TextButton(onClick = { showSearch = true }) { Text("Найти другой продукт", color = c.accent, style = AppTheme.type.caption) }
        }
    }
}
