package com.nutritiontracker.remote.usda

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.core.model.SourceUnit
import com.nutritiontracker.core.model.UnitConversion
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Перевод данных USDA в модель приложения.
 *
 * Нутриенты сопоставляются по стабильным номерам USDA (nutrient number).
 * Если в ответе нет нутриента, он остаётся неизвестным. Единицы переводятся
 * только точными коэффициентами; МЕ витамина A в RAE не переводятся.
 */
object UsdaMapper {
    /** Номера USDA в порядке предпочтения для каждого нутриента. */
    private val numbers: Map<Nutrient, List<String>> = linkedMapOf(
        // 208 — классическая энергия (SR Legacy, FNDDS, Branded);
        // 958/957 — энергия по специфическим/общим коэффициентам Этуотера (Foundation).
        Nutrient.ENERGY to listOf("208", "958", "957"),
        Nutrient.PROTEIN to listOf("203"),
        Nutrient.FAT to listOf("204", "298"),
        Nutrient.CARBS to listOf("205", "205.2"),
        Nutrient.FIBER to listOf("291", "293"),
        Nutrient.SUGARS to listOf("269", "269.3"),
        Nutrient.ADDED_SUGARS to listOf("539"),
        Nutrient.SATURATED_FAT to listOf("606"),
        Nutrient.MONOUNSATURATED_FAT to listOf("645"),
        Nutrient.POLYUNSATURATED_FAT to listOf("646"),
        Nutrient.TRANS_FAT to listOf("605"),
        Nutrient.CHOLESTEROL to listOf("601"),
        Nutrient.VITAMIN_A to listOf("320"),
        Nutrient.VITAMIN_B1 to listOf("404"),
        Nutrient.VITAMIN_B2 to listOf("405"),
        Nutrient.VITAMIN_B3 to listOf("406"),
        Nutrient.VITAMIN_B5 to listOf("410"),
        Nutrient.VITAMIN_B6 to listOf("415"),
        Nutrient.VITAMIN_B7 to listOf("416"),
        Nutrient.VITAMIN_B9 to listOf("435"),
        Nutrient.VITAMIN_B12 to listOf("418"),
        Nutrient.VITAMIN_C to listOf("401"),
        Nutrient.VITAMIN_D to listOf("328", "324"),
        Nutrient.VITAMIN_E to listOf("323"),
        Nutrient.VITAMIN_K to listOf("430"),
        Nutrient.CALCIUM to listOf("301"),
        Nutrient.MAGNESIUM to listOf("304"),
        Nutrient.POTASSIUM to listOf("306"),
        Nutrient.SODIUM to listOf("307"),
        Nutrient.PHOSPHORUS to listOf("305"),
        Nutrient.IRON to listOf("303"),
        Nutrient.ZINC to listOf("309"),
        Nutrient.COPPER to listOf("312"),
        Nutrient.MANGANESE to listOf("315"),
        Nutrient.SELENIUM to listOf("317"),
        Nutrient.IODINE to listOf("314"),
        Nutrient.ALCOHOL to listOf("221"),
    )

    private const val ENERGY_KJ = "268"
    private const val FOLATE_TOTAL = "417"
    private const val FOLIC_ACID = "431"
    private const val FOLATE_FOOD = "432"

    /** Значение источника: номер → (величина, единица). */
    private data class Raw(val value: Double, val unit: SourceUnit?)

    fun sourceFor(dataType: String?): FoodSource = when (dataType?.trim()?.lowercase()) {
        "foundation" -> FoodSource.USDA_FOUNDATION
        "sr legacy" -> FoodSource.USDA_SR_LEGACY
        "survey (fndds)" -> FoodSource.USDA_SURVEY
        "branded" -> FoodSource.USDA_BRANDED
        else -> FoodSource.USDA_SR_LEGACY
    }

    private fun basisFor(servingUnit: String?): AmountUnit =
        when (servingUnit?.trim()?.lowercase()) {
            "ml", "mlt" -> AmountUnit.MILLILITER
            else -> AmountUnit.GRAM
        }

