package com.nutritiontracker.core.norms

import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.model.Nutrient

enum class NormKind(val titleRu: String, val shortRu: String) {
    RDA("Рекомендуемая норма потребления (RDA)", "RDA"),
    AI("Адекватный уровень потребления (AI)", "AI"),
    RDA_PER_KG("Рекомендуемая норма (RDA) на кг массы тела", "RDA"),
    ENERGY_SHARE("Доля от калорийности", "% ккал"),
    USER("Ваша цель", "своя"),
}

enum class MaximumKind(val titleRu: String, val shortRu: String) {
    UL("Верхний допустимый уровень потребления (UL)", "UL"),
    CDRR("Уровень снижения риска хронических заболеваний (CDRR): выше него рекомендуют снижать потребление", "CDRR"),
    DGA_LIMIT("Не более 10% калорийности (Dietary Guidelines for Americans)", "≤10% ккал"),
    USER("Ваш предел", "свой"),
}

/**
 * Ориентир для одного нутриента. Все значения — в единице хранения нутриента.
 *
 * @property target к чему стремиться (RDA/AI); null — числовой нормы нет.
 * @property maximum выше чего сообщается о превышении; null — предел не
 *   установлен или не применим к данным дневника (см. [noteRu]).
 */
data class NutrientNorm(
    val nutrient: Nutrient,
    val target: Double?,
    val kind: NormKind?,
    val maximum: Double?,
    val maximumKind: MaximumKind?,
    val sourceRu: String,
    val noteRu: String? = null,
)

enum class LifeStage(val titleRu: String) {
    Y14_18("14–18 лет"),
    Y19_30("19–30 лет"),
    Y31_50("31–50 лет"),
    Y51_70("51–70 лет"),
    Y71_PLUS("71 год и старше"),
    ;

    companion object {
        fun forAge(age: Int): LifeStage = when {
            age <= 18 -> Y14_18
            age <= 30 -> Y19_30
            age <= 50 -> Y31_50
            age <= 70 -> Y51_70
            else -> Y71_PLUS
        }
    }
}

/**
 * Справочные нормы потребления NASEM (Dietary Reference Intakes) для
 * здоровых людей по полу и возрасту. Значения сверены со сводными таблицами
 * NIH Office of Dietary Supplements. Нормы для беременности и кормления
 * грудью не поддерживаются.
 *
 * Это популяционные ориентиры, а не индивидуальные медицинские назначения.
 */
object DriNorms {
    const val SYSTEM_TITLE = "NASEM Dietary Reference Intakes (США/Канада)"
    const val SYSTEM_DESCRIPTION =
        "Нормы Национальных академий наук, инженерии и медицины США (DRI), сводные таблицы NIH Office of Dietary Supplements. " +
            "Это ориентиры для здоровых людей, а не медицинское назначение. Нормы для беременности и кормления грудью не учитываются."

    private const val SRC_DRI = "NASEM DRI"
    private const val SRC_DRI_2019 = "NASEM DRI для натрия и калия (2019)"
    private const val SRC_DRI_2011 = "NASEM DRI для кальция и витамина D (2011)"
    private const val SRC_MACRO = "NASEM DRI для энергии и макронутриентов (2005)"
    private const val SRC_DGA = "Dietary Guidelines for Americans 2020–2025"

    private const val UL_SUPPLEMENT_ONLY =
        "Верхний предел NASEM для этого вещества относится только к добавкам и обогащённым продуктам, поэтому по дневнику питания превышение не оценивается."

    /** Значения по возрастным группам в порядке [LifeStage]: 14–18, 19–30, 31–50, 51–70, 71+. */
    private class ByAge(vararg v: Double) {
        private val values = v
        init { require(values.size == 5) }
        operator fun get(stage: LifeStage) = values[stage.ordinal]
    }

    private fun same(v: Double) = ByAge(v, v, v, v, v)

    private data class Row(
        val male: ByAge,
        val female: ByAge,
        val kind: NormKind,
        val ul: ByAge? = null,
        val maximumKind: MaximumKind? = if (ul != null) MaximumKind.UL else null,
        val source: String = SRC_DRI,
        val note: String? = null,
    )

