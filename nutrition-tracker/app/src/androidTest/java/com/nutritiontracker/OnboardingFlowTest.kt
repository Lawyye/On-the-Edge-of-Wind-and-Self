package com.nutritiontracker

import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Проходит онбординг через интерфейс на чистой установке и добавляет воду.
 * Запускается в CI первым; затем приложение переустанавливается поверх,
 * и [DataSurvivesReinstallTest] проверяет, что данные сохранились.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun waitForText(text: String, timeoutMs: Long = 10_000) {
        compose.waitUntil(timeoutMs) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun completeOnboardingAndLogWater() {
        waitForText("Начать")
        compose.onNodeWithText("Начать").performClick()

        waitForText("Мужской")
        compose.onNodeWithText("Мужской").performClick()
        compose.onNodeWithText("Дата рождения").performClick()
        waitForText("Готово")
        compose.onNodeWithText("Готово").performClick()
        compose.onNode(hasSetTextAction() and hasText("Рост")).performTextInput("180")
        compose.onNode(hasSetTextAction() and hasText("Текущий вес")).performTextInput("82")
        compose.onNode(hasSetTextAction() and hasText("Желаемый вес")).performScrollTo().performTextInput("76")
        compose.onNodeWithText("Далее").performClick()

        waitForText("Умеренная")
        compose.onNodeWithText("Умеренная", substring = true).performClick()
        compose.onNodeWithText("Далее").performClick()

        waitForText("Ваша цель")
        compose.onNodeWithText("Готово").performClick()

        waitForText("Сегодня")
        // Ждём окончания загрузки главной: до этого в списке только скелетоны.
        waitForText("Приёмы пищи")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("+250"))
        compose.onNodeWithText("+250").performClick()
        waitForText("250 / 2000 мл")

        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NutritionApp).container
        runBlocking {
            assertNotNull(container.profile.get())
            val weights = container.database.weightDao().all()
            check(weights.any { it.weightKg == 82.0 }) { "Первое взвешивание из онбординга не сохранилось: $weights" }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class DataSurvivesReinstallTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun profileAndWaterAreStillThere() {
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("Сегодня")).fetchSemanticsNodes().isNotEmpty() }
        val container = (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as NutritionApp).container
        runBlocking {
            assertNotNull(container.profile.get())
            val water = container.database.waterDao().all().filter { it.epochDay == LocalDate.now().toEpochDay() }.sumOf { it.amountMl }
            check(water >= 250) { "Вода за сегодня пропала после переустановки: $water" }
            check(container.database.weightDao().all().isNotEmpty()) { "Взвешивания пропали после переустановки" }
        }
    }
}
