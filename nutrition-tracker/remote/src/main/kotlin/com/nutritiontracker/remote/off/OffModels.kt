package com.nutritiontracker.remote.off

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class OffSearchResponse(
    val count: Int = 0,
    val page: Int = 1,
    @SerialName("page_size") val pageSize: Int = 0,
    val products: List<OffProduct> = emptyList(),
)

@Serializable
data class OffProductResponse(
    val status: Int = 0,
    val code: String? = null,
    val product: OffProduct? = null,
)

/**
 * Продукт Open Food Facts. Числовые поля иногда приходят строками,
 * поэтому они читаются как JsonElement.
 */
@Serializable
data class OffProduct(
    val code: String? = null,
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_ru") val productNameRu: String? = null,
    @SerialName("product_name_en") val productNameEn: String? = null,
    @SerialName("generic_name") val genericName: String? = null,
    @SerialName("generic_name_ru") val genericNameRu: String? = null,
    val brands: JsonElement? = null,
    val categories: String? = null,
    @SerialName("categories_tags") val categoriesTags: List<String>? = null,
    val nutriments: JsonObject? = null,
    @SerialName("serving_size") val servingSize: String? = null,
    @SerialName("serving_quantity") val servingQuantity: JsonElement? = null,
    @SerialName("serving_quantity_unit") val servingQuantityUnit: String? = null,
    val quantity: String? = null,
    @SerialName("product_quantity") val productQuantity: JsonElement? = null,
    @SerialName("product_quantity_unit") val productQuantityUnit: String? = null,
    @SerialName("nutrition_data_per") val nutritionDataPer: String? = null,
    @SerialName("last_modified_t") val lastModifiedT: Long? = null,
)
