package com.nutritiontracker.data

import com.nutritiontracker.core.calc.ActivityLevel
import com.nutritiontracker.core.calc.AdaptiveTdee
import com.nutritiontracker.core.calc.AdaptiveTdeeResult
import com.nutritiontracker.core.calc.DayIntake
import com.nutritiontracker.core.calc.DeficitMode
import com.nutritiontracker.core.calc.MacroPlan
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.calc.WeightPoint
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.NutrientGoalEntity
import com.nutritiontracker.data.db.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Profile(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: Double,
    val startWeightKg: Double,
    val targetWeightKg: Double,
    val activity: ActivityLevel,
    val deficitMode: DeficitMode,
    val customDeficitKcal: Double?,
    val manualGoalKcal: Double?,
    val macroPlan: MacroPlan,
    val waterGoalMl: Int,
    val adaptiveEnabled: Boolean,
    val appliedAdaptiveTdee: Double?,
    val adaptiveUpdated: LocalDate?,
    val createdAt: Long,
)

fun ProfileEntity.toProfile(): Profile = Profile(
    sex = Sex.fromKey(sex) ?: Sex.FEMALE,
    birthDate = LocalDate.ofEpochDay(birthEpochDay),
    heightCm = heightCm,
    startWeightKg = startWeightKg,
    targetWeightKg = targetWeightKg,
    activity = ActivityLevel.fromKey(activityLevel) ?: ActivityLevel.MINIMAL,
    deficitMode = DeficitMode.fromKey(deficitMode) ?: DeficitMode.SMALL,
    customDeficitKcal = customDeficitKcal,
    manualGoalKcal = manualGoalKcal,
    macroPlan = MacroPlan(
        proteinGPerKg = proteinGPerKg.coerceIn(MacroPlan.MIN_PROTEIN_G_PER_KG, MacroPlan.MAX_PROTEIN_G_PER_KG),
        fatPercent = fatPercent.coerceIn(MacroPlan.FAT_PERCENT_RANGE.first, MacroPlan.FAT_PERCENT_RANGE.last),
    ),
    waterGoalMl = waterGoalMl,
    adaptiveEnabled = adaptiveEnabled,
    appliedAdaptiveTdee = appliedAdaptiveTdee,
    adaptiveUpdated = adaptiveUpdatedEpochDay?.let { LocalDate.ofEpochDay(it) },
    createdAt = createdAt,
)

data class UserGoal(val nutrient: Nutrient, val target: Double?, val maximum: Double?)

class ProfileRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.profileDao()
    private val goalDao = db.goalDao()

    fun observe(): Flow<Profile?> = dao.observe().map { it?.toProfile() }
    suspend fun get(): Profile? = dao.get()?.toProfile()

    suspend fun save(p: Profile) {
        val now = clock()
        val existing = dao.get()
        dao.upsert(
            ProfileEntity(
                sex = p.sex.key,
                birthEpochDay = p.birthDate.toEpochDay(),
                heightCm = p.heightCm,
                startWeightKg = p.startWeightKg,
                targetWeightKg = p.targetWeightKg,
                activityLevel = p.activity.key,
                deficitMode = p.deficitMode.key,
                customDeficitKcal = p.customDeficitKcal,
                manualGoalKcal = p.manualGoalKcal,
                // Доли белка и углеводов устарели (с версии 2 белок в г/кг), хранятся для совместимости.
                proteinPercent = existing?.proteinPercent ?: 25,
                fatPercent = p.macroPlan.fatPercent,
                carbsPercent = existing?.carbsPercent ?: 45,
                proteinGPerKg = p.macroPlan.proteinGPerKg,
                waterGoalMl = p.waterGoalMl,
                adaptiveEnabled = p.adaptiveEnabled,
                appliedAdaptiveTdee = if (p.adaptiveEnabled) p.appliedAdaptiveTdee else null,
                adaptiveUpdatedEpochDay = if (p.adaptiveEnabled) p.adaptiveUpdated?.toEpochDay() else null,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            ),
        )
    }

    fun observeUserGoals(): Flow<List<UserGoal>> = goalDao.observeAll().map { l ->
        l.mapNotNull { g -> Nutrient.fromKey(g.nutrientKey)?.let { UserGoal(it, g.target, g.maximum) } }
    }

    suspend fun setUserGoal(nutrient: Nutrient, target: Double?, maximum: Double?) {
        if (target == null && maximum == null) {
            goalDao.delete(nutrient.key)
        } else {
            goalDao.upsert(
                NutrientGoalEntity(
                    nutrientKey = nutrient.key, target = target, maximum = maximum,
                    unit = nutrient.unit.name, source = "user", updatedAt = clock(),
                ),
            )
        }
    }

    /**
     * Еженедельное обновление адаптивной оценки расхода энергии, если оно
     * включено. Цель меняется не чаще раза в неделю и не больше чем на
     * [AdaptiveTdee.MAX_STEP_KCAL] за раз.
     */
    suspend fun maybeUpdateAdaptive(today: LocalDate): AdaptiveTdeeResult? {
        val profile = get() ?: return null
        if (!profile.adaptiveEnabled) return null
        val last = profile.adaptiveUpdated
        if (last != null && ChronoUnit.DAYS.between(last, today) < 7) return null
        val result = estimateAdaptive(today)
        if (result is AdaptiveTdeeResult.Estimate) {
            val next = AdaptiveTdee.nextApplied(profile.appliedAdaptiveTdee, result.tdeeKcal)
            save(profile.copy(appliedAdaptiveTdee = next, adaptiveUpdated = today))
        }
        return result
    }

    suspend fun estimateAdaptive(today: LocalDate): AdaptiveTdeeResult {
        val from = today.minusDays(AdaptiveTdee.WINDOW_DAYS)
        val entries = db.entryDao().range(from.toEpochDay(), today.minusDays(1).toEpochDay()).map { it.toEntry() }
        val intakes = entries.groupBy { it.date }.map { (d, list) ->
            val total = NutrientMath.total(list.map { it.eaten })[Nutrient.ENERGY]
            DayIntake(d, total.value ?: 0.0, total.isComplete)
        }
        val weights = db.weightDao().all().mapNotNull { w -> w.weightKg?.let { WeightPoint(LocalDate.ofEpochDay(w.epochDay), it) } }
        return AdaptiveTdee.estimate(intakes, weights, today)
    }
}
