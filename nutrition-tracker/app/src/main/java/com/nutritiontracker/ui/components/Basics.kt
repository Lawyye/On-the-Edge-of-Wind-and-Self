package com.nutritiontracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(Dimens.cardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = AppTheme.colors
    val shape = RoundedCornerShape(Dimens.radiusCard)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(c.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = Dimens.gap, bottom = Dimens.gapSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = AppTheme.type.section, color = AppTheme.colors.textPrimary, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = AppTheme.type.label,
                color = AppTheme.colors.accent,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

enum class BannerKind { INFO, WARNING, ERROR, SUCCESS }

@Composable
fun InfoBanner(text: String, modifier: Modifier = Modifier, kind: BannerKind = BannerKind.INFO, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = AppTheme.colors
    val color = when (kind) {
        BannerKind.INFO -> c.textSecondary
        BannerKind.WARNING -> c.warning
        BannerKind.ERROR -> c.error
        BannerKind.SUCCESS -> c.success
    }
    val icon = when (kind) {
        BannerKind.INFO, BannerKind.SUCCESS -> Icons.Outlined.Info
        BannerKind.WARNING -> Icons.Outlined.WarningAmber
        BannerKind.ERROR -> Icons.Outlined.ErrorOutline
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(color.copy(alpha = 0.10f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(text, style = AppTheme.type.caption, color = if (kind == BannerKind.INFO) c.textSecondary else c.textPrimary)
            if (action != null && onAction != null) {
                Text(
                    action,
                    style = AppTheme.type.label,
                    color = c.accent,
                    modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onAction).padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val c = AppTheme.colors
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(c.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = c.textSecondary, modifier = Modifier.size(26.dp)) }
        Spacer(Modifier.height(14.dp))
        Text(title, style = AppTheme.type.section, color = c.textPrimary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, style = AppTheme.type.caption, color = c.textSecondary, textAlign = TextAlign.Center)
        if (action != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            SecondaryButton(action, onClick = onAction)
        }
    }
}

/** Заглушка с мерцанием на время загрузки. */
@Composable
fun SkeletonBox(modifier: Modifier = Modifier, height: Dp = 16.dp, shapeRadius: Dp = 8.dp) {
    val c = AppTheme.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "shift",
    )
    val brush = Brush.linearGradient(
        colors = listOf(c.surfaceVariant, c.surfaceHigh, c.surfaceVariant),
        start = androidx.compose.ui.geometry.Offset(shift * 1200f - 400f, 0f),
        end = androidx.compose.ui.geometry.Offset(shift * 1200f, 0f),
    )
    Box(modifier.height(height).clip(RoundedCornerShape(shapeRadius)).background(brush))
}

@Composable
fun SkeletonList(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(rows) {
            AppCard {
                SkeletonBox(Modifier.fillMaxWidth(0.6f), height = 16.dp)
                Spacer(Modifier.height(8.dp))
                SkeletonBox(Modifier.fillMaxWidth(0.35f), height = 12.dp)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = AppTheme.colors
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = RoundedCornerShape(Dimens.radiusControl),
        colors = ButtonDefaults.buttonColors(
            containerColor = c.accent,
            contentColor = c.onAccent,
            disabledContainerColor = c.surfaceVariant,
            disabledContentColor = c.textTertiary,
        ),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppTheme.type.bodyStrong)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val c = AppTheme.colors
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(Dimens.radiusControl),
        border = androidx.compose.foundation.BorderStroke(1.dp, c.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = c.textPrimary, disabledContentColor = c.textTertiary),
    ) {
        if (icon != null) {
            Icon(icon, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = AppTheme.type.bodyStrong)
    }
}

@Composable
fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    TextButton(onClick = onClick, modifier = modifier) {
        Text(text, color = AppTheme.colors.error, style = AppTheme.type.bodyStrong)
    }
}

/** Полоса прогресса; [fraction] может быть больше 1 — тогда полоса заполнена и окрашена в [overColor]. */
@Composable
fun ProgressBar(
    fraction: Float?,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    overColor: Color = color,
    dashed: Boolean = false,
) {
    val c = AppTheme.colors
    val target = (fraction ?: 0f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(target, animationSpec = tween(600), label = "progress")
    val barColor = if ((fraction ?: 0f) > 1f) overColor else color
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(height))
            .background(c.surfaceVariant),
    ) {
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(height)
                    .clip(RoundedCornerShape(height))
                    .background(if (dashed) barColor.copy(alpha = 0.45f) else barColor),
            )
        }
    }
}

