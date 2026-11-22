package com.nutritiontracker.remote.off

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.core.model.SourceUnit
import com.nutritiontracker.core.model.UnitConversion
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import java.time.Instant
import java.time.ZoneOffset

/**
 * Перевод данных Open Food Facts в модель приложения.
 *
 * Поля «_100g» в Open Food Facts хранятся в граммах для всех веществ
 * (кроме энергии: «energy-kcal» — ккал, «energy»/«energy-kj» — кДж).
 * Для напитков значения отнесены к 100 мл.
 */
object OffMapper {
    private val keys: Map<Nutrient, List<String>> = linkedMapOf(
        Nutrient.PROTEIN to listOf("proteins"),
        Nutrient.FAT to listOf("fat"),
        Nutrient.CARBS to listOf("carbohydrates"),
        Nutrient.FIBER to listOf("fiber"),
        Nutrient.SUGARS to listOf("sugars"),
        Nutrient.ADDED_SUGARS to listOf("added-sugars"),
        Nutrient.SATURATED_FAT to listOf("saturated-fat"),
        Nutrient.MONOUNSATURATED_FAT to listOf("monounsaturated-fat"),
        Nutrient.POLYUNSATURATED_FAT to listOf("polyunsaturated-fat"),
        Nutrient.TRANS_FAT to listOf("trans-fat"),
        Nutrient.CHOLESTEROL to listOf("cholesterol"),
        Nutrient.VITAMIN_A to listOf("vitamin-a"),
        Nutrient.VITAMIN_B1 to listOf("vitamin-b1"),
        Nutrient.VITAMIN_B2 to listOf("vitamin-b2"),
        Nutrient.VITAMIN_B3 to listOf("vitamin-pp", "vitamin-b3"),
        Nutrient.VITAMIN_B5 to listOf("pantothenic-acid", "vitamin-b5"),
        Nutrient.VITAMIN_B6 to listOf("vitamin-b6"),
        Nutrient.VITAMIN_B7 to listOf("biotin", "vitamin-b7"),
        Nutrient.VITAMIN_B9 to listOf("vitamin-b9", "folates"),
        Nutrient.VITAMIN_B12 to listOf("vitamin-b12"),
        Nutrient.VITAMIN_C to listOf("vitamin-c"),
        Nutrient.VITAMIN_D to listOf("vitamin-d"),
        Nutrient.VITAMIN_E to listOf("vitamin-e"),
        Nutrient.VITAMIN_K to listOf("vitamin-k", "phylloquinone"),
        Nutrient.CALCIUM to listOf("calcium"),
        Nutrient.MAGNESIUM to listOf("magnesium"),
        Nutrient.POTASSIUM to listOf("potassium"),
        Nutrient.SODIUM to listOf("sodium"),
        Nutrient.PHOSPHORUS to listOf("phosphorus"),
        Nutrient.IRON to listOf("iron"),
        Nutrient.ZINC to listOf("zinc"),
        Nutrient.COPPER to listOf("copper"),
        Nutrient.MANGANESE to listOf("manganese"),
        Nutrient.SELENIUM to listOf("selenium"),
        Nutrient.IODINE to listOf("iodine"),
        Nutrient.CHLORIDE to listOf("chloride"),
    )

    private val labelBased = setOf(Nutrient.VITAMIN_A, Nutrient.VITAMIN_B9)

    internal fun number(e: JsonElement?): Double? = when (e) {
        null, JsonNull -> null
        is JsonPrimitive -> e.doubleOrNull ?: e.contentOrNull?.trim()?.replace(',', '.')?.toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    private fun text(e: JsonElement?): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ")
        else -> null
    }?.trim()?.takeIf { it.isNotEmpty() }

    fun basisOf(p: OffProduct): AmountUnit {
        val per = p.nutritionDataPer?.lowercase()?.replace(" ", "")
        val qUnit = p.productQuantityUnit?.lowercase()?.trim()
        val sUnit = p.servingQuantityUnit?.lowercase()?.trim()
        return if (per == "100ml" || qUnit == "ml" || (qUnit == null && sUnit == "ml")) AmountUnit.MILLILITER else AmountUnit.GRAM
    }

