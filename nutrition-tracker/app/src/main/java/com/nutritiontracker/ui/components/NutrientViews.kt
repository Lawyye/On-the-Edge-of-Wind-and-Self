package com.nutritiontracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nutritiontracker.core.calc.NutrientTotal
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.norms.AssessmentStatus
import com.nutritiontracker.core.norms.NutrientAssessment
import com.nutritiontracker.ui.theme.AppColors
import com.nutritiontracker.ui.theme.AppTheme

fun statusColor(status: AssessmentStatus, c: AppColors): Color = when (status) {
    AssessmentStatus.MET -> c.success
    AssessmentStatus.BELOW -> c.warning
    AssessmentStatus.BELOW_INCOMPLETE -> c.textSecondary
    AssessmentStatus.ABOVE_MAXIMUM -> c.error
    AssessmentStatus.WITHIN_LIMIT -> c.success
    AssessmentStatus.NO_DATA, AssessmentStatus.NO_NORM -> c.textTertiary
}

/** Значение нутриента в продукте: «нет данных» показывается явно, а не как ноль. */
@Composable
fun NutrientValueRow(nutrient: Nutrient, nutrients: Nutrients, modifier: Modifier = Modifier, indent: Boolean = false) {
    val c = AppTheme.colors
    val v = nutrients[nutrient]
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp).padding(start = if (indent) 14.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(nutrient.titleRu, style = AppTheme.type.body, color = if (indent) c.textSecondary else c.textPrimary, modifier = Modifier.weight(1f))
        val text = when {
            v == null -> NutrientFormat.NO_DATA
            nutrient in nutrients.incomplete -> "≥ " + NutrientFormat.withUnit(v, nutrient)
            else -> NutrientFormat.withUnit(v, nutrient)
        }
        Text(text, style = AppTheme.type.number, color = if (v == null) c.textTertiary else c.textPrimary)
    }
}

/** Строка анализа: количество, норма, процент, недостаток/превышение и источник нормы. */
@Composable
fun AssessmentRow(a: NutrientAssessment, modifier: Modifier = Modifier, periodNote: String? = null) {
    val c = AppTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val color = statusColor(a.status, c)
    Column(
        modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.nutrient.titleRu, style = AppTheme.type.body, color = c.textPrimary, modifier = Modifier.weight(1f))
            Text(NutrientFormat.total(a.total), style = AppTheme.type.number, color = if (a.total.isUnknown) c.textTertiary else c.textPrimary)
            val target = a.norm?.target
            if (target != null) {
                Text(" / ${NutrientFormat.withUnit(target, a.nutrient)}", style = AppTheme.type.caption, color = c.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))
        val fraction = a.percentOfTarget?.let { (it / 100.0).toFloat() }
            ?: a.norm?.maximum?.takeIf { it > 0 }?.let { m -> a.total.value?.let { (it / m).toFloat() } }
        ProgressBar(fraction = if (a.total.isUnknown) null else fraction, color = color, overColor = color, dashed = a.total.isLowerBound)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(statusLabel(a), color)
            a.percentOfTarget?.let {
                Text((if (a.total.isLowerBound) "не менее " else "") + NutrientFormat.percent(it), style = AppTheme.type.label, color = c.textSecondary)
            }
        }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            DetailText(buildDetails(a, periodNote))
        }
    }
}

private fun statusLabel(a: NutrientAssessment): String = when (a.status) {
    AssessmentStatus.BELOW -> "Не хватает ${NutrientFormat.withUnit(a.shortfall, a.nutrient)}"
    AssessmentStatus.ABOVE_MAXIMUM -> "Выше предела на ${NutrientFormat.withUnit(a.excess, a.nutrient)}"
    AssessmentStatus.BELOW_INCOMPLETE -> "Данные неполные"
    else -> a.status.titleRu
}

private fun buildDetails(a: NutrientAssessment, periodNote: String?): List<String> = buildList {
    val t = a.total
    if (t.isUnknown) add("Ни для одного продукта в источниках нет данных об этом веществе.")
    if (t.isLowerBound) {
        if (t.itemsWithoutData > 0) add("Данные есть для ${t.itemsWithData} из ${t.itemsTotal} записей. Фактическое потребление может быть выше.")
        else add("В части рецептов данные есть не по всем ингредиентам. Фактическое потребление может быть выше.")
    }
    a.norm?.let { n ->
        n.kind?.let { add("Норма: ${n.target?.let { v -> NutrientFormat.withUnit(v, a.nutrient) } ?: "—"} — ${it.titleRu}.") }
        if (n.maximum != null && n.maximumKind != null) add("Предел: ${NutrientFormat.withUnit(n.maximum, a.nutrient)} — ${n.maximumKind!!.titleRu}.")
        add("Источник: ${n.sourceRu}.")
        n.noteRu?.let { add(it) }
    } ?: add("Для этого показателя справочной нормы нет.")
    a.nutrient.noteRu?.let { add(it) }
    periodNote?.let { add(it) }
}

@Composable
fun DetailText(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEach { Text(it, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary) }
    }
}

/** Строка макронутриента с целью: «Белки 82 / 120 г». */
@Composable
fun MacroRow(label: String, total: NutrientTotal, goal: Double?, color: Color, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Dot(color)
            Spacer(Modifier.width(8.dp))
            Text(label, style = AppTheme.type.caption, color = c.textSecondary, modifier = Modifier.weight(1f))
            Text(
                (if (total.isLowerBound) "≥ " else "") + (total.value?.let { NutrientFormat.amount(it) } ?: "—"),
                style = AppTheme.type.number,
                color = c.textPrimary,
            )
            if (goal != null) Text(" / ${NutrientFormat.amount(goal)} г", style = AppTheme.type.caption, color = c.textSecondary)
            else Text(" г", style = AppTheme.type.caption, color = c.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        val f = if (goal != null && goal > 0) total.value?.let { (it / goal).toFloat() } else null
        ProgressBar(f, color, overColor = c.warning, dashed = total.isLowerBound)
    }
}
