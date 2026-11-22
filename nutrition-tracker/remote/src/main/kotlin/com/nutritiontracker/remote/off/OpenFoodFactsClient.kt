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

/**
 * Клиент Open Food Facts. Ключ не нужен; сервис просит указывать
 * User-Agent с названием приложения.
 */
class OpenFoodFactsClient(
    okHttpClient: OkHttpClient,
    userAgent: String,
    baseUrl: String = BASE_URL,
) {
    private val api: OffApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(
            okHttpClient.newBuilder()
                .addInterceptor(Interceptor { chain ->
                    chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
                })
                .build(),
        )
        .addConverterFactory(RemoteJson.json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OffApi::class.java)

    suspend fun search(query: String, pageSize: Int = 20): RemoteResult<List<RemoteFood>> = remoteCall(SOURCE) {
        api.search(query, pageSize, FIELDS).products.mapNotNull { p -> OffMapper.map(p)?.let { RemoteFood(it, true) } }
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
        const val FIELDS = "code,product_name,product_name_ru,product_name_en,generic_name,generic_name_ru,brands," +
            "categories,categories_tags,nutriments,serving_size,serving_quantity,serving_quantity_unit,quantity," +
            "product_quantity,product_quantity_unit,nutrition_data_per,last_modified_t"
    }
}
