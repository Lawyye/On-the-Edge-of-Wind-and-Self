package com.nutritiontracker.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nutritiontracker.ui.appContainer
import com.nutritiontracker.ui.appViewModel
import com.nutritiontracker.ui.components.PrimaryButton
import com.nutritiontracker.ui.components.SecondaryButton
import com.nutritiontracker.ui.profile.ActivitySelector
import com.nutritiontracker.ui.profile.BodyFields
import com.nutritiontracker.ui.profile.DateField
import com.nutritiontracker.ui.profile.DeficitSelector
import com.nutritiontracker.ui.profile.GoalPreview
import com.nutritiontracker.ui.profile.ProfileFormViewModel
import com.nutritiontracker.ui.profile.SexSelector
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens

@Composable
fun OnboardingScreen(onDone: () -> Unit, onRestore: () -> Unit) {
    val vm = appViewModel { c, _ -> ProfileFormViewModel(c) }
    val f by vm.form.collectAsStateWithLifecycle()
    val c = AppTheme.colors
    var step by rememberSaveable { mutableIntStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    // Профиль мог появиться из резервной копии — тогда онбординг не нужен.
    val container = appContainer()
    val existingProfile by container.profile.observe().collectAsStateWithLifecycle(initialValue = null)
    LaunchedEffect(existingProfile) {
        if (existingProfile != null && !finished) {
            finished = true
            onDone()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Dimens.screenPadding, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(if (i <= step) c.accent else c.surfaceVariant, RoundedCornerShape(2.dp)),
                )
            }
        }
        AnimatedContent(targetState = step, modifier = Modifier.weight(1f), label = "step") { s ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (s) {
                    0 -> Welcome(onRestore)
                    1 -> {
                        StepTitle("О вас", "Эти данные нужны для расчёта основного обмена и норм питательных веществ.")
                        Text("Пол", style = AppTheme.type.label, color = c.textSecondary)
                        SexSelector(f.sex) { sex -> vm.edit { it.copy(sex = sex) } }
                        DateField("Дата рождения", f.birthDate, { d -> vm.edit { it.copy(birthDate = d) } }, error = f.ageError)
                        f.age?.let { Text("Возраст: $it", style = AppTheme.type.caption, color = c.textSecondary) }
                        BodyFields(f, vm::edit)
                    }
                    2 -> {
                        StepTitle("Активность", "Коэффициент умножается на основной обмен. Если сомневаетесь, выберите уровень ниже — так оценка будет осторожнее.")
                        ActivitySelector(f.activity) { a -> vm.edit { it.copy(activity = a) } }
                    }
                    else -> {
                        StepTitle("Темп снижения веса", "Цель по калориям можно изменить в любой момент в профиле.")
                        DeficitSelector(f, vm::edit)
                        GoalPreview(f.preview())
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(Dimens.screenPadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 0) SecondaryButton("Назад", onClick = { step-- }, modifier = Modifier.weight(1f))
            val canNext = when (step) {
                0 -> true
                1 -> f.sex != null && f.birthDate != null && f.ageError == null && f.heightCm != null && f.heightError == null &&
                    f.weightKg != null && f.weightError == null && f.targetKg != null && f.targetError == null
                2 -> f.activity != null
                else -> f.valid && !f.saving
            }
            PrimaryButton(
                text = when (step) { 0 -> "Начать"; 3 -> "Готово"; else -> "Далее" },
                onClick = {
                    if (step < 3) step++ else vm.save {
                        if (!finished) { finished = true; onDone() }
                    }
                },
                enabled = canNext,
                modifier = Modifier.weight(if (step > 0) 1.4f else 1f),
            )
        }
    }
}

@Composable
private fun StepTitle(title: String, text: String) {
    Column {
        Text(title, style = AppTheme.type.title, color = AppTheme.colors.textPrimary)
        Spacer(Modifier.height(4.dp))
        Text(text, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary)
    }
}

@Composable
private fun Welcome(onRestore: () -> Unit) {
    val c = AppTheme.colors
    Spacer(Modifier.height(16.dp))
    Text("Рацион", style = AppTheme.type.hero, color = c.textPrimary)
    Text("Личный дневник питания: калории, БЖУ, витамины и минералы.", style = AppTheme.type.body, color = c.textSecondary)
    Spacer(Modifier.height(8.dp))
    Feature(Icons.Outlined.Lock, "Данные только на телефоне", "Без аккаунтов и серверов. В интернет уходят только поисковые запросы к базам продуктов.")
    Feature(Icons.Outlined.CloudOff, "Работает без интернета", "Дневник, расчёты, рецепты и сохранённые продукты доступны офлайн.")
    Feature(Icons.Outlined.Science, "Честные цифры", "Если в базе нет данных о веществе, приложение пишет «нет данных», а не ноль.")
    Feature(Icons.Outlined.Mic, "Голосовой ввод", "Продиктуйте, что съели, — и проверьте результат перед сохранением.")
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onRestore) {
        Text("Перенос с другого телефона: восстановить из резервной копии", color = c.accent, style = AppTheme.type.caption)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, text: String) {
    val c = AppTheme.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Box(Modifier.background(c.surface, RoundedCornerShape(12.dp)).padding(10.dp)) {
            Icon(icon, null, tint = c.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppTheme.type.bodyStrong, color = c.textPrimary)
            Text(text, style = AppTheme.type.caption, color = c.textSecondary)
        }
    }
}
