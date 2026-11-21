package com.nutritiontracker.core.calc

import java.time.LocalDate
import java.time.Period
import kotlin.math.roundToInt

enum class Sex(val key: String, val titleRu: String) {
    MALE("male", "Мужской"),
    FEMALE("female", "Женский"),
    ;

    companion object {
        fun fromKey(key: String): Sex? = entries.firstOrNull { it.key == key }
    }
}

enum class ActivityLevel(val key: String, val factor: Double, val titleRu: String, val descriptionRu: String) {
    MINIMAL("minimal", 1.2, "Минимальная", "Сидячая работа, почти без тренировок"),
    LIGHT("light", 1.375, "Лёгкая", "Лёгкие тренировки или прогулки 1–3 раза в неделю"),
    MODERATE("moderate", 1.55, "Умеренная", "Тренировки 3–5 раз в неделю"),
    HIGH("high", 1.725, "Высокая", "Интенсивные тренировки 6–7 раз в неделю"),
    VERY_HIGH("very_high", 1.9, "Очень высокая", "Физическая работа или тренировки дважды в день"),
    ;

    companion object {
        fun fromKey(key: String): ActivityLevel? = entries.firstOrNull { it.key == key }
    }
}

enum class DeficitMode(val key: String, val titleRu: String, val fraction: Double?) {
    /** Без дефицита — поддержание веса. */
    MAINTAIN("maintain", "Поддержание веса", 0.0),
    SMALL("small", "Небольшой дефицит (10%)", 0.10),
    MODERATE("moderate", "Умеренный дефицит (20%)", 0.20),
    /** Пользователь задаёт дефицит в ккал/сутки. */
    CUSTOM_DEFICIT("custom_deficit", "Свой дефицит", null),
    /** Пользователь задаёт калорийность напрямую. */
    MANUAL_GOAL("manual_goal", "Своя калорийность", null),
    ;

    companion object {
        fun fromKey(key: String): DeficitMode? = entries.firstOrNull { it.key == key }
    }
}

enum class GoalWarning(val messageRu: String) {
    AUTO_GOAL_RAISED_TO_BMR(
        "Расчётная цель оказалась ниже оценки основного обмена, поэтому она поднята до уровня основного обмена. Более низкую калорийность можно задать только вручную.",
    ),
    BELOW_BMR("Цель ниже оценки основного обмена (BMR). Длительный такой дефицит обычно не рекомендуют без наблюдения специалиста."),
    BELOW_COMMON_MINIMUM(
        "Цель ниже 1200 ккал (для женщин) / 1500 ккал (для мужчин) — ориентира, ниже которого низкокалорийные рационы обычно проводят под наблюдением специалиста.",
    ),
    VERY_LOW_CALORIE(
        "Цель не выше 800 ккал — это уровень очень низкокалорийной диеты (VLCD), которую проводят только под медицинским наблюдением (NIDDK).",
    ),
    LARGE_DEFICIT("Дефицит больше 25% от расчётного расхода энергии."),
    TARGET_BMI_LOW("Желаемый вес соответствует ИМТ ниже 18,5 — по классификации ВОЗ это недостаточная масса тела."),
    ESTIMATE_ONLY("Расход энергии — приблизительная оценка по формуле, а не измерение. Ошибка для конкретного человека может составлять 10% и больше."),
}

data class EnergyProfile(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val weightKg: Double,
    val activity: ActivityLevel,
)

data class CalorieGoal(
    val bmrKcal: Double,
    /** Расход энергии, использованный для расчёта (формула или адаптивная оценка). */
    val tdeeKcal: Double,
    val tdeeIsAdaptive: Boolean,
    val goalKcal: Double,
    val deficitKcal: Double,
    /** Ожидаемый темп изменения веса, кг/нед (отрицательный — снижение). Приблизительно. */
    val expectedWeeklyChangeKg: Double,
    val warnings: List<GoalWarning>,
)

object EnergyCalculator {
    /** Приблизительная энергетическая ценность 1 кг изменения массы тела. */
    const val KCAL_PER_KG_BODY_WEIGHT = 7700.0
    const val MIN_AGE = 18
    const val MAX_AGE = 100

    fun ageYears(birthDate: LocalDate, today: LocalDate): Int = Period.between(birthDate, today).years