    fun map(p: OffProduct): FoodData? {
        val code = p.code?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val name = listOf(p.productNameRu, p.productName, p.genericNameRu, p.genericName, p.productNameEn)
            .firstNotNullOfOrNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } } ?: return null
        val n = p.nutriments ?: JsonObject(emptyMap())
        val notes = mutableListOf<String>()
        val values = LinkedHashMap<Nutrient, Double>()
        val lessThan = mutableListOf<Nutrient>()

        val kcal = number(n["energy-kcal_100g"])
        val kj = number(n["energy-kj_100g"]) ?: number(n["energy_100g"])
        when {
            kcal != null && kcal >= 0 -> values[Nutrient.ENERGY] = kcal
            kj != null && kj >= 0 -> {
                values[Nutrient.ENERGY] = UnitConversion.toCanonical(Nutrient.ENERGY, kj, SourceUnit.KJ)!!
                notes += "Энергия пересчитана из кДж (1 ккал = 4,184 кДж)"
            }
        }
        for ((nutrient, names) in keys) {
            for (k in names) {
                val v = number(n["${k}_100g"]) ?: continue
                if (v < 0) continue
                values[nutrient] = UnitConversion.toCanonical(nutrient, v, SourceUnit.G) ?: continue
                if ((n["${k}_modifier"] as? JsonPrimitive)?.contentOrNull == "<") lessThan += nutrient
                break
            }
        }
        if (lessThan.isNotEmpty()) {
            notes += "На этикетке указано «менее»: ${lessThan.joinToString(", ") { it.titleRu }} — сохранено верхнее значение"
        }
        if (labelBased.any { it in values }) {
            notes += "Витамины A и B9 приведены по этикетке и могут не совпадать с RAE/DFE"
        }
        val basis = basisOf(p)
        if (basis == AmountUnit.MILLILITER) notes += "Пищевая ценность указана на 100 мл"

        val servings = mutableListOf<ServingSize>()
        val servingQty = number(p.servingQuantity)
        val servingUnit = p.servingQuantityUnit?.lowercase()?.trim() ?: if (basis == AmountUnit.MILLILITER) "ml" else "g"
        if (servingQty != null && servingQty > 0 && unitMatches(servingUnit, basis)) {
            servings += ServingSize(
                label = p.servingSize?.trim()?.takeIf { it.isNotEmpty() }?.let { "Порция: $it" } ?: "Порция по этикетке",
                amount = servingQty,
                kind = ServingKind.PORTION,
                sourceNote = "Open Food Facts, этикетка",
            )
        }
        val packQty = number(p.productQuantity)
        val packUnit = p.productQuantityUnit?.lowercase()?.trim() ?: if (basis == AmountUnit.MILLILITER) "ml" else "g"
        if (packQty != null && packQty > 0 && unitMatches(packUnit, basis)) {
            servings += ServingSize(
                label = p.quantity?.trim()?.takeIf { it.isNotEmpty() }?.let { "Упаковка: $it" } ?: "Упаковка",
                amount = packQty,
                kind = ServingKind.PACKAGE,
                sourceNote = "Open Food Facts, масса нетто",
            )
        }
        val category = p.categories?.split(',')?.map { it.trim() }?.lastOrNull { it.isNotEmpty() && !it.contains(':') }
            ?: p.categories?.split(',')?.map { it.trim().substringAfter(':') }?.lastOrNull { it.isNotEmpty() }
        return FoodData(
            name = name,
            nameEn = p.productNameEn?.trim()?.takeIf { it.isNotEmpty() && it != name },
            brand = text(p.brands)?.split(',')?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
            category = category,
            source = FoodSource.OPEN_FOOD_FACTS,
            externalId = code,
            basis = basis,
            nutrients = Nutrients.of(values),
            servings = servings,
            sourceUpdated = p.lastModifiedT?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate().toString() },
            dataNotes = notes,
        )
    }

    private fun unitMatches(unit: String, basis: AmountUnit): Boolean = when (basis) {
        AmountUnit.GRAM -> unit == "g" || unit == "г"
        AmountUnit.MILLILITER -> unit == "ml" || unit == "мл"
    }
}
