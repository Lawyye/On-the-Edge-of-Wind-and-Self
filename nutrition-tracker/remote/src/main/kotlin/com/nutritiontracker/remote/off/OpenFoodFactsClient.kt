package com.nutritiontracker.remote.off

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
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

internal interface OffApi {
    @GET("cgi/search.pl")
    suspend fun search(
        @Query("search_terms") terms: String,
        @Query("page_size") pageSize: Int,
        @Query("fields") fields: String,
        @Query("search_simple") simple: Int = 1,
        @Query("action") action: String = "process",
        @Query("json") json: Int = 1,
    ): OffSearchResponse

    @GET("api/v2/product/{code}")
    suspend fun product(@Path("code") code: String, @Query("fields") fields: String): OffProductResponse
}

/** Новый поисковый сервис Open Food Facts (search-a-licious) — запасной вариант. */
internal interface OffSearchApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Query("page_size") pageSize: Int,
        @Query("fields") fields: String,
        @Query("langs") langs: String = "ru,en",
    ): OffSearchHits
}

@kotlinx.serialization.Serializable
internal data class OffSearchHits(val hits: List<OffProduct> = emptyList())

/**
 * Клиент Open Food Facts. Ключ не нужен; сервис просит указывать
 * User-Agent с названием приложения.
 */
class OpenFoodFactsClient(
    okHttpClient: OkHttpClient,
    userAgent: String,
    baseUrl: String = BASE_URL,
    searchBaseUrl: String = SEARCH_BASE_URL,
) {
    private val client = okHttpClient.newBuilder()
        .addInterceptor(Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
        })
        .build()

    private fun retrofit(url: String) = Retrofit.Builder()
        .baseUrl(url)
        .client(client)
        .addConverterFactory(RemoteJson.json.asConverterFactory("application/json".toMediaType()))
        .build()

    private val api: OffApi = retrofit(baseUrl).create(OffApi::class.java)
    private val searchApi: OffSearchApi = retrofit(searchBaseUrl).create(OffSearchApi::class.java)

    /**
     * Поиск по названию. Основной адрес — классический поиск Open Food Facts;
     * если он не отвечает, запрос повторяется в новом поисковом сервисе.
     */
    suspend fun search(query: String, pageSize: Int = 20): RemoteResult<List<RemoteFood>> {
        val primary = remoteCall(SOURCE) {
            api.search(query, pageSize, FIELDS).products.mapNotNull { p -> OffMapper.map(p)?.let { RemoteFood(it, true) } }
        }
        if (primary is RemoteResult.Success || (primary as RemoteResult.Failure).error.kind == RemoteErrorKind.NO_NETWORK) return primary
        val fallback = remoteCall(SOURCE) {
            searchApi.search(query, pageSize, FIELDS).hits.mapNotNull { p -> OffMapper.map(p)?.let { RemoteFood(it, true) } }
        }
        return if (fallback is RemoteResult.Success) fallback else primary
    }

    suspend fun product(code: String): RemoteResult<RemoteFood> = remoteCall(SOURCE) {
        val r = api.product(code, FIELDS)
        val product = r.product
        if (r.status != 1 || product == null) throw RemoteError(RemoteErrorKind.NOT_FOUND, SOURCE)
        val mapped = OffMapper.map(product) ?: throw RemoteError(RemoteErrorKind.NOT_FOUND, SOURCE)
        RemoteFood(mapped, true)
    }

    companion object {
        const val SOURCE = "Open Food Facts"
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val SEARCH_BASE_URL = "https://search.openfoodfacts.org/"
        const val FIELDS = "code,product_name,product_name_ru,product_name_en,generic_name,generic_name_ru,brands," +
            "categories,categories_tags,nutriments,serving_size,serving_quantity,serving_quantity_unit,quantity," +
            "product_quantity,product_quantity_unit,nutrition_data_per,last_modified_t"
    }
}
