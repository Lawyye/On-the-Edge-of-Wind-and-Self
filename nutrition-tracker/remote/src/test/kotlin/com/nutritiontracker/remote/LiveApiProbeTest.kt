package com.nutritiontracker.remote

import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Проверка настоящих сервисов. Запускается только с NT_LIVE_API=1 (отдельный
 * шаг CI): печатает коды ответов и что вернули клиенты приложения, чтобы
 * видеть реальную причину ошибок, а не гадать. Не падает от сбоев сервисов.
 */
class LiveApiProbeTest {
    private val enabled = System.getenv("NT_LIVE_API") == "1"
    private val http = RemoteJson.defaultOkHttp()

    private fun raw(label: String, url: String) {
        val started = System.nanoTime()
        val line = try {
            http.newCall(Request.Builder().url(url).header("User-Agent", UA).build()).execute().use { r ->
                val body = r.body.string()
                val limit = r.header("X-RateLimit-Remaining")?.let { " remaining=$it" } ?: ""
                "HTTP ${r.code}$limit bytes=${body.length} ${body.take(160).replace('\n', ' ')}"
            }
        } catch (e: Exception) {
            "EXCEPTION ${e::class.simpleName}: ${e.message}"
        }
        println("LIVE raw  [$label] ${(System.nanoTime() - started) / 1_000_000} ms: $line")
    }

    private fun report(label: String, r: RemoteResult<List<RemoteFood>>) {
        val text = when (r) {
            is RemoteResult.Success -> "OK ${r.value.size}: " + r.value.take(4).joinToString(" | ") {
                "${it.food.name} (${it.food.nutrients.known.size} в-в)"
            }
            is RemoteResult.Failure -> "FAIL ${r.error.userMessage} cause=${r.error.cause?.let { it::class.simpleName + ": " + it.message?.take(120) }}"
        }
        println("LIVE app  [$label] $text")
    }

    @Test
    fun probe() = runBlocking<Unit> {
        assumeTrue("NT_LIVE_API=1 не задан", enabled)
        raw("USDA search DEMO_KEY", "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=DEMO_KEY&query=cottage%20cheese&pageSize=2&dataType=Foundation,SR%20Legacy")
        raw("OFF cgi search", "https://world.openfoodfacts.org/cgi/search.pl?search_terms=%D1%82%D0%B2%D0%BE%D1%80%D0%BE%D0%B3&search_simple=1&action=process&json=1&page_size=2&fields=code,product_name")
        raw("OFF search-a-licious", "https://search.openfoodfacts.org/search?q=%D1%82%D0%B2%D0%BE%D1%80%D0%BE%D0%B3&page_size=2&fields=code,product_name,nutriments")
        raw("OFF api v2 search", "https://world.openfoodfacts.org/api/v2/search?categories_tags_en=cottage-cheeses&page_size=2&fields=code,product_name")

        val usda = UsdaClient(http, { "" })
        val off = OpenFoodFactsClient(http, UA)
        for (q in listOf("cottage cheese", "spaghetti", "olive oil")) report("USDA '$q'", usda.search(q, 10))
        for (q in listOf("творог", "спагетти", "оливковое масло")) report("OFF '$q'", off.search(q, 10))

        // Как голосовой ввод до исправления: три позиции одновременно в оба сервиса.
        val parallel = listOf("chicken breast", "rice", "buckwheat").map { q -> async(kotlinx.coroutines.Dispatchers.IO) { q to usda.search(q, 10) } } +
            listOf("курица", "рис", "гречка").map { q -> async(kotlinx.coroutines.Dispatchers.IO) { q to off.search(q, 10) } }
        parallel.awaitAll().forEach { (q, r) -> report("parallel '$q'", r) }
    }

    private companion object {
        const val UA = "Racion/1.1 (personal nutrition tracker; CI probe)"
    }
}
