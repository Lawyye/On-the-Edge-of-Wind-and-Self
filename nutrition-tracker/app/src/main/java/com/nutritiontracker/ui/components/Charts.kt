package com.nutritiontracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nutritiontracker.ui.theme.AppTheme

/**
 * Кольцо калорий: доля съеденного от цели. При превышении цели кольцо
 * полностью замкнуто и окрашено в цвет предупреждения.
 */
@Composable
fun CalorieRing(
    eaten: Double,
    goal: Double?,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    stroke: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    val c = AppTheme.colors
    val fraction = if (goal != null && goal > 0) (eaten / goal).toFloat() else 0f
    val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(800), label = "ring")
    val over = fraction > 1f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val sw = stroke.toPx()
            val arcSize = Size(this.size.width - sw, this.size.height - sw)
            val topLeft = Offset(sw / 2, sw / 2)
            drawArc(c.surfaceVariant, -90f, 360f, false, topLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
            if (animated > 0f) {
                drawArc(
                    if (over) c.warning else c.accent, -90f, 360f * animated, false, topLeft, arcSize,
                    style = Stroke(sw, cap = StrokeCap.Round),
                )
            }
        }
        content()
    }
}

data class BarPoint(val label: String, val value: Double?, val highlight: Boolean = false)

/**
 * Столбчатая диаграмма по дням. Дни без данных показаны пустыми, а не нулём.
 * [goal] — горизонтальная линия цели.
 */
@Composable
fun BarChart(
    points: List<BarPoint>,
    modifier: Modifier = Modifier,
    goal: Double? = null,
    color: Color = AppTheme.colors.accent,
    overColor: Color = AppTheme.colors.warning,
    height: Dp = 160.dp,
    description: String = "Диаграмма",
) {
    val c = AppTheme.colors
    val maxValue = (points.mapNotNull { it.value } + listOfNotNull(goal)).maxOrNull()?.takeIf { it > 0 } ?: 1.0
    Column(modifier.fillMaxWidth().semantics { contentDescription = description }) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val n = points.size.coerceAtLeast(1)
            val slot = size.width / n
            val barW = (slot * 0.62f).coerceAtMost(28.dp.toPx())
            val top = 8.dp.toPx()
            val usable = size.height - top
            points.forEachIndexed { i, p ->
                val x = slot * i + (slot - barW) / 2
                val v = p.value
                if (v == null) {
                    drawRoundRect(c.surfaceVariant.copy(alpha = 0.5f), Offset(x, size.height - 3.dp.toPx()), Size(barW, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                } else {
                    val h = (v / maxValue * usable).toFloat().coerceAtLeast(2.dp.toPx())
                    val col = if (goal != null && v > goal * 1.05) overColor else color
                    drawRoundRect(
                        if (p.highlight) col else col.copy(alpha = 0.75f),
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(6.dp.toPx()),
                    )
                }
            }
            if (goal != null) {
                val y = size.height - (goal / maxValue * usable).toFloat()
                drawLine(c.textSecondary, Offset(0f, y), Offset(size.width, y), 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
            }
        }
        if (points.size <= 14) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                points.forEach { p ->
                    Text(
                        p.label, style = AppTheme.type.label, color = if (p.highlight) c.textPrimary else c.textTertiary,
                        modifier = Modifier.weight(1f), textAlign = TextAlign.Center, maxLines = 1,
                    )
                }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(points.first().label, style = AppTheme.type.label, color = c.textTertiary)
                Text(points.last().label, style = AppTheme.type.label, color = c.textTertiary)
            }
        }
    }
}

data class LinePoint(val x: Long, val y: Double)

/**
 * Линейный график: точки — отдельные значения (например, взвешивания),
 * сплошная линия — сглаженная тенденция, пунктир — цель.
 */
@Composable
fun LineChart(
    raw: List<LinePoint>,
    trend: List<LinePoint>,
    modifier: Modifier = Modifier,
    target: Double? = null,
    height: Dp = 180.dp,
    description: String = "График",
) {
    val c = AppTheme.colors
    if (raw.isEmpty()) return
    val all = raw.map { it.y } + trend.map { it.y } + listOfNotNull(target)
    val minY = all.min()
    val maxY = all.max()
    val pad = ((maxY - minY) * 0.15).coerceAtLeast(0.5)
    val lo = minY - pad
    val hi = maxY + pad
    val minX = raw.minOf { it.x }
    val maxX = raw.maxOf { it.x }.coerceAtLeast(minX + 1)
    Canvas(modifier.fillMaxWidth().height(height).semantics { contentDescription = description }) {
        fun px(x: Long) = ((x - minX).toFloat() / (maxX - minX)) * (size.width - 16.dp.toPx()) + 8.dp.toPx()
        fun py(y: Double) = (size.height - ((y - lo) / (hi - lo) * size.height)).toFloat()
        // Сетка
        for (i in 1..3) {
            val y = size.height * i / 4
            drawLine(c.outline.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 1f)
        }
        if (target != null && target in lo..hi) {
            drawLine(c.success.copy(alpha = 0.8f), Offset(0f, py(target)), Offset(size.width, py(target)), 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f)))
        }
        raw.forEach { p -> drawCircle(c.textTertiary, 3.dp.toPx(), Offset(px(p.x), py(p.y))) }
        if (trend.size >= 2) {
            val path = Path()
            trend.forEachIndexed { i, p ->
                if (i == 0) path.moveTo(px(p.x), py(p.y)) else path.lineTo(px(p.x), py(p.y))
            }
            drawPath(path, c.accent, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))
        }
        trend.lastOrNull()?.let { drawCircle(c.accent, 5.dp.toPx(), Offset(px(it.x), py(it.y))) }
    }
}