@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .background(c.surface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            val bg by animateColorAsState(if (isSelected) c.surfaceHigh else Color.Transparent, label = "seg")
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .padding(horizontal = 6.dp, vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    style = AppTheme.type.label,
                    color = if (isSelected) c.textPrimary else c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
fun ChoiceChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = AppTheme.colors
    Box(
        modifier
            .clip(RoundedCornerShape(Dimens.radiusChip))
            .background(if (selected) c.accentMuted else c.surface)
            .border(1.dp, if (selected) c.accent else c.outline, RoundedCornerShape(Dimens.radiusChip))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(text, style = AppTheme.type.label, color = if (selected) c.textPrimary else c.textSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    val c = AppTheme.colors
    CenterAlignedTopAppBar(
        title = { Text(title, style = AppTheme.type.section, color = c.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = c.textPrimary)
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = c.background, scrolledContainerColor = c.background),
    )
}

/** Текстовое поле. Для чисел — [numeric] = true: допускается запятая как десятичный разделитель. */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    numeric: Boolean = false,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    imeAction: ImeAction = ImeAction.Next,
    onDone: (() -> Unit)? = null,
    placeholder: String? = null,
) {
    val c = AppTheme.colors
    OutlinedTextField(
        value = value,
        onValueChange = { new ->
            if (!numeric) onValueChange(new)
            else {
                val filtered = new.replace('.', ',').filter { it.isDigit() || it == ',' }
                if (filtered.count { it == ',' } <= 1 && filtered.length <= 9) onValueChange(filtered)
            }
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.textTertiary) } },
        suffix = suffix?.let { { Text(it, color = c.textSecondary) } },
        isError = error != null,
        supportingText = (error ?: supporting)?.let { { Text(it, color = if (error != null) c.error else c.textSecondary) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text,
            imeAction = imeAction,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        shape = RoundedCornerShape(Dimens.radiusControl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.outline,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.textSecondary,
            cursorColor = c.accent,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            errorContainerColor = c.surface,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Разбор числа, введённого пользователем: запятая или точка; пустая строка — null. */
fun parseDecimal(text: String): Double? {
    val t = text.trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()?.takeIf { it.isFinite() }
}

fun formatInput(v: Double?): String = when {
    v == null -> ""
    v == Math.floor(v) && kotlin.math.abs(v) < 1e9 -> v.toLong().toString()
    else -> com.nutritiontracker.core.format.NutrientFormat.number(v, 3)
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    val c = AppTheme.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = { Text(title, style = AppTheme.type.section, color = c.textPrimary) },
        text = { Text(text, style = AppTheme.type.body, color = c.textSecondary) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = if (destructive) c.error else c.accent, style = AppTheme.type.bodyStrong) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = c.textSecondary) } },
    )
}

@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    trailingColor: Color? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val c = AppTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = Dimens.touchTarget)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(12.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.body, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, style = AppTheme.type.number, color = trailingColor ?: c.textPrimary)
        }
        if (trailingContent != null) {
            Spacer(Modifier.width(8.dp))
            trailingContent()
        }
    }
}

@Composable
fun StatusPill(text: String, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, style = AppTheme.type.label, color = color, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}

@Composable
fun DateSwitcher(title: String, subtitle: String?, onPrev: () -> Unit, onNext: () -> Unit, onTitleClick: () -> Unit, nextEnabled: Boolean = true) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, "Предыдущий день", tint = c.textPrimary) }
        Column(
            Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onTitleClick).padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(title, style = AppTheme.type.section, color = c.textPrimary)
            if (subtitle != null) Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary)
        }
        IconButton(onClick = onNext, enabled = nextEnabled) {
            Icon(Icons.Filled.ChevronRight, "Следующий день", tint = if (nextEnabled) c.textPrimary else c.textTertiary)
        }
    }
}

@Composable
fun ScreenColumn(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxSize().background(AppTheme.colors.background), content = content)
}
