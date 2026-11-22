package com.nutritiontracker.remote

import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
                "HTTP ${r.code}$limit bytes=${body.length} ${body.take(if (r.code == 200) 200 else 600).replace('\n', ' ')}"
            }
        } catch (e: Exception) {
            "EXCEPTION ${e::class.simpleName}: ${e.message}"
        }
        println("LIVE raw  [$label] ${(System.nanoTime() - started) / 1_000_000} ms: $line")
    }

    private fun rawPost(label: String, url: String, json: String) {
        val started = System.nanoTime()
        val line = try {
            val body = json.toRequestBody("application/json".toMediaType())
            http.newCall(Request.Builder().url(url).post(body).header("User-Agent", UA).build()).execute().use { r ->
                val text = r.body.string()
                "HTTP ${r.code} bytes=${text.length} ${text.take(300).replace('\n', ' ')}"
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
        val base = "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=DEMO_KEY"
        val all = "Foundation,SR%20Legacy,Survey%20%28FNDDS%29"
        raw("USDA A app cottage", "$base&query=cottage%20cheese&dataType=$all&pageSize=25&pageNumber=1")
        raw("USDA B no FNDDS cottage", "$base&query=cottage%20cheese&dataType=Foundation,SR%20Legacy&pageSize=25")
        raw("USDA C FNDDS only cottage", "$base&query=cottage%20cheese&dataType=Survey%20%28FNDDS%29&pageSize=25")
        raw("USDA D page10 cottage", "$base&query=cottage%20cheese&dataType=$all&pageSize=10")
        raw("USDA F repeated cottage", "$base&query=cottage%20cheese&dataType=Foundation&dataType=SR%20Legacy&dataType=Survey%20%28FNDDS%29&pageSize=25")
        rawPost("USDA E POST cottage", "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=DEMO_KEY",
            """{"query":"cottage cheese","dataType":["Foundation","SR Legacy","Survey (FNDDS)"],"pageSize":25}""")
        raw("OFF search-a-licious app fields", "https://search.openfoodfacts.org/search?q=%D1%82%D0%B2%D0%BE%D1%80%D0%BE%D0%B3&page_size=3&langs=ru,en&fields=" + OpenFoodFactsClient.FIELDS)

        val usda = UsdaClient(http, { "" })
        val off = OpenFoodFactsClient(http, UA)
        for (q in listOf("cottage cheese", "rice")) report("USDA '$q'", usda.search(q, 25))
        for (q in listOf("творог", "спагетти", "оливковое масло")) report("OFF '$q'", off.search(q, 10))

        // Несколько запросов OFF одновременно — как голосовой ввод из трёх позиций.
        listOf("курица", "рис", "гречка", "кефир", "овсянка").map { q -> async(kotlinx.coroutines.Dispatchers.IO) { q to off.search(q, 10) } }
            .awaitAll().forEach { (q, r) -> report("parallel '$q'", r) }
    }

    private companion object {
        const val UA = "Racion/1.1 (personal nutrition tracker; CI probe)"
    }
}