    private val micro: Map<Nutrient, Row> = mapOf(
        Nutrient.VITAMIN_A to Row(
            same(900.0), same(700.0), NormKind.RDA,
            note = "Верхний предел NASEM для витамина A относится только к готовому ретинолу, а не к сумме с каротиноидами, поэтому по общему RAE превышение не оценивается.",
        ),
        Nutrient.VITAMIN_C to Row(
            ByAge(75.0, 90.0, 90.0, 90.0, 90.0), ByAge(65.0, 75.0, 75.0, 75.0, 75.0), NormKind.RDA,
            ul = ByAge(1800.0, 2000.0, 2000.0, 2000.0, 2000.0),
            note = "Курящим NASEM рекомендует на 35 мг больше.",
        ),
        Nutrient.VITAMIN_D to Row(
            ByAge(15.0, 15.0, 15.0, 15.0, 20.0), ByAge(15.0, 15.0, 15.0, 15.0, 20.0), NormKind.RDA,
            ul = same(100.0), source = SRC_DRI_2011,
            note = "Витамин D образуется и в коже под действием солнца; дневник питания учитывает только пищу.",
        ),
        Nutrient.VITAMIN_E to Row(same(15.0), same(15.0), NormKind.RDA, note = UL_SUPPLEMENT_ONLY),
        Nutrient.VITAMIN_K to Row(
            ByAge(75.0, 120.0, 120.0, 120.0, 120.0), ByAge(75.0, 90.0, 90.0, 90.0, 90.0), NormKind.AI,
        ),
        Nutrient.VITAMIN_B1 to Row(same(1.2), ByAge(1.0, 1.1, 1.1, 1.1, 1.1), NormKind.RDA),
        Nutrient.VITAMIN_B2 to Row(same(1.3), ByAge(1.0, 1.1, 1.1, 1.1, 1.1), NormKind.RDA),
        Nutrient.VITAMIN_B3 to Row(same(16.0), same(14.0), NormKind.RDA, note = UL_SUPPLEMENT_ONLY),
        Nutrient.VITAMIN_B5 to Row(same(5.0), same(5.0), NormKind.AI),
        Nutrient.VITAMIN_B6 to Row(
            ByAge(1.3, 1.3, 1.3, 1.7, 1.7), ByAge(1.2, 1.3, 1.3, 1.5, 1.5), NormKind.RDA,
            ul = ByAge(80.0, 100.0, 100.0, 100.0, 100.0),
        ),
        Nutrient.VITAMIN_B7 to Row(ByAge(25.0, 30.0, 30.0, 30.0, 30.0), ByAge(25.0, 30.0, 30.0, 30.0, 30.0), NormKind.AI),
        Nutrient.VITAMIN_B9 to Row(same(400.0), same(400.0), NormKind.RDA, note = UL_SUPPLEMENT_ONLY),
        Nutrient.VITAMIN_B12 to Row(same(2.4), same(2.4), NormKind.RDA),

        Nutrient.CALCIUM to Row(
            ByAge(1300.0, 1000.0, 1000.0, 1000.0, 1200.0), ByAge(1300.0, 1000.0, 1000.0, 1200.0, 1200.0), NormKind.RDA,
            ul = ByAge(3000.0, 2500.0, 2500.0, 2000.0, 2000.0), source = SRC_DRI_2011,
        ),
        Nutrient.MAGNESIUM to Row(
            ByAge(410.0, 400.0, 420.0, 420.0, 420.0), ByAge(360.0, 310.0, 320.0, 320.0, 320.0), NormKind.RDA,
            note = UL_SUPPLEMENT_ONLY,
        ),
        Nutrient.PHOSPHORUS to Row(
            ByAge(1250.0, 700.0, 700.0, 700.0, 700.0), ByAge(1250.0, 700.0, 700.0, 700.0, 700.0), NormKind.RDA,
            ul = ByAge(4000.0, 4000.0, 4000.0, 4000.0, 3000.0),
        ),
        Nutrient.IRON to Row(
            ByAge(11.0, 8.0, 8.0, 8.0, 8.0), ByAge(15.0, 18.0, 18.0, 8.0, 8.0), NormKind.RDA,
            ul = same(45.0),
        ),
        Nutrient.ZINC to Row(
            same(11.0), ByAge(9.0, 8.0, 8.0, 8.0, 8.0), NormKind.RDA,
            ul = ByAge(34.0, 40.0, 40.0, 40.0, 40.0),
        ),
        Nutrient.COPPER to Row(
            ByAge(0.89, 0.9, 0.9, 0.9, 0.9), ByAge(0.89, 0.9, 0.9, 0.9, 0.9), NormKind.RDA,
            ul = ByAge(8.0, 10.0, 10.0, 10.0, 10.0),
        ),
        Nutrient.MANGANESE to Row(
            ByAge(2.2, 2.3, 2.3, 2.3, 2.3), ByAge(1.6, 1.8, 1.8, 1.8, 1.8), NormKind.AI,
            ul = ByAge(9.0, 11.0, 11.0, 11.0, 11.0),
        ),
        Nutrient.SELENIUM to Row(same(55.0), same(55.0), NormKind.RDA, ul = same(400.0)),
        Nutrient.IODINE to Row(
            same(150.0), same(150.0), NormKind.RDA,
            ul = ByAge(900.0, 1100.0, 1100.0, 1100.0, 1100.0),
            note = "Основной источник йода — йодированная соль; в базах продуктов он указан редко.",
        ),
        Nutrient.POTASSIUM to Row(
            ByAge(3000.0, 3400.0, 3400.0, 3400.0, 3400.0), ByAge(2300.0, 2600.0, 2600.0, 2600.0, 2600.0), NormKind.AI,
            source = SRC_DRI_2019,
        ),
        Nutrient.SODIUM to Row(
            same(1500.0), same(1500.0), NormKind.AI,
            ul = same(2300.0), maximumKind = MaximumKind.CDRR, source = SRC_DRI_2019,
        ),
        Nutrient.CHLORIDE to Row(
            ByAge(2300.0, 2300.0, 2300.0, 2000.0, 1800.0), ByAge(2300.0, 2300.0, 2300.0, 2000.0, 1800.0), NormKind.AI,
            ul = same(3600.0),
        ),
        Nutrient.FIBER to Row(
            ByAge(38.0, 38.0, 38.0, 30.0, 30.0), ByAge(26.0, 25.0, 25.0, 21.0, 21.0), NormKind.AI,
            source = SRC_MACRO,
        ),
        Nutrient.CARBS to Row(
            same(130.0), same(130.0), NormKind.RDA, source = SRC_MACRO,
            note = "RDA для углеводов — минимум для обеспечения мозга глюкозой; цель по углеводам на главном экране считается от калорийности.",
        ),
    )