    /**
     * Основной обмен по формуле Миффлина — Сан Жеора (Mifflin et al., 1990):
     * мужчины: 10 × вес + 6,25 × рост − 5 × возраст + 5;
     * женщины: 10 × вес + 6,25 × рост − 5 × возраст − 161.
     */
    fun bmrMifflinStJeor(sex: Sex, weightKg: Double, heightCm: Double, ageYears: Int): Double {
        require(weightKg.isFinite() && weightKg in 20.0..400.0) { "Вес вне допустимого диапазона: $weightKg" }
        require(heightCm.isFinite() && heightCm in 100.0..250.0) { "Рост вне допустимого диапазона: $heightCm" }
        require(ageYears in MIN_AGE..MAX_AGE) { "Возраст вне допустимого диапазона: $ageYears" }
        val base = 10.0 * weightKg + 6.25 * heightCm - 5.0 * ageYears
        return when (sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    fun tdee(bmr: Double, activity: ActivityLevel): Double = bmr * activity.factor

    fun bmi(weightKg: Double, heightCm: Double): Double {
        val m = heightCm / 100.0
        return weightKg / (m * m)
    }

    fun commonMinimumKcal(sex: Sex): Double = when (sex) {
        Sex.MALE -> 1500.0
        Sex.FEMALE -> 1200.0
    }

    /**
     * Суточная цель по калориям.
     *
     * @param adaptiveTdeeKcal оценка расхода по реальной динамике веса; если
     *   задана, используется вместо формульной.
     * @param customDeficitKcal дефицит для [DeficitMode.CUSTOM_DEFICIT].
     * @param manualGoalKcal калорийность для [DeficitMode.MANUAL_GOAL].
     */
    fun calorieGoal(
        profile: EnergyProfile,
        today: LocalDate,
        mode: DeficitMode,
        customDeficitKcal: Double? = null,
        manualGoalKcal: Double? = null,
        adaptiveTdeeKcal: Double? = null,
        targetWeightKg: Double? = null,
    ): CalorieGoal {
        val age = ageYears(profile.birthDate, today)
        val bmr = bmrMifflinStJeor(profile.sex, profile.weightKg, profile.heightCm, age)
        val formulaTdee = tdee(bmr, profile.activity)
        val tdee = adaptiveTdeeKcal?.takeIf { it.isFinite() && it > 0 } ?: formulaTdee
        val warnings = mutableListOf<GoalWarning>()

        var goal = when (mode) {
            DeficitMode.MAINTAIN, DeficitMode.SMALL, DeficitMode.MODERATE -> tdee * (1.0 - mode.fraction!!)
            DeficitMode.CUSTOM_DEFICIT -> {
                val d = requireNotNull(customDeficitKcal) { "Не указан дефицит" }
                require(d.isFinite() && d >= 0.0) { "Дефицит должен быть неотрицательным" }
                tdee - d
            }
            DeficitMode.MANUAL_GOAL -> {
                val g = requireNotNull(manualGoalKcal) { "Не указана калорийность" }
                require(g.isFinite() && g > 0.0) { "Калорийность должна быть больше нуля" }
                g
            }
        }
        // Автоматические режимы никогда не назначают калорийность ниже основного обмена.
        val automatic = mode == DeficitMode.SMALL || mode == DeficitMode.MODERATE || mode == DeficitMode.MAINTAIN
        if (automatic && goal < bmr) {
            goal = bmr
            warnings += GoalWarning.AUTO_GOAL_RAISED_TO_BMR
        }
        if (!automatic && goal < bmr) warnings += GoalWarning.BELOW_BMR
        if (goal <= 800.0) {
            warnings += GoalWarning.VERY_LOW_CALORIE
        } else if (goal < commonMinimumKcal(profile.sex)) {
            warnings += GoalWarning.BELOW_COMMON_MINIMUM
        }
        val deficit = tdee - goal
        if (deficit > tdee * 0.25) warnings += GoalWarning.LARGE_DEFICIT
        if (targetWeightKg != null && bmi(targetWeightKg, profile.heightCm) < 18.5) warnings += GoalWarning.TARGET_BMI_LOW
        if (adaptiveTdeeKcal == null) warnings += GoalWarning.ESTIMATE_ONLY

        return CalorieGoal(
            bmrKcal = bmr,
            tdeeKcal = tdee,
            tdeeIsAdaptive = adaptiveTdeeKcal != null,
            goalKcal = goal,
            deficitKcal = deficit,
            expectedWeeklyChangeKg = -deficit * 7.0 / KCAL_PER_KG_BODY_WEIGHT,
            warnings = warnings,
        )
    }

    /** Дефицит, соответствующий желаемому темпу снижения веса (кг/нед). Приблизительно. */
    fun deficitForWeeklyLoss(kgPerWeek: Double): Double =
        (kgPerWeek * KCAL_PER_KG_BODY_WEIGHT / 7.0).roundToInt().toDouble()
}