    private fun buildNutrients(raw: Map<String, Raw>, notes: MutableList<String>): Nutrients {
        val values = LinkedHashMap<Nutrient, Double>()
        for ((nutrient, nums) in numbers) {
            for (num in nums) {
                val r = raw[num] ?: continue
                val unit = r.unit ?: continue
                val v = UnitConversion.toCanonical(nutrient, r.value, unit) ?: continue
                if (!v.isFinite() || v < 0) continue
                values[nutrient] = v
                if (nutrient == Nutrient.ENERGY && num == "958") notes += "Энергия: USDA, расчёт по специфическим коэффициентам Этуотера"
                if (nutrient == Nutrient.ENERGY && num == "957") notes += "Энергия: USDA, расчёт по общим коэффициентам Этуотера"
                if (nutrient == Nutrient.VITAMIN_D && num == "324") notes += "Витамин D пересчитан из МЕ (1 мкг = 40 МЕ)"
                break
            }
        }
        if (Nutrient.ENERGY !in values) {
            raw[ENERGY_KJ]?.let { r ->
                val v = r.unit?.let { UnitConversion.toCanonical(Nutrient.ENERGY, r.value, it) }
                if (v != null && v >= 0) {
                    values[Nutrient.ENERGY] = v
                    notes += "Энергия пересчитана из кДж (1 ккал = 4,184 кДж)"
                }
            }
        }
        // Фолат в DFE по определению: пищевой фолат + 1,7 × фолиевая кислота.
        if (Nutrient.VITAMIN_B9 !in values) {
            val folicAcid = raw[FOLIC_ACID]?.let { r -> r.unit?.let { UnitConversion.toCanonical(Nutrient.VITAMIN_B9, r.value, it) } }
            val foodFolate = raw[FOLATE_FOOD]?.let { r -> r.unit?.let { UnitConversion.toCanonical(Nutrient.VITAMIN_B9, r.value, it) } }
            val total = raw[FOLATE_TOTAL]?.let { r -> r.unit?.let { UnitConversion.toCanonical(Nutrient.VITAMIN_B9, r.value, it) } }
            val dfe = when {
                folicAcid != null && foodFolate != null -> foodFolate + 1.7 * folicAcid
                folicAcid != null && total != null && total >= folicAcid -> (total - folicAcid) + 1.7 * folicAcid
                else -> null
            }
            if (dfe != null) {
                values[Nutrient.VITAMIN_B9] = dfe
                notes += "Фолат (DFE) рассчитан по определению: пищевой фолат + 1,7 × фолиевая кислота"
            }
        }
        return Nutrients.of(values)
    }

    private fun categoryName(detail: UsdaFoodDetail): String? {
        detail.brandedFoodCategory?.let { return it }
        val cat = detail.foodCategory ?: detail.wweiaFoodCategory ?: return null
        return when (cat) {
            is JsonPrimitive -> cat.contentOrNull
            is JsonObject -> (cat["description"] ?: cat["wweiaFoodCategoryDescription"])?.jsonPrimitive?.contentOrNull
            else -> null
        }
    }

    fun fromSearch(item: UsdaSearchFood): FoodData {
        val raw = item.foodNutrients.mapNotNull { n ->
            val num = n.nutrientNumber ?: return@mapNotNull null
            val v = n.value ?: return@mapNotNull null
            num to Raw(v, SourceUnit.parse(n.unitName))
        }.toMap()
        val notes = mutableListOf<String>()
        val source = sourceFor(item.dataType)
        val basis = if (source == FoodSource.USDA_BRANDED) basisFor(item.servingSizeUnit) else AmountUnit.GRAM
        val servings = brandedServing(item.servingSize, item.servingSizeUnit, item.householdServingFullText, basis)
        return FoodData(
            name = item.description.trim(),
            nameEn = item.description.trim(),
            brand = (item.brandName ?: item.brandOwner)?.trim()?.takeIf { it.isNotEmpty() },
            category = item.foodCategory,
            source = source,
            externalId = item.fdcId.toString(),
            basis = basis,
            nutrients = buildNutrients(raw, notes),
            servings = servings,
            sourceUpdated = item.publishedDate,
            dataNotes = notes,
        )
    }

