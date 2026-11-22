package com.nutritiontracker.ui.foods

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.data.FoodItem
import com.nutritiontracker.data.displayName
import com.nutritiontracker.remote.RemoteErrorKind
import com.nutritiontracker.remote.RemoteFood
import com.nutritiontracker.ui.components.BannerKind
import com.nutritiontracker.ui.components.EmptyState
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.SectionHeader
import com.nutritiontracker.ui.components.SkeletonList
import com.nutritiontracker.ui.sourceShort
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens

@Composable
fun SearchField(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier, placeholder: String = "Название, производитель, категория") {
    val c = AppTheme.colors
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text(placeholder, color = c.textTertiary, style = AppTheme.type.body) },
        leadingIcon = { Icon(Icons.Filled.Search, null, tint = c.textSecondary) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Filled.Clear, "Очистить", tint = c.textSecondary) }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        shape = RoundedCornerShape(Dimens.radiusControl),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.outline,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.surface,
            cursorColor = c.accent,
        ),
        modifier = modifier.fillMaxWidth(),
    )
}

/** Краткая строка пищевой ценности: «165 ккал · Б 31 · Ж 3,6 · У 0 на 100 г». */
fun shortNutrition(data: FoodData): String {
    val n = data.nutrients
    val kcal = n[Nutrient.ENERGY]?.let { "${NutrientFormat.kcal(it)} ккал" } ?: "ккал: нет данных"
    fun g(nu: Nutrient, l: String) = "$l ${n[nu]?.let { NutrientFormat.amount(it) } ?: "—"}"
    return "$kcal · ${g(Nutrient.PROTEIN, "Б")} · ${g(Nutrient.FAT, "Ж")} · ${g(Nutrient.CARBS, "У")} ${data.basis.per100Ru}"
}

@Composable
fun FoodRow(
    title: String,
    subtitle: String,
    nutrition: String,
    onClick: () -> Unit,
    favorite: Boolean? = null,
    onToggleFavorite: (() -> Unit)? = null,
    badge: String? = null,
) {
    val c = AppTheme.colors
    Row(
        Modifier.fillMaxWidth().background(c.surface, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.bodyStrong, color = c.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(2.dp))
            Text(nutrition, style = AppTheme.type.caption, color = c.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (badge != null) {
            Text(badge, style = AppTheme.type.label, color = c.success, modifier = Modifier.padding(horizontal = 6.dp))
        }
        if (favorite != null && onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (favorite) "Убрать из избранного" else "В избранное",
                    tint = if (favorite) c.warning else c.textTertiary,
                )
            }
        } else {
            Spacer(Modifier.width(10.dp))
        }
    }
}

fun foodSubtitle(item: FoodItem): String = listOfNotNull(
    item.data.name.takeIf { it != item.displayName },
    item.data.brand,
    sourceShort(item.source),
).joinToString(" · ")

