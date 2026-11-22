package com.nutritiontracker.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nutritiontracker.BuildConfig
import com.nutritiontracker.core.calc.AdaptiveTdee
import com.nutritiontracker.core.calc.EnergyCalculator
import com.nutritiontracker.core.norms.DriNorms
import com.nutritiontracker.ui.components.AppCard
import com.nutritiontracker.ui.components.AppTopBar
import com.nutritiontracker.ui.theme.AppTheme
import com.nutritiontracker.ui.theme.Dimens

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val c = AppTheme.colors
    Scaffold(containerColor = c.background, topBar = { AppTopBar("О приложении", onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = Dimens.screenPadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            Block(
                "Рацион ${BuildConfig.VERSION_NAME}",
                "Личный дневник питания. Без аккаунтов, рекламы, подписок и аналитики. Все данные хранятся на телефоне.",
            )
            Block(
                "Источники пищевой ценности",
                "• USDA FoodData Central — Министерство сельского хозяйства США; данные в общественном достоянии (CC0). Типы данных: Foundation, SR Legacy, FNDDS.\n" +
                    "• Open Food Facts — открытая база продуктов (лицензия ODbL); данные с этикеток, заполняются сообществом и могут содержать ошибки.\n" +
                    "• Ваши продукты и рецепты.\n\n" +
                    "Если источник не сообщает значение вещества, приложение хранит «нет данных», а не ноль. Значения ничем не заменяются и не досчитываются.",
            )
            Block(
                "Как считается",
                "• Ценность порции = значение на 100 г × масса / 100.\n" +
                    "• Рецепт: сумма ингредиентов / вес готового блюда × 100.\n" +
                    "• Основной обмен — формула Миффлина — Сан Жеора (1990); расход = BMR × коэффициент активности (1,2–1,9).\n" +
                    "• Темп изменения веса — приблизительно, из расчёта ≈${EnergyCalculator.KCAL_PER_KG_BODY_WEIGHT.toInt()} ккал на 1 кг.\n" +
                    "• Адаптивный расход: средняя калорийность минус энергия тренда веса за ${AdaptiveTdee.MIN_DAYS}–${AdaptiveTdee.WINDOW_DAYS} дней; шаг изменения не больше ${AdaptiveTdee.MAX_STEP_KCAL.toInt()} ккал в неделю.\n" +
                    "• Калорийность из источника не пересчитывается из БЖУ; при грубом расхождении показывается предупреждение.\n" +
                    "• Суммы, где данные есть не для всех продуктов, показываются как нижняя граница (≥).\n" +
                    "• Значения хранятся с полной точностью, округляются только при показе.",
            )
            Block("Нормы", "${DriNorms.SYSTEM_TITLE}. ${DriNorms.SYSTEM_DESCRIPTION}")
            Block(
                "Голосовой ввод",
                "Используется системное распознавание речи Android. Приложение получает только текст, аудио не сохраняется. Записи попадают в дневник только после вашего подтверждения.",
            )
            Block(
                "Приватность",
                "Дневник, вес и профиль не покидают телефон и не попадают в облачную копию Google. В интернет отправляются только поисковые запросы и идентификаторы продуктов — к USDA и Open Food Facts. Перенос на другой телефон — через резервную копию в файл.",
            )
            Block(
                "Важно",
                "Приложение не ставит диагнозов и не заменяет консультацию врача или диетолога. Справочные нормы рассчитаны на здоровых взрослых и не учитывают беременность, кормление грудью и заболевания.",
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Block(title: String, text: String) {
    AppCard {
        Text(title, style = AppTheme.type.section, color = AppTheme.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(text, style = AppTheme.type.caption, color = AppTheme.colors.textSecondary)
    }
}
