package com.nutritiontracker.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nutritiontracker.AppContainer
import com.nutritiontracker.core.backup.BackupReadResult
import com.nutritiontracker.ui.Dates
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.components.InfoBanner
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class BackupViewModel(private val c: AppContainer) : ViewModel() {
    private val _busy = MutableStateFlow(false)
    val busy = _busy.asStateFlow()
    private val _pending = MutableStateFlow<BackupReadResult?>(null)
    val pending = _pending.asStateFlow()
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()
    private val _safetyCopies = MutableStateFlow<List<File>>(emptyList())
    val safetyCopies = _safetyCopies.asStateFlow()

    init { refreshSafetyCopies() }

    private fun refreshSafetyCopies() {
        viewModelScope.launch(Dispatchers.IO) { _safetyCopies.value = c.backup.safetyCopies() }
    }

    private fun io(work: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        viewModelScope.launch {
            try {
                work()
            } catch (e: Exception) {
                _messages.emit("Ошибка: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                _busy.value = false
            }
        }
    }

    fun exportJson(uri: Uri, write: (Uri, String) -> Unit) = io {
        val text = c.backup.exportJson()
        withContext(Dispatchers.IO) { write(uri, text) }
        _messages.emit("Резервная копия сохранена")
    }

    fun exportCsv(uri: Uri, days: Long?, write: (Uri, String) -> Unit) = io {
        val to = LocalDate.now()
        val text = c.backup.exportCsv(days?.let { to.minusDays(it - 1) }, if (days != null) to else null)
        withContext(Dispatchers.IO) { write(uri, text) }
        _messages.emit("Дневник выгружен в CSV")
    }

    fun readForImport(read: suspend () -> String) = io {
        val text = withContext(Dispatchers.IO) { read() }
        _pending.value = c.backup.read(text)
    }

    fun confirmImport() {
        val valid = _pending.value as? BackupReadResult.Valid ?: return
        _pending.value = null
        io {
            c.backup.saveSafetyCopy()
            c.backup.replaceAll(valid)
            _messages.emit("Данные восстановлены из копии")
            refreshSafetyCopies()
        }
    }

    fun dismissPending() { _pending.value = null }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val vm = appViewModel { c, _ -> BackupViewModel(c) }
    val context = LocalContext.current
    val c = AppTheme.colors
    val busy by vm.busy.collectAsStateWithLifecycle()
    val pending by vm.pending.collectAsStateWithLifecycle()
    val copies by vm.safetyCopies.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var csvDays by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val write: (Uri, String) -> Unit = { uri, text ->
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Не удалось открыть файл для записи")
        out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }
    val jsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportJson(it, write) }
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let { vm.exportCsv(it, csvDays, write) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            vm.readForImport {
                val input = context.contentResolver.openInputStream(u) ?: error("Не удалось открыть файл")
                input.use { s ->
                    val bytes = s.readBytes()
                    require(bytes.size <= 64 * 1024 * 1024) { "Файл слишком большой" }
                    String(bytes, Charsets.UTF_8)
                }
            }
        }
    }
    val today = LocalDate.now()

    Scaffold(containerColor = c.background, topBar = { AppTopBar("Резервная копия", onBack = onBack) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            AppCard {
                Text("Все данные в JSON", style = AppTheme.type.section, color = c.textPrimary)
                Text(
                    "Профиль, дневник, продукты, рецепты, вес, вода, цели и шаблоны. Файл проверяется контрольной суммой; по нему можно перенести данные на другой телефон.",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Сохранить копию", onClick = { jsonLauncher.launch("racion-backup-$today.json") }, enabled = !busy, icon = Icons.Outlined.Download, modifier = Modifier.fillMaxWidth())
            }
            AppCard {
                Text("Дневник в CSV", style = AppTheme.type.section, color = c.textPrimary)
                Text(
                    "Таблица для Excel или Google Таблиц: разделитель «;», десятичная запятая. Пустая ячейка — нет данных.",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Последние 30 дней", onClick = { csvDays = 30; csvLauncher.launch("racion-diary-30d-$today.csv") }, enabled = !busy, icon = Icons.Outlined.TableChart, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                SecondaryButton("Весь дневник", onClick = { csvDays = null; csvLauncher.launch("racion-diary-$today.csv") }, enabled = !busy, icon = Icons.Outlined.TableChart, modifier = Modifier.fillMaxWidth())
            }
            AppCard {
                Text("Восстановить из копии", style = AppTheme.type.section, color = c.textPrimary)
                Text(
                    "Перед заменой файл проверяется. Текущие данные заменяются только после подтверждения, а их копия сохраняется внутри приложения.",
                    style = AppTheme.type.caption, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Выбрать файл", onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }, enabled = !busy, icon = Icons.Outlined.Upload, modifier = Modifier.fillMaxWidth())
            }
            if (copies.isNotEmpty()) {
                AppCard {
                    Text("Страховочные копии", style = AppTheme.type.section, color = c.textPrimary)
                    Text("Создаются автоматически перед каждым восстановлением. Хранятся последние пять.", style = AppTheme.type.caption, color = c.textSecondary)
                    Spacer(Modifier.height(6.dp))
                    copies.take(5).forEach { f ->
                        val date = Instant.ofEpochMilli(f.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
                        TextButton(onClick = { vm.readForImport { f.readText() } }, enabled = !busy) {
                            Text("Вернуть состояние до импорта ${Dates.full(date)}", color = c.accent, style = AppTheme.type.caption)
                        }
                    }
                }
            }
            InfoBanner("Файлы копий не шифруются. Храните их там, куда нет доступа у посторонних.")
            Spacer(Modifier.height(12.dp))
        }
    }

    when (val p = pending) {
        is BackupReadResult.Invalid -> AlertDialog(
            onDismissRequest = vm::dismissPending,
            containerColor = c.surface,
            title = { Text("Файл не подходит", style = AppTheme.type.section, color = c.textPrimary) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text("Данные не изменены.", style = AppTheme.type.body, color = c.textSecondary)
                    Spacer(Modifier.height(8.dp))
                    p.errors.take(20).forEach { Text("• $it", style = AppTheme.type.caption, color = c.error) }
                }
            },
            confirmButton = { TextButton(onClick = vm::dismissPending) { Text("Понятно", color = c.accent) } },
        )
        is BackupReadResult.Valid -> {
            val sm = p.summary
            val exported = Instant.ofEpochMilli(p.file.exportedAt).atZone(ZoneId.systemDefault()).toLocalDate()
            AlertDialog(
                onDismissRequest = vm::dismissPending,
                containerColor = c.surface,
                title = { Text("Заменить данные?", style = AppTheme.type.section, color = c.textPrimary) },
                text = {
                    Column {
                        Text("Копия от ${Dates.full(exported)} (версия ${p.file.appVersion}):", style = AppTheme.type.body, color = c.textSecondary)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "записей дневника — ${sm.entries}, продуктов — ${sm.foods}, рецептов — ${sm.recipes}, взвешиваний — ${sm.weights}, записей воды — ${sm.water}" +
                                (if (sm.hasProfile) ", профиль" else ", без профиля") +
                                (if (sm.firstEpochDay != null && sm.lastEpochDay != null) ". Период: ${Dates.full(LocalDate.ofEpochDay(sm.firstEpochDay!!))} — ${Dates.full(LocalDate.ofEpochDay(sm.lastEpochDay!!))}." else "."),
                            style = AppTheme.type.caption, color = c.textPrimary,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text("Все текущие данные будут заменены. Их копия сохранится внутри приложения, и к ним можно будет вернуться.", style = AppTheme.type.caption, color = c.warning)
                    }
                },
                confirmButton = { TextButton(onClick = vm::confirmImport) { Text("Заменить", color = c.error) } },
                dismissButton = { TextButton(onClick = vm::dismissPending) { Text("Отмена", color = c.textSecondary) } },
            )
        }
        null -> {}
    }
}