/** Результаты поиска: сначала сохранённые продукты, затем найденные в интернете. */
fun LazyListScope.searchResults(
    query: String,
    local: List<FoodItem>,
    online: OnlineState,
    favoriteIds: Set<Long>,
    onOpenLocal: (Long) -> Unit,
    onOpenRemote: (RemoteFood) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
    onRetry: () -> Unit,
    onCreateFood: () -> Unit,
) {
    item { SectionHeader(if (local.isEmpty()) "В сохранённых — ничего" else "Сохранённые продукты") }
    items(local, key = { "local-${it.id}" }) { f ->
        Column(Modifier.padding(bottom = 8.dp)) {
            FoodRow(
                title = f.displayName,
                subtitle = foodSubtitle(f),
                nutrition = shortNutrition(f.data),
                onClick = { onOpenLocal(f.id) },
                favorite = f.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(f.id, f.id !in favoriteIds) },
            )
        }
    }
    when (online) {
        OnlineState.Idle -> {}
        OnlineState.Disabled -> item {
            InfoBanner("Поиск в интернете выключен в настройках — показаны только сохранённые продукты.")
        }
        OnlineState.Offline -> item {
            InfoBanner(
                "Нет подключения к интернету. Поиск работает по сохранённым продуктам; новые продукты найдутся, когда появится сеть.",
                kind = BannerKind.WARNING, action = "Попробовать всё равно", onAction = onRetry,
            )
        }
        OnlineState.Loading -> {
            item { SectionHeader("Ищем в USDA и Open Food Facts…") }
            item { SkeletonList(3) }
        }
        is OnlineState.Done -> {
            val r = online.result
            r.errors.forEach { e ->
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        InfoBanner(
                            "${e.source}: ${e.kind.messageRu}.",
                            kind = if (e.kind == RemoteErrorKind.NO_NETWORK) BannerKind.WARNING else BannerKind.ERROR,
                            action = "Повторить", onAction = onRetry,
                        )
                    }
                }
            }
            if (r.usdaSkippedNoTranslation) {
                item {
                    Column(Modifier.padding(bottom = 8.dp)) {
                        InfoBanner("USDA ищет только по-английски, а для «$query» перевода в словаре нет. Попробуйте английское название — например, «chicken breast».")
                    }
                }
            }
            if (r.usda.isNotEmpty()) {
                item { SectionHeader("USDA FoodData Central" + (r.usdaQuery?.takeIf { it != query.trim() }?.let { " · «$it»" } ?: "")) }
                items(r.usda, key = { "usda-${it.food.externalId}" }) { rf ->
                    Column(Modifier.padding(bottom = 8.dp)) {
                        FoodRow(rf.food.name, listOfNotNull(rf.food.brand, rf.food.category).joinToString(" · "), shortNutrition(rf.food), onClick = { onOpenRemote(rf) })
                    }
                }
            }
            if (r.openFoodFacts.isNotEmpty()) {
                item { SectionHeader("Open Food Facts") }
                items(r.openFoodFacts, key = { "off-${it.food.externalId}" }) { rf ->
                    Column(Modifier.padding(bottom = 8.dp)) {
                        FoodRow(rf.food.name, listOfNotNull(rf.food.brand, rf.food.category).joinToString(" · "), shortNutrition(rf.food), onClick = { onOpenRemote(rf) })
                    }
                }
            }
            if (r.usda.isEmpty() && r.openFoodFacts.isEmpty() && r.errors.isEmpty() && local.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.SearchOff, "Ничего не найдено",
                        "Попробуйте другое название или создайте свой продукт.",
                        action = "Создать продукт", onAction = onCreateFood,
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryChips(history: List<String>, onPick: (String) -> Unit, onClear: () -> Unit) {
    if (history.isEmpty()) return
    val c = AppTheme.colors
    Column {
        SectionHeader("История поиска", action = "Очистить", onAction = onClear)
        history.forEach { q ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(q) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.History, null, tint = c.textTertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                Text(q, style = AppTheme.type.body, color = c.textPrimary)
            }
        }
    }
}

@Composable
fun OfflineHint() {
    val c = AppTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Outlined.CloudOff, null, tint = c.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text("Офлайн: поиск по сохранённым продуктам", style = AppTheme.type.caption, color = c.warning)
    }
}

fun LazyListScope.foodList(
    items: List<FoodItem>,
    favoriteIds: Set<Long>,
    keyPrefix: String,
    onOpen: (Long) -> Unit,
    onToggleFavorite: (Long, Boolean) -> Unit,
) {
    items(items, key = { "$keyPrefix-${it.id}" }) { f ->
        Column(Modifier.padding(bottom = 8.dp)) {
            FoodRow(
                title = f.displayName,
                subtitle = foodSubtitle(f),
                nutrition = shortNutrition(f.data),
                onClick = { onOpen(f.id) },
                favorite = f.id in favoriteIds,
                onToggleFavorite = { onToggleFavorite(f.id, f.id !in favoriteIds) },
            )
        }
    }
}
