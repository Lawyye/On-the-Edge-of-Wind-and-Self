package com.nutritiontracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.data.AppSettings
import com.nutritiontracker.data.work.CacheRefreshWorker
import com.nutritiontracker.ui.appContainer
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTextField
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val container = appContainer()
    val context = LocalContext.current
    val c = AppTheme.colors
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var keyText by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(settings.usdaApiKey) { if (keyText == null) keyText = settings.usdaApiKey }

    fun update(t: (AppSettings) -> AppSettings) = scope.launch { container.settings.update(t) }

    Scaffold(containerColor = c.background, topBar = { AppTopBar("Настройки", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            AppCard {
                Text("Поиск в интернете", style = AppTheme.type.section, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Toggle("Искать новые продукты онлайн", "Без этого поиск идёт только по сохранённым продуктам", settings.onlineSearchEnabled) { v -> update { it.copy(onlineSearchEnabled = v) } }
                Toggle("USDA FoodData Central", "Подробный состав, включая витамины и минералы. Ищет по-английски — русский запрос переводится словарём", settings.usdaEnabled) { v -> update { it.copy(usdaEnabled = v) } }
                Toggle("Open Food Facts", "Продукты производителей, в том числе российские. Данные по этикеткам", settings.openFoodFactsEnabled) { v -> update { it.copy(openFoodFactsEnabled = v) } }
                Toggle("Обновлять сохранённые продукты", "Раз в неделю при наличии сети догружает недостающие данные. Записи дневника не меняются", settings.autoRefreshEnabled) { v -> update { it.copy(autoRefreshEnabled = v) } }
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Синхронизировать сейчас", onClick = {
                    CacheRefreshWorker.syncWhenOnline(context)
                    scope.launch { snackbar.showSnackbar("Синхронизация запустится, как только будет сеть") }
                }, modifier = Modifier.fillMaxWidth())
            }
            AppCard {
                Text("Ключ USDA API", style = AppTheme.type.section, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Без ключа используется общий DEMO_KEY: он работает, но допускает мало запросов в час. Бесплатный личный ключ выдаётся на api.data.gov/signup — " +
                        "он хранится только на этом телефоне и не попадает в резервную копию.",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                AppTextField(
                    keyText ?: "", { keyText = it }, "Ключ API", placeholder = "DEMO_KEY",
                    supporting = if (settings.usdaApiKey.isBlank()) "Сейчас: DEMO_KEY" else "Сохранён личный ключ",
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Сохранить ключ", onClick = {
                        update { it.copy(usdaApiKey = (keyText ?: "").trim()) }
                        scope.launch { snackbar.showSnackbar("Ключ сохранён") }
                    }, modifier = Modifier.weight(1f))
                    SecondaryButton("Удалить", onClick = {
                        keyText = ""
                        update { it.copy(usdaApiKey = "") }
                    }, modifier = Modifier.weight(1f))
                }
            }
            AppCard {
                Text("История поиска", style = AppTheme.type.section, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Очистить историю поиска", onClick = {
                    scope.launch {
                        container.foods.clearSearchHistory()
                        snackbar.showSnackbar("История очищена")
                    }
                }, modifier = Modifier.fillMaxWidth())
            }
            InfoBanner(
                "Приватность: дневник, вес и профиль хранятся только на телефоне и не копируются в облако Google. " +
                    "В интернет уходят только поисковые запросы и идентификаторы продуктов — к USDA и Open Food Facts. Рекламы и аналитики нет.",
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = AppTheme.type.body, color = c.textPrimary)
            Text(subtitle, style = AppTheme.type.caption, color = c.textSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedThumbColor = c.onAccent, checkedTrackColor = c.accent))
    }
}
