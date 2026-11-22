package com.nutritiontracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nutritiontracker.AppContainer
import com.nutritiontracker.NutritionApp
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodSource
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")

@Composable
fun appContainer(): AppContainer = (LocalContext.current.applicationContext as NutritionApp).container

@Composable
inline fun <reified VM : ViewModel> appViewModel(
    key: String? = null,
    crossinline create: (AppContainer, SavedStateHandle) -> VM,
): VM {
    val container = appContainer()
    return viewModel(
        key = key,
        factory = viewModelFactory { initializer { create(container, createSavedStateHandle()) } },
    )
}

object Dates {
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", RU)
    private val dayMonthYear = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
    private val weekday = DateTimeFormatter.ofPattern("EEEE", RU)
    private val short = DateTimeFormatter.ofPattern("d.MM", RU)
    private val shortWeekday = DateTimeFormatter.ofPattern("EE", RU)

    fun title(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (ChronoUnit.DAYS.between(date, today)) {
        0L -> "Сегодня"
        1L -> "Вчера"
        -1L -> "Завтра"
        else -> if (date.year == today.year) dayMonth.format(date) else dayMonthYear.format(date)
    }

    fun subtitle(date: LocalDate, today: LocalDate = LocalDate.now()): String {
        val wd = weekday.format(date).replaceFirstChar { it.uppercase() }
        return if (ChronoUnit.DAYS.between(date, today) in -1L..1L) "$wd, ${dayMonth.format(date)}" else wd
    }

    fun full(date: LocalDate): String = dayMonthYear.format(date)
    fun dayMonth(date: LocalDate): String = dayMonth.format(date)
    fun short(date: LocalDate): String = short.format(date)
    fun weekdayShort(date: LocalDate): String = shortWeekday.format(date).replaceFirstChar { it.uppercase() }

    fun toMillis(date: LocalDate): Long = date.toEpochDay() * 86_400_000L
    fun fromMillis(millis: Long): LocalDate = LocalDate.ofEpochDay(Math.floorDiv(millis, 86_400_000L))
}

fun sourceShort(s: FoodSource): String = when (s) {
    FoodSource.USDA_FOUNDATION, FoodSource.USDA_SR_LEGACY, FoodSource.USDA_SURVEY, FoodSource.USDA_BRANDED -> "USDA"
    FoodSource.OPEN_FOOD_FACTS -> "Open Food Facts"
    FoodSource.USER -> "Свой продукт"
    FoodSource.RECIPE -> "Рецепт"
}

fun unitSymbol(u: AmountUnit) = u.symbol

/** Склонение: 1 продукт, 2 продукта, 5 продуктов. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val m10 = n % 10
    val m100 = n % 100
    val word = when {
        m10 == 1 && m100 != 11 -> one
        m10 in 2..4 && m100 !in 12..14 -> few
        else -> many
    }
    return "$n $word"
}
