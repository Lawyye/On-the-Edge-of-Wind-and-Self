package com.nutritiontracker.core.model

/**
 * Основа, к которой отнесена пищевая ценность продукта: 100 г или 100 мл.
 * В той же единице указывается съеденное количество. Граммы и миллилитры
 * между собой не пересчитываются — для этого нужна плотность, а её мы не знаем.
 */
enum class AmountUnit(val symbol: String, val per100Ru: String) {
    GRAM("г", "на 100 г"),
    MILLILITER("мл", "на 100 мл"),
}

/** Единицы, в которых данные приходят из внешних источников. */
enum class SourceUnit {
    KCAL, KJ, G, MG, UG, IU;

    companion object {
        /**
         * Распознаёт обозначение единицы из ответа API. Учитываются варианты
         * написания микрограмма: «µg» (знак микро), «μg» (греческая мю), «ug», «mcg».
         */
        fun parse(raw: String?): SourceUnit? {
            val s = raw?.trim()?.lowercase() ?: return null
            return when (s) {
                "kcal" -> KCAL
                "kj" -> KJ
                "g" -> G
                "mg" -> MG
                "µg", "μg", "ug", "mcg" -> UG
                "iu" -> IU
                else -> null
            }
        }
    }
}

object UnitConversion {
    /** Термохимическая калория: 1 ккал = 4,184 кДж (точное определение). */
    const val KJ_PER_KCAL = 4.184

    /** 1 мкг витамина D = 40 МЕ (точное определение для D2 и D3). */
    const val VITAMIN_D_IU_PER_UG = 40.0

    /**
     * Переводит значение из единицы источника в единицу хранения нутриента.
     * Возвращает null, если перевод невозможен без допущений (например, МЕ
     * витамина A в RAE: коэффициент зависит от формы витамина, которую источник
     * не сообщает). В таком случае значение считается неизвестным.
     */
    fun toCanonical(nutrient: Nutrient, value: Double, from: SourceUnit): Double? {
        if (!value.isFinite()) return null
        val target = nutrient.unit
        return when (from) {
            SourceUnit.KCAL -> if (target == NutrientUnit.KCAL) value else null
            SourceUnit.KJ -> if (target == NutrientUnit.KCAL) value / KJ_PER_KCAL else null
            SourceUnit.IU -> if (nutrient == Nutrient.VITAMIN_D) value / VITAMIN_D_IU_PER_UG else null
            SourceUnit.G, SourceUnit.MG, SourceUnit.UG -> {
                if (target == NutrientUnit.KCAL) return null
                val grams = when (from) {
                    SourceUnit.G -> value
                    SourceUnit.MG -> value / 1_000.0
                    else -> value / 1_000_000.0
                }
                massFromGrams(grams, target)
            }
        }
    }

    fun massFromGrams(grams: Double, target: NutrientUnit): Double = when (target) {
        NutrientUnit.G -> grams
        NutrientUnit.MG -> grams * 1_000.0
        NutrientUnit.UG -> grams * 1_000_000.0
        NutrientUnit.KCAL -> error("Энергия не является массой")
    }

    fun massToGrams(value: Double, unit: NutrientUnit): Double = when (unit) {
        NutrientUnit.G -> value
        NutrientUnit.MG -> value / 1_000.0
        NutrientUnit.UG -> value / 1_000_000.0
        NutrientUnit.KCAL -> error("Энергия не является массой")
    }

    /** Перевод между массовыми единицами нутриентов (г ↔ мг ↔ мкг). */
    fun convertMass(value: Double, from: NutrientUnit, to: NutrientUnit): Double =
        if (from == to) value else massFromGrams(massToGrams(value, from), to)
}