    /**
     * Нормы для человека.
     * @param weightKg масса тела — для нормы белка (RDA на кг).
     * @param calorieGoalKcal калорийность — для ограничений в процентах от энергии.
     */
    fun forPerson(sex: Sex, ageYears: Int, weightKg: Double?, calorieGoalKcal: Double?): Map<Nutrient, NutrientNorm> {
        val stage = LifeStage.forAge(ageYears)
        val result = LinkedHashMap<Nutrient, NutrientNorm>()

        if (calorieGoalKcal != null) {
            result[Nutrient.ENERGY] = NutrientNorm(
                Nutrient.ENERGY, calorieGoalKcal, NormKind.USER, null, null,
                "Ваша цель по калориям",
            )
        }
        if (weightKg != null) {
            val perKg = if (stage == LifeStage.Y14_18) 0.85 else 0.8
            result[Nutrient.PROTEIN] = NutrientNorm(
                Nutrient.PROTEIN, perKg * weightKg, NormKind.RDA_PER_KG, null, null, SRC_MACRO,
                "RDA — ${perKg.toString().replace('.', ',')} г на кг массы тела; это минимум для здоровых людей, а не оптимум при снижении веса.",
            )
        }
        if (calorieGoalKcal != null) {
            result[Nutrient.FAT] = NutrientNorm(
                Nutrient.FAT, null, NormKind.ENERGY_SHARE, null, null, SRC_MACRO,
                "Допустимый диапазон AMDR — 20–35% калорийности: ${fmt(calorieGoalKcal * 0.20 / 9)}–${fmt(calorieGoalKcal * 0.35 / 9)} г.",
            )
            result[Nutrient.SATURATED_FAT] = NutrientNorm(
                Nutrient.SATURATED_FAT, null, null, calorieGoalKcal * 0.10 / 9.0, MaximumKind.DGA_LIMIT, SRC_DGA,
                "NASEM рекомендует потреблять как можно меньше насыщенных жиров при полноценном рационе.",
            )
            result[Nutrient.ADDED_SUGARS] = NutrientNorm(
                Nutrient.ADDED_SUGARS, null, null, calorieGoalKcal * 0.10 / 4.0, MaximumKind.DGA_LIMIT, SRC_DGA,
            )
        }
        result[Nutrient.TRANS_FAT] = NutrientNorm(
            Nutrient.TRANS_FAT, null, null, null, null, SRC_MACRO,
            "Числовой нормы нет: NASEM рекомендует потреблять как можно меньше.",
        )
        result[Nutrient.CHOLESTEROL] = NutrientNorm(
            Nutrient.CHOLESTEROL, null, null, null, null, SRC_MACRO,
            "Числовой нормы нет: NASEM рекомендует потреблять как можно меньше при полноценном рационе.",
        )

        for ((nutrient, row) in micro) {
            val target = if (sex == Sex.MALE) row.male[stage] else row.female[stage]
            result[nutrient] = NutrientNorm(
                nutrient = nutrient,
                target = target,
                kind = row.kind,
                maximum = row.ul?.get(stage),
                maximumKind = row.maximumKind,
                sourceRu = row.source,
                noteRu = row.note,
            )
        }
        return result
    }

    private fun fmt(v: Double): String = Math.round(v).toString()
}
