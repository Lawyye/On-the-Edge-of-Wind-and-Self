package com.nutritiontracker.remote

import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.text.FoodQueryTranslator
import com.nutritiontracker.core.text.FoodSearch
import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.Locale

/**
 * Проверка настоящих сервисов. Запускается только с NT_LIVE_API=1 (отдельный
 * шаг CI) и печатает, что выбрал бы голосовой ввод для типичных фраз: тот же
 * перевод, те же клиенты и та же оценка кандидатов, что в приложении.
 * Не падает от сбоев сервисов — это диагностика.
 */
class LiveApiProbeTest {
    private val enabled = System.getenv("NT_LIVE_API") == "1"
    private val http = RemoteJson.defaultOkHttp()

    private fun describe(r: RemoteResult<List<RemoteFood>>): String = when (r) {
        is RemoteResult.Success -> "OK ${r.value.size}"
        is RemoteResult.Failure -> "FAIL ${r.error.userMessage}"
    }

    @Test
    fun probe() = runBlocking<Unit> {
        assumeTrue("NT_LIVE_API=1 не задан", enabled)
        val usda = UsdaClient(http, { "" })
        val off = OpenFoodFactsClient(http, UA)
        for (query in listOf("спагетти", "творог", "рис", "куриное филе", "сливочное масло", "гречка", "овсянка")) {
            val english = FoodQueryTranslator.translate(query).englishQuery
            val u = english?.let { usda.search(it) }
            val o = off.search(query)
            val all = listOfNotNull((u as? RemoteResult.Success)?.value, (o as? RemoteResult.Success)?.value).flatten()
                .filter { it.food.nutrients.isKnown(Nutrient.ENERGY) }
            val ranked = all.map { it to FoodSearch.autoPickScore(query, english, it.food) }.filter { it.second > 0 }.sortedByDescending { it.second }
            println("LIVE pick [$query → $english] USDA ${u?.let(::describe) ?: "—"}, OFF ${describe(o)}")
            ranked.take(4).forEachIndexed { i, (r, score) ->
                val n = r.food.nutrients
                println(
                    "LIVE pick   ${i + 1}. ${String.format(Locale.ROOT, "%.2f", score)} ${r.food.source.key}: ${r.food.name.take(70)} — " +
                        "${n[Nutrient.ENERGY]?.toInt()} ккал, Б ${n[Nutrient.PROTEIN]}, Ж ${n[Nutrient.FAT]}, У ${n[Nutrient.CARBS]}",
                )
            }
        }
    }

    private companion object {
        const val UA = "Racion/1.1 (personal nutrition tracker; CI probe)"
    }
}
