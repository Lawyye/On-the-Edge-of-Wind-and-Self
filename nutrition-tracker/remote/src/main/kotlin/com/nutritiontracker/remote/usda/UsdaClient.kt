package com.nutritiontracker.remote.usda

import com.nutritiontracker.remote.RemoteError
import com.nutritiontracker.remote.RemoteErrorKind
import com.nutritiontracker.remote.RemoteFood
import com.nutritiontracker.remote.RemoteJson
import com.nutritiontracker.remote.RemoteResult
import com.nutritiontracker.remote.remoteCall
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

internal interface UsdaApi {
    /**
     * Поиск — POST с телом запроса. GET с типами данных через запятую сервер
     * USDA для части запросов отклоняет ответом 400 (проверено в CI, ноябрь 2026).
     */
    @POST("foods/search")
    suspend fun search(@Body body: UsdaSearchRequest): UsdaSearchResponse

    @GET("food/{fdcId}")
    suspend fun food(@Path("fdcId") fdcId: Long): UsdaFoodDetail

    @POST("foods")
    suspend fun foods(@Body body: UsdaFoodsRequest): List<UsdaFoodDetail>
}

/**
 * Клиент USDA FoodData Central. Ключ API передаётся функцией, чтобы его можно
 * было менять в настройках без пересоздания клиента. Без своего ключа
 * используется общий DEMO_KEY с низким лимитом запросов.
 */
class UsdaClient(
    okHttpClient: OkHttpClient,
    private val apiKeyProvider: () -> String,
    baseUrl: String = BASE_URL,
) {
    private val api: UsdaApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            okHttpClient.newBuilder()
                .addInterceptor(Interceptor { chain ->
                    val req = chain.request()
                    val url = req.url.newBuilder()
                        .addQueryParameter("api_key", apiKeyProvider().ifBlank { DEMO_KEY })
                        .build()
                    chain.proceed(req.newBuilder().url(url).build())
                })
                .build(),
        )
        .addConverterFactory(RemoteJson.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(UsdaApi::class.java)

    suspend fun search(query: String, pageSize: Int = 15, includeBranded: Boolean = false): RemoteResult<List<RemoteFood>> {
        val types = buildList {
            add("Foundation"); add("SR Legacy"); add("Survey (FNDDS)")
            if (includeBranded) add("Branded")
        }
        val first = remoteCall(SOURCE) { searchOnce(query, types, pageSize) }
        // Сервер иногда не справляется с большим ответом — вторая попытка с меньшей страницей.
        val retry = first is RemoteResult.Failure && first.error.kind == RemoteErrorKind.SERVER && pageSize > SMALL_PAGE
        return if (retry) remoteCall(SOURCE) { searchOnce(query, types, SMALL_PAGE) } else first
    }

    private suspend fun searchOnce(query: String, types: List<String>, pageSize: Int): List<RemoteFood> =
        api.search(UsdaSearchRequest(query, types, pageSize)).foods.map { RemoteFood(UsdaMapper.fromSearch(it), isFullRecord = false) }

    suspend fun details(fdcId: String): RemoteResult<RemoteFood> = remoteCall(SOURCE) {
        val id = fdcId.toLongOrNull() ?: throw RemoteError(RemoteErrorKind.NOT_FOUND, SOURCE)
        RemoteFood(UsdaMapper.fromDetail(api.food(id)), isFullRecord = true)
    }

    suspend fun detailsBatch(fdcIds: List<String>): RemoteResult<List<RemoteFood>> = remoteCall(SOURCE) {
        val ids = fdcIds.mapNotNull { it.toLongOrNull() }
        if (ids.isEmpty()) emptyList() else api.foods(UsdaFoodsRequest(ids)).map { RemoteFood(UsdaMapper.fromDetail(it), true) }
    }

    companion object {
        const val SOURCE = "USDA FoodData Central"
        const val BASE_URL = "https://api.nal.usda.gov/fdc/v1/"
        const val DEMO_KEY = "DEMO_KEY"
        private const val SMALL_PAGE = 8
    }
}