    fun fromDetail(detail: UsdaFoodDetail): FoodData {
        val raw = detail.foodNutrients.mapNotNull { n ->
            val num = n.nutrient?.number ?: return@mapNotNull null
            val v = n.amount ?: return@mapNotNull null
            num to Raw(v, SourceUnit.parse(n.nutrient.unitName))
        }.toMap()
        val notes = mutableListOf<String>()
        val source = sourceFor(detail.dataType)
        val basis = if (source == FoodSource.USDA_BRANDED) basisFor(detail.servingSizeUnit) else AmountUnit.GRAM
        val servings = if (source == FoodSource.USDA_BRANDED) {
            brandedServing(detail.servingSize, detail.servingSizeUnit, detail.householdServingFullText, basis)
        } else {
            portions(detail.foodPortions)
        }
        return FoodData(
            name = detail.description.trim(),
            nameEn = detail.description.trim(),
            brand = (detail.brandName ?: detail.brandOwner)?.trim()?.takeIf { it.isNotEmpty() },
            category = categoryName(detail),
            source = source,
            externalId = detail.fdcId.toString(),
            basis = basis,
            nutrients = buildNutrients(raw, notes),
            servings = servings,
            sourceUpdated = detail.modifiedDate ?: detail.publicationDate,
            dataNotes = notes,
        )
    }

    private fun brandedServing(size: Double?, unit: String?, household: String?, basis: AmountUnit): List<ServingSize> {
        if (size == null || !size.isFinite() || size <= 0) return emptyList()
        if (basisFor(unit) != basis) return emptyList()
        val label = household?.trim()?.takeIf { it.isNotEmpty() }?.let { "Порция: $it" } ?: "Порция по этикетке"
        return listOf(ServingSize(label, size, ServingKind.PORTION, "USDA Branded, этикетка"))
    }

    private val pieceWords = listOf(
        "medium", "large", "small", "extra small", "extra large", "whole", "each", "piece", "fruit",
        "egg", "item", "slice", "patty", "breast", "thigh", "drumstick", "wing", "leg", "fillet", "steak",
        "bar", "cookie", "muffin", "bun", "roll", "link", "sausage", "stalk", "clove", "head", "ear", "potato",
    )
    private val portionWords = listOf("racc", "nlea serving", "serving")
    private val householdWords = listOf("cup", "tbsp", "tablespoon", "tsp", "teaspoon", "oz", "lb", "fl oz", "pint", "quart", "ml", "liter")

    /** Справочные порции USDA: вес одной единицы, вид определяется по описанию. */
    fun portions(portions: List<UsdaPortion>): List<ServingSize> {
        val result = mutableListOf<Pair<ServingSize, Int>>()
        for (p in portions.sortedBy { it.sequenceNumber ?: Int.MAX_VALUE }) {
            val grams = p.gramWeight ?: continue
            if (!grams.isFinite() || grams <= 0) continue
            val count = p.amount?.takeIf { it.isFinite() && it > 0 } ?: 1.0
            val unitName = p.measureUnit?.name?.trim()?.takeIf { it.isNotEmpty() && !it.equals("undetermined", true) }
            val desc = listOfNotNull(
                p.portionDescription?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Quantity not specified", true) },
                unitName,
                p.modifier?.trim()?.takeIf { it.isNotEmpty() },
            ).distinct().joinToString(" ")
            if (desc.isEmpty()) continue
            val lower = desc.lowercase()
            val kind = when {
                portionWords.any { lower.contains(it) } -> ServingKind.PORTION
                householdWords.any { Regex("\\b${Regex.escape(it)}\\b").containsMatchIn(lower) } -> ServingKind.HOUSEHOLD
                pieceWords.any { lower.contains(it) } -> ServingKind.PIECE
                else -> ServingKind.HOUSEHOLD
            }
            val label = if (lower.first().isDigit()) desc else "${formatCount(count)} $desc"
            val perUnit = if (kind == ServingKind.PIECE) grams / count else grams
            val serving = ServingSize(
                label = if (kind == ServingKind.PIECE && count != 1.0) "1 ${desc.trim()}" else label,
                amount = perUnit,
                kind = kind,
                sourceNote = "USDA",
            )
            // «medium» — наиболее типичная штука; она идёт первой среди штук.
            val priority = when {
                kind == ServingKind.PIECE && lower.contains("medium") -> 0
                kind == ServingKind.PIECE && !lower.contains("small") && !lower.contains("large") -> 1
                kind == ServingKind.PIECE -> 2
                kind == ServingKind.PORTION && lower.contains("racc") -> 3
                kind == ServingKind.PORTION -> 4
                else -> 5
            }
            result += serving to priority
        }
        return result.sortedBy { it.second }.map { it.first }.distinctBy { it.label to it.amount }
    }

    private fun formatCount(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
}
