package com.nutritiontracker.remote

import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object RemoteJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** Общий HTTP-клиент: без журналирования запросов, чтобы не сохранять поисковые запросы. */
    fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(RetryInterceptor())
        .build()

    /**
     * Повтор при временных ошибках сервера (500, 502, 503, 504): бесплатные API
     * иногда так отвечают при нагрузке. До двух повторов с паузой. Ответ 429
     * (лимит запросов) повторяется, только если сервер сам просит подождать
     * не больше 5 секунд (Retry-After): почасовой лимит повтором не обойти.
     */
    class RetryInterceptor(private val delaysMs: List<Long> = listOf(1_000, 2_500)) : okhttp3.Interceptor {
        private val retryable = setOf(500, 502, 503, 504)

        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
            var response = chain.proceed(chain.request())
            for (delay in delaysMs) {
                val retryAfter = response.header("Retry-After")?.trim()?.toLongOrNull()?.takeIf { it in 0..5 }?.times(1_000)
                val retry = response.code in retryable || (response.code == 429 && retryAfter != null)
                if (!retry) return response
                response.close()
                try {
                    Thread.sleep(retryAfter ?: delay)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw java.io.InterruptedIOException("Запрос отменён")
                }
                response = chain.proceed(chain.request())
            }
            return response
        }
    }
}
