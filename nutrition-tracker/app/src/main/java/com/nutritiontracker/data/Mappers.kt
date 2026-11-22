package com.nutritiontracker.data

import com.nutritiontracker.core.backup.ServingDto
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.core.text.FoodSearch
import com.nutritiontracker.data.db.FoodEntity
import com.nutritiontracker.data.db.FoodEntryEntity
import com.nutritiontracker.data.db.MealTemplateItemEntity
import com.nutritiontracker.data.db.RecipeIngredientEntity
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** JSON для колонок базы. */
object StorageJson {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val mapSer = MapSerializer(String.serializer(), Double.serializer())
    private val listSer = ListSerializer(String.serializer())
    private val servingsSer = ListSerializer(ServingDto.serializer())

    fun nutrients(n: Nutrients): String = json.encodeToString(mapSer, n.storageValues())
    fun incomplete(n: Nutrients): String = json.encodeToString(listSer, n.storageIncomplete())
    fun readNutrients(values: String, incomplete: String): Nutrients =
        Nutrients.fromStorage(json.decodeFromString(mapSer, values), json.decodeFromString(listSer, incomplete))

    fun strings(list: List<String>): String = json.encodeToString(listSer, list)
    fun readStrings(s: String): List<String> = if (s.isBlank()) emptyList() else json.decodeFromString(listSer, s)

    fun servings(list: List<ServingSize>): String =
        json.encodeToString(servingsSer, list.map { ServingDto(it.label, it.amount, it.kind.name, it.sourceNote) })

    fun readServings(s: String): List<ServingSize> =
        if (s.isBlank()) emptyList() else json.decodeFromString(servingsSer, s).mapNotNull { d ->
            val kind = ServingKind.entries.firstOrNull { it.name == d.kind } ?: return@mapNotNull null
            if (!d.amount.isFinite() || d.amount <= 0) null else ServingSize(d.label, d.amount, kind, d.sourceNote)
        }

    fun mapValues(s: String): Map<String, Double> = json.decodeFromString(mapSer, s)
}

/** Продукт из локальной базы. */
data class FoodItem(
    val id: Long,
    val data: FoodData,
    val aliases: List<String>,
    val detailsLoaded: Boolean,
    val recipeId: Long?,
    val fetchedAt: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val useCount: Int,
) {
    val name: String get() = data.name
    val basis: AmountUnit get() = data.basis
    val nutrients: Nutrients get() = data.nutrients
    val source: FoodSource get() = data.source
}

fun FoodEntity.toItem(): FoodItem = FoodItem(
    id = id,
    data = FoodData(
        name = name,
        nameEn = nameEn,
        brand = brand,
        category = category,
        source = FoodSource.fromKey(source) ?: FoodSource.USER,
        externalId = externalId,
        basis = AmountUnit.entries.firstOrNull { it.name == basis } ?: AmountUnit.GRAM,
        nutrients = StorageJson.readNutrients(nutrients, incompleteNutrients),
        servings = StorageJson.readServings(servings),
        sourceUpdated = sourceUpdated,
        dataNotes = StorageJson.readStrings(dataNotes),
    ),
    aliases = StorageJson.readStrings(aliases),
    detailsLoaded = detailsLoaded,
    recipeId = recipeId,
    fetchedAt = fetchedAt,
    createdAt = createdAt,
    updatedAt = updatedAt,
    useCount = useCount,
)

fun buildSearchIndex(data: FoodData, aliases: List<String>): String =
    FoodSearch.buildIndex(*(listOf(data.name, data.nameEn, data.brand, data.category) + aliases).toTypedArray())

