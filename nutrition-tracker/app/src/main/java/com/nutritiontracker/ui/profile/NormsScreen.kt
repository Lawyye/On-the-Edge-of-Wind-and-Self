package com.nutritiontracker.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.norms.DriNorms
import com.nutritiontracker.core.norms.NutrientNorm
import com.nutritiontracker.data.UserGoal
import com.nutritiontracker.domain.Targets
import com.nutritiontracker.ui.analysis.ANALYSIS_GROUPS
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.StatusPill
import com.nutritiontracker.ui.components.formatInput
import com.nutritiontracker.ui.components.parseDecimal
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class NormsState(val norms: Map<Nutrient, NutrientNorm> = emptyMap(), val userGoals: Map<Nutrient, UserGoal> = emptyMap(), val describe: String = "")

class NormsViewModel(private val c: AppContainer) : ViewModel() {
    val state: StateFlow<NormsState> = combine(c.profile.observe(), c.body.observeWeights(), c.profile.observeUserGoals()) { p, w, g ->
        val t = p?.let { runCatching { Targets.compute(it, w, g, LocalDate.now()) }.getOrNull() }
        NormsState(
            norms = t?.norms ?: emptyMap(),
            userGoals = g.associateBy { it.nutrient },
            describe = p?.let { "${it.sex.titleRu}, ${t?.ageYears ?: "—"} лет" } ?: "",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NormsState())

    fun setGoal(n: Nutrient, target: Double?, maximum: Double?) {
        viewModelScope.launch { c.profile.setUserGoal(n, target, maximum) }
    }
}

@Composable
fun NormsScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> NormsViewModel(c) }
    val s by vm.state.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var editing by remember { mutableStateOf<Nutrient?>(null) }
    Scaffold(containerColor = c.background, topBar = { AppTopBar("Нормы", onBack = onBack) }) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            item {
                InfoBanner("${DriNorms.SYSTEM_TITLE}. ${s.describe}. ${DriNorms.SYSTEM_DESCRIPTION} Нажмите на показатель, чтобы задать свою цель — например, по рекомендации врача.")
            }
            ANALYSIS_GROUPS.forEach { (title, list) ->
                item(key = title) {
                    AppCard {
                        Text(title, style = AppTheme.type.section, color = c.textPrimary)
                        list.forEachIndexed { i, n ->
                            if (i > 0) HorizontalDivider(color = c.outline.copy(alpha = 0.4f))
                            NormRow(n, s.norms[n], s.userGoals[n] != null, onClick = { editing = n })
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
    }
    editing?.let { n ->
        GoalDialog(n, s.norms[n], s.userGoals[n], onDismiss = { editing = null }, onSave = { t, m -> vm.setGoal(n, t, m); editing = null })
    }
}

@Composable
private fun NormRow(n: Nutrient, norm: NutrientNorm?, custom: Boolean, onClick: () -> Unit) {
    val c = AppTheme.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(n.titleRu, style = AppTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
            if (custom) StatusPill("своя", c.accent)
        }
        val parts = buildList {
            norm?.target?.let { add("${norm.kind?.shortRu ?: "цель"}: ${NutrientFormat.withUnit(it, n)}") }
            norm?.maximum?.let { add("${norm.maximumKind?.shortRu ?: "предел"}: ${NutrientFormat.withUnit(it, n)}") }
        }
        Text(
            if (parts.isEmpty()) (norm?.noteRu ?: "Нормы нет") else parts.joinToString(" · "),
            style = AppTheme.type.caption, color = c.textSecondary,
        )
        norm?.let { Text(it.sourceRu, style = AppTheme.type.label, color = c.textTertiary) }
    }
}

@Composable
private fun GoalDialog(n: Nutrient, norm: NutrientNorm?, current: UserGoal?, onDismiss: () -> Unit, onSave: (Double?, Double?) -> Unit) {
    val c = AppTheme.colors
    var target by remember { mutableStateOf(formatInput(current?.target)) }
    var maximum by remember { mutableStateOf(formatInput(current?.maximum)) }
    val tErr = target.takeIf { it.isNotBlank() }?.let { parseDecimal(it)?.let { v -> if (v < 0) "≥ 0" else null } ?: "Не число" }
    val mErr = maximum.takeIf { it.isNotBlank() }?.let { parseDecimal(it)?.let { v -> if (v < 0) "≥ 0" else null } ?: "Не число" }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(n.titleRu, style = AppTheme.type.section, color = c.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Справочно: " + listOfNotNull(
                        norm?.target?.let { "норма ${NutrientFormat.withUnit(it, n)}" },
                        norm?.maximum?.let { "предел ${NutrientFormat.withUnit(it, n)}" },
                    ).ifEmpty { listOf("нормы нет") }.joinToString(", ") + ". Пустое поле — использовать справочное значение.",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
                AppTextField(target, { target = it }, "Своя цель", suffix = n.unit.symbol, numeric = true, error = tErr)
                AppTextField(maximum, { maximum = it }, "Свой верхний предел", suffix = n.unit.symbol, numeric = true, error = mErr)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(parseDecimal(target), parseDecimal(maximum)) }, enabled = tErr == null && mErr == null) { Text("Сохранить", color = c.accent) }
        },
        dismissButton = {
            Row {
                if (current != null) TextButton(onClick = { onSave(null, null) }) { Text("Сбросить", color = c.error) }
                TextButton(onClick = onDismiss) { Text("Отмена", color = c.textSecondary) }
            }
        },
    )
}