fun newFoodEntity(
    data: FoodData,
    aliases: List<String>,
    detailsLoaded: Boolean,
    now: Long,
    fetchedAt: Long?,
    recipeId: Long? = null,
): FoodEntity = FoodEntity(
    name = data.name,
    nameEn = data.nameEn,
    aliases = StorageJson.strings(aliases),
    brand = data.brand,
    category = data.category,
    source = data.source.key,
    externalId = data.externalId,
    basis = data.basis.name,
    nutrients = StorageJson.nutrients(data.nutrients),
    incompleteNutrients = StorageJson.incomplete(data.nutrients),
    servings = StorageJson.servings(data.servings),
    sourceUpdated = data.sourceUpdated,
    dataNotes = StorageJson.strings(data.dataNotes),
    recipeId = recipeId,
    detailsLoaded = detailsLoaded,
    fetchedAt = fetchedAt,
    createdAt = now,
    updatedAt = now,
    useCount = 0,
    lastUsedAt = null,
    searchIndex = buildSearchIndex(data, aliases),
)

/** Обновляет данные продукта, сохраняя идентификатор, историю использования и альтернативные названия. */
fun FoodEntity.withData(data: FoodData, detailsLoaded: Boolean, now: Long, fetchedAt: Long?, aliasesOverride: List<String>? = null): FoodEntity {
    val aliasesList = aliasesOverride ?: StorageJson.readStrings(aliases)
    return copy(
        name = data.name,
        nameEn = data.nameEn,
        aliases = StorageJson.strings(aliasesList),
        brand = data.brand,
        category = data.category,
        source = data.source.key,
        externalId = data.externalId,
        basis = data.basis.name,
        nutrients = StorageJson.nutrients(data.nutrients),
        incompleteNutrients = StorageJson.incomplete(data.nutrients),
        servings = StorageJson.servings(data.servings),
        sourceUpdated = data.sourceUpdated,
        dataNotes = StorageJson.strings(data.dataNotes),
        detailsLoaded = detailsLoaded,
        fetchedAt = fetchedAt,
        updatedAt = now,
        searchIndex = buildSearchIndex(data, aliasesList),
    )
}

/** Запись дневника со снимком пищевой ценности. */
data class DiaryEntry(
    val id: Long,
    val foodId: Long?,
    val date: LocalDate,
    val meal: MealType,
    val amount: Double,
    val unit: AmountUnit,
    val foodName: String,
    val brand: String?,
    val source: FoodSource,
    val per100: Nutrients,
    val servingLabel: String?,
    val createdAt: Long,
) {
    val eaten: Nutrients by lazy { com.nutritiontracker.core.calc.NutrientMath.forAmount(per100, amount) }
}

fun FoodEntryEntity.toEntry(): DiaryEntry = DiaryEntry(
    id = id,
    foodId = foodId,
    date = LocalDate.ofEpochDay(epochDay),
    meal = MealType.fromKey(mealType) ?: MealType.SNACK,
    amount = amount,
    unit = AmountUnit.entries.firstOrNull { it.name == unit } ?: AmountUnit.GRAM,
    foodName = foodName,
    brand = brand,
    source = FoodSource.fromKey(source) ?: FoodSource.USER,
    per100 = StorageJson.readNutrients(nutrientsPer100, incompleteNutrients),
    servingLabel = servingLabel,
    createdAt = createdAt,
)

data class RecipeIngredient(
    val id: Long,
    val foodId: Long?,
    val name: String,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
)

fun RecipeIngredientEntity.toIngredient() = RecipeIngredient(
    id = id,
    foodId = foodId,
    name = name,
    amount = amount,
    unit = AmountUnit.entries.firstOrNull { it.name == unit } ?: AmountUnit.GRAM,
    per100 = StorageJson.readNutrients(nutrientsPer100, incompleteNutrients),
)

data class TemplateItem(
    val id: Long,
    val foodId: Long?,
    val name: String,
    val brand: String?,
    val source: FoodSource,
    val amount: Double,
    val unit: AmountUnit,
    val per100: Nutrients,
)

fun MealTemplateItemEntity.toTemplateItem() = TemplateItem(
    id = id,
    foodId = foodId,
    name = name,
    brand = brand,
    source = FoodSource.fromKey(source) ?: FoodSource.USER,
    amount = amount,
    unit = AmountUnit.entries.firstOrNull { it.name == unit } ?: AmountUnit.GRAM,
    per100 = StorageJson.readNutrients(nutrientsPer100, incompleteNutrients),
)
