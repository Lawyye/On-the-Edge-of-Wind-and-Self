package com.nutritiontracker.core

import com.nutritiontracker.core.calc.ActivityLevel
import com.nutritiontracker.core.calc.AdaptiveTdee
import com.nutritiontracker.core.calc.AdaptiveTdeeResult
import com.nutritiontracker.core.calc.DayIntake
import com.nutritiontracker.core.calc.DeficitMode
import com.nutritiontracker.core.calc.EnergyCalculator
import com.nutritiontracker.core.calc.EnergyProfile
import com.nutritiontracker.core.calc.GoalWarning
import com.nutritiontracker.core.calc.MacroCalculator
import com.nutritiontracker.core.calc.MacroPlan
import com.nutritiontracker.core.calc.NutrientTotal
import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.calc.WeightPoint
import com.nutritiontracker.core.calc.WeightTrend
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.norms.AssessmentStatus
import com.nutritiontracker.core.norms.DriNorms
import com.nutritiontracker.core.norms.MaximumKind
import com.nutritiontracker.core.norms.NormEvaluator
import com.nutritiontracker.core.norms.UserNutrientGoal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class EnergyAndNormsTest {
    private val eps = 1e-9
    private val today = LocalDate.of(2026, 11, 21)

    @Test
    fun `BMR по Миффлину — Сан Жеору для мужчины и женщины`() {
        assertEquals(1780.0, EnergyCalculator.bmrMifflinStJeor(Sex.MALE, 80.0, 180.0, 30), eps)
        assertEquals(1370.25, EnergyCalculator.bmrMifflinStJeor(Sex.FEMALE, 70.0, 165.0, 40), eps)
    }

    @Test
    fun `TDEE — BMR, умноженный на коэффициент активности`() {
        assertEquals(1780.0 * 1.2, EnergyCalculator.tdee(1780.0, ActivityLevel.MINIMAL), eps)
        assertEquals(1780.0 * 1.375, EnergyCalculator.tdee(1780.0, ActivityLevel.LIGHT), eps)
        assertEquals(1780.0 * 1.55, EnergyCalculator.tdee(1780.0, ActivityLevel.MODERATE), eps)
        assertEquals(1780.0 * 1.725, EnergyCalculator.tdee(1780.0, ActivityLevel.HIGH), eps)
        assertEquals(1780.0 * 1.9, EnergyCalculator.tdee(1780.0, ActivityLevel.VERY_HIGH), eps)
    }

    @Test
    fun `возраст считается по дате рождения`() {
        assertEquals(30, EnergyCalculator.ageYears(LocalDate.of(1996, 6, 15), today))
        assertEquals(29, EnergyCalculator.ageYears(LocalDate.of(1996, 11, 22), today))
    }

    @Test
    fun `небольшой и умеренный дефицит`() {
        val p = EnergyProfile(Sex.MALE, LocalDate.of(1996, 6, 15), 180.0, 80.0, ActivityLevel.MODERATE)
        val small = EnergyCalculator.calorieGoal(p, today, DeficitMode.SMALL)
        assertEquals(2759.0, small.tdeeKcal, eps)
        assertEquals(2759.0 * 0.9, small.goalKcal, eps)
        assertEquals(275.9, small.deficitKcal, 1e-6)
        val moderate = EnergyCalculator.calorieGoal(p, today, DeficitMode.MODERATE)
        assertEquals(2759.0 * 0.8, moderate.goalKcal, eps)
        assertEquals(-551.8 * 7 / 7700.0, moderate.expectedWeeklyChangeKg, 1e-9)
        assertTrue(GoalWarning.ESTIMATE_ONLY in moderate.warnings)
    }

    @Test
    fun `автоматический дефицит не опускает цель ниже основного обмена`() {
        val p = EnergyProfile(Sex.FEMALE, LocalDate.of(1986, 1, 1), 165.0, 70.0, ActivityLevel.MINIMAL)
        val g = EnergyCalculator.calorieGoal(p, today, DeficitMode.MODERATE)
        assertEquals(1370.25, g.bmrKcal, eps)
        assertEquals(1370.25, g.goalKcal, eps)
        assertTrue(GoalWarning.AUTO_GOAL_RAISED_TO_BMR in g.warnings)
    }

    @Test
    fun `слишком низкая своя калорийность сопровождается предупреждениями`() {
        val p = EnergyProfile(Sex.FEMALE, LocalDate.of(1990, 1, 1), 165.0, 70.0, ActivityLevel.LIGHT)
        val g = EnergyCalculator.calorieGoal(p, today, DeficitMode.MANUAL_GOAL, manualGoalKcal = 1100.0)
        assertEquals(1100.0, g.goalKcal, eps)
        assertTrue(GoalWarning.BELOW_BMR in g.warnings)
        assertTrue(GoalWarning.BELOW_COMMON_MINIMUM in g.warnings)
        assertTrue(GoalWarning.LARGE_DEFICIT in g.warnings)
        val vlcd = EnergyCalculator.calorieGoal(p, today, DeficitMode.MANUAL_GOAL, manualGoalKcal = 750.0)
        assertTrue(GoalWarning.VERY_LOW_CALORIE in vlcd.warnings)
    }

    @Test
    fun `свой дефицит в ккал`() {
        val p = EnergyProfile(Sex.MALE, LocalDate.of(1996, 6, 15), 180.0, 80.0, ActivityLevel.MODERATE)
        val g = EnergyCalculator.calorieGoal(p, today, DeficitMode.CUSTOM_DEFICIT, customDeficitKcal = 500.0)
        assertEquals(2259.0, g.goalKcal, eps)
        assertFalse(GoalWarning.LARGE_DEFICIT in g.warnings)
        assertEquals(550.0, EnergyCalculator.deficitForWeeklyLoss(0.5), eps)
    }

    @Test
    fun `адаптивный расход заменяет формульный`() {
        val p = EnergyProfile(Sex.MALE, LocalDate.of(1996, 6, 15), 180.0, 80.0, ActivityLevel.MODERATE)
        val g = EnergyCalculator.calorieGoal(p, today, DeficitMode.SMALL, adaptiveTdeeKcal = 2500.0)
        assertEquals(2250.0, g.goalKcal, eps)
        assertTrue(g.tdeeIsAdaptive)
        assertFalse(GoalWarning.ESTIMATE_ONLY in g.warnings)
    }

    @Test
    fun `некорректные данные профиля отклоняются`() {
        assertThrows(IllegalArgumentException::class.java) { EnergyCalculator.bmrMifflinStJeor(Sex.MALE, 0.0, 180.0, 30) }
        assertThrows(IllegalArgumentException::class.java) { EnergyCalculator.bmrMifflinStJeor(Sex.MALE, 80.0, 30.0, 30) }
        assertThrows(IllegalArgumentException::class.java) { EnergyCalculator.bmrMifflinStJeor(Sex.MALE, 80.0, 180.0, 12) }
        assertThrows(IllegalArgumentException::class.java) { EnergyCalculator.bmrMifflinStJeor(Sex.MALE, Double.NaN, 180.0, 30) }
    }

    @Test
    fun `низкий желаемый ИМТ вызывает предупреждение`() {
        val p = EnergyProfile(Sex.FEMALE, LocalDate.of(1990, 1, 1), 170.0, 60.0, ActivityLevel.LIGHT)
        val g = EnergyCalculator.calorieGoal(p, today, DeficitMode.SMALL, targetWeightKg = 50.0)
        assertTrue(GoalWarning.TARGET_BMI_LOW in g.warnings)
    }

    @Test
    fun `белок в граммах на кг веса, жиры долей, углеводы остатком`() {
        // Умеренный дефицит, вес 82 кг при росте 180 см (ИМТ 25,3) — белок от текущего веса.
        val ref = MacroCalculator.referenceWeight(82.0, 76.0, 180.0, losingWeight = true)
        assertEquals(82.0, ref, eps)
        val m = MacroCalculator.goals(2232.0, ref, MacroPlan(1.6, 30))
        assertEquals(131.2, m.proteinG, 1e-9)
        assertEquals(2232.0 * 0.30 / 9.0, m.fatG, 1e-9)
        assertEquals((2232.0 - 131.2 * 4 - 2232.0 * 0.30) / 4.0, m.carbsG, 1e-9)
        assertTrue(m.proteinG in 110.0..140.0)
        assertTrue(m.warnings.isEmpty())
        // При ожирении (ИМТ ≥ 30) и снижении веса — от желаемого веса.
        assertEquals(85.0, MacroCalculator.referenceWeight(120.0, 85.0, 175.0, losingWeight = true), eps)
        assertEquals(120.0, MacroCalculator.referenceWeight(120.0, 85.0, 175.0, losingWeight = false), eps)
    }

    @Test
    fun `слишком мало калорий для белка и жиров даёт предупреждение, а не отрицательные углеводы`() {
        val m = MacroCalculator.goals(900.0, 90.0, MacroPlan(2.2, 40))
        assertEquals(0.0, m.carbsG, 0.0)
        assertTrue(m.warnings.any { it.contains("ничего не остаётся") })
        val low = MacroCalculator.goals(1400.0, 80.0, MacroPlan(1.6, 35))
        assertTrue(low.warnings.any { it.contains("130 г") })
        assertThrows(IllegalArgumentException::class.java) { MacroCalculator.goals(2000.0, 80.0, MacroPlan(3.0, 30)) }
        assertThrows(IllegalArgumentException::class.java) { MacroCalculator.goals(2000.0, 80.0, MacroPlan(1.6, 10)) }
    }

    @Test
    fun `в нормах белок, жиры и углеводы берутся из плана, а RDA — справка`() {
        val goals = MacroCalculator.goals(2232.0, 76.0, MacroPlan.DEFAULT)
        val norms = DriNorms.withMacroPlan(DriNorms.forPerson(Sex.MALE, 30, 82.0, 2232.0), goals, MacroPlan.DEFAULT, 30)
        assertEquals(goals.proteinG, norms.getValue(Nutrient.PROTEIN).target!!, eps)
        assertTrue(norms.getValue(Nutrient.PROTEIN).noteRu!!.contains("0,8"))
        assertEquals(goals.fatG, norms.getValue(Nutrient.FAT).target!!, eps)
        assertEquals(goals.carbsG, norms.getValue(Nutrient.CARBS).target!!, eps)
    }

    @Test
    fun `прогноз даты цели по тенденции веса`() {
        val pts = (0..20).map { WeightPoint(today.minusDays(20L - it), 85.0 - it * 0.1) }
        // Тренд −0,1 кг/сут; среднее за 7 дней ≈ 83,3 → до 80 кг ≈ 33 дня.
        val d = WeightTrend.forecastGoalDate(pts, 80.0, today)!!
        assertTrue(d.toString(), !d.isBefore(today.plusDays(32)) && !d.isAfter(today.plusDays(34)))
        // Вес растёт, а цель ниже — прогноза нет.
        val up = (0..20).map { WeightPoint(today.minusDays(20L - it), 80.0 + it * 0.1) }
        assertNull(WeightTrend.forecastGoalDate(up, 75.0, today))
        assertNull(WeightTrend.forecastGoalDate(pts.take(3), 80.0, today))
    }

    @Test
    fun `нормы NASEM для мужчины 30 лет`() {
        val n = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        assertEquals(90.0, n.getValue(Nutrient.VITAMIN_C).target!!, eps)
        assertEquals(2000.0, n.getValue(Nutrient.VITAMIN_C).maximum!!, eps)
        assertEquals(8.0, n.getValue(Nutrient.IRON).target!!, eps)
        assertEquals(400.0, n.getValue(Nutrient.MAGNESIUM).target!!, eps)
        assertNull(n.getValue(Nutrient.MAGNESIUM).maximum)
        assertEquals(3400.0, n.getValue(Nutrient.POTASSIUM).target!!, eps)
        assertEquals(1500.0, n.getValue(Nutrient.SODIUM).target!!, eps)
        assertEquals(2300.0, n.getValue(Nutrient.SODIUM).maximum!!, eps)
        assertEquals(MaximumKind.CDRR, n.getValue(Nutrient.SODIUM).maximumKind)
        assertEquals(38.0, n.getValue(Nutrient.FIBER).target!!, eps)
        assertEquals(64.0, n.getValue(Nutrient.PROTEIN).target!!, eps)
        assertEquals(15.0, n.getValue(Nutrient.VITAMIN_D).target!!, eps)
        assertEquals(0.9, n.getValue(Nutrient.COPPER).target!!, eps)
        assertNull(n.getValue(Nutrient.VITAMIN_A).maximum)
    }

    @Test
    fun `нормы зависят от пола и возраста`() {
        val f40 = DriNorms.forPerson(Sex.FEMALE, 40, 70.0, null)
        assertEquals(18.0, f40.getValue(Nutrient.IRON).target!!, eps)
        assertEquals(320.0, f40.getValue(Nutrient.MAGNESIUM).target!!, eps)
        assertEquals(25.0, f40.getValue(Nutrient.FIBER).target!!, eps)
        val f55 = DriNorms.forPerson(Sex.FEMALE, 55, 70.0, null)
        assertEquals(8.0, f55.getValue(Nutrient.IRON).target!!, eps)
        assertEquals(1200.0, f55.getValue(Nutrient.CALCIUM).target!!, eps)
        assertEquals(2000.0, f55.getValue(Nutrient.CALCIUM).maximum!!, eps)
        val m75 = DriNorms.forPerson(Sex.MALE, 75, 70.0, null)
        assertEquals(20.0, m75.getValue(Nutrient.VITAMIN_D).target!!, eps)
        assertEquals(3000.0, m75.getValue(Nutrient.PHOSPHORUS).maximum!!, eps)
        assertEquals(1.7, m75.getValue(Nutrient.VITAMIN_B6).target!!, eps)
        val m18 = DriNorms.forPerson(Sex.MALE, 18, 70.0, null)
        assertEquals(1300.0, m18.getValue(Nutrient.CALCIUM).target!!, eps)
        assertEquals(1250.0, m18.getValue(Nutrient.PHOSPHORUS).target!!, eps)
        assertEquals(0.85 * 70.0, m18.getValue(Nutrient.PROTEIN).target!!, eps)
        // Без калорийности нет ограничений в процентах от энергии.
        assertNull(f40[Nutrient.SATURATED_FAT])
    }

    private fun total(n: Nutrient, v: Double?, with: Int, of: Int, partial: Boolean = false) =
        NutrientTotal(n, v, with, of, partial)

    @Test
    fun `оценка выполнения нормы`() {
        val norms = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        val met = NormEvaluator.assess(total(Nutrient.VITAMIN_C, 120.0, 3, 3), norms[Nutrient.VITAMIN_C])
        assertEquals(AssessmentStatus.MET, met.status)
        assertEquals(120.0 / 90.0 * 100.0, met.percentOfTarget!!, eps)
        val below = NormEvaluator.assess(total(Nutrient.VITAMIN_C, 45.0, 3, 3), norms[Nutrient.VITAMIN_C])
        assertEquals(AssessmentStatus.BELOW, below.status)
        assertEquals(45.0, below.shortfall!!, eps)
    }

    @Test
    fun `при неполных данных недостаток не утверждается`() {
        val norms = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        val a = NormEvaluator.assess(total(Nutrient.IRON, 4.0, 2, 5), norms[Nutrient.IRON])
        assertEquals(AssessmentStatus.BELOW_INCOMPLETE, a.status)
        assertNull(a.shortfall)
        assertEquals(50.0, a.percentOfTarget!!, eps)
    }

    @Test
    fun `превышение верхнего предела фиксируется даже по нижней границе`() {
        val norms = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        val a = NormEvaluator.assess(total(Nutrient.SODIUM, 3100.0, 3, 6), norms[Nutrient.SODIUM])
        assertEquals(AssessmentStatus.ABOVE_MAXIMUM, a.status)
        assertEquals(800.0, a.excess!!, eps)
    }

    @Test
    fun `нет данных — это не ноль процентов`() {
        val norms = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        val a = NormEvaluator.assess(total(Nutrient.VITAMIN_D, null, 0, 4), norms[Nutrient.VITAMIN_D])
        assertEquals(AssessmentStatus.NO_DATA, a.status)
        assertNull(a.percentOfTarget)
    }

    @Test
    fun `своя цель заменяет справочную норму`() {
        val norms = DriNorms.forPerson(Sex.MALE, 30, 80.0, 2200.0)
        val custom = NormEvaluator.applyUserGoals(norms, listOf(UserNutrientGoal(Nutrient.PROTEIN, 140.0, null)))
        assertEquals(140.0, custom.getValue(Nutrient.PROTEIN).target!!, eps)
        assertEquals("Ваша цель", custom.getValue(Nutrient.PROTEIN).sourceRu)
    }

    @Test
    fun `тенденция веса по скользящему среднему`() {
        val pts = (0..13).map { WeightPoint(today.minusDays(13L - it), 80.0 - it * 0.1) }
        val s = WeightTrend.summary(pts, startWeightKg = 82.0, targetWeightKg = 72.0, today = today)
        val last7 = (7..13).map { 80.0 - it * 0.1 }.average()
        assertEquals(last7, s.average7!!, 1e-9)
        assertEquals(last7 - 82.0, s.changeFromStart!!, 1e-9)
        assertEquals((82.0 - last7) / 10.0, s.progressToTarget!!, 1e-9)
        assertNull(s.change30Days)
        assertEquals(-0.1, WeightTrend.slopeKgPerDay(pts)!!, 1e-9)
    }

    @Test
    fun `несколько взвешиваний за день усредняются`() {
        val d = today
        val daily = WeightTrend.daily(listOf(WeightPoint(d, 80.0), WeightPoint(d, 81.0)))
        assertEquals(1, daily.size)
        assertEquals(80.5, daily[0].weightKg, eps)
    }

    @Test
    fun `адаптивный расход по балансу энергии`() {
        // 28 дней по 2000 ккал, вес падает на 0,05 кг/сутки → расход 2000 + 0,05 × 7700 = 2385.
        val intakes = (1..28).map { DayIntake(today.minusDays(it.toLong()), 2000.0, true) }
        val weights = (1..28 step 2).map { WeightPoint(today.minusDays(it.toLong()), 80.0 + it * 0.05) }
        val r = AdaptiveTdee.estimate(intakes, weights, today)
        assertTrue(r is AdaptiveTdeeResult.Estimate)
        r as AdaptiveTdeeResult.Estimate
        assertEquals(2385.0, r.tdeeKcal, 1e-6)
        assertEquals(-0.35, r.weightSlopeKgPerWeek, 1e-9)
    }

    @Test
    fun `адаптивный расход требует достаточно данных`() {
        val intakes = (1..10).map { DayIntake(today.minusDays(it.toLong()), 2000.0, true) }
        val weights = (1..10).map { WeightPoint(today.minusDays(it.toLong()), 80.0) }
        assertTrue(AdaptiveTdee.estimate(intakes, weights, today) is AdaptiveTdeeResult.NotEnoughData)
        val sparse = (1..28 step 3).map { DayIntake(today.minusDays(it.toLong()), 2000.0, true) }
        val w2 = (1..28 step 2).map { WeightPoint(today.minusDays(it.toLong()), 80.0) }
        assertTrue(AdaptiveTdee.estimate(sparse, w2, today) is AdaptiveTdeeResult.NotEnoughData)
    }

    @Test
    fun `применение адаптивной оценки ограничено шагом`() {
        assertEquals(2500.0, AdaptiveTdee.nextApplied(null, 2500.0), eps)
        assertEquals(2650.0, AdaptiveTdee.nextApplied(2500.0, 2900.0), eps)
        assertEquals(2350.0, AdaptiveTdee.nextApplied(2500.0, 2100.0), eps)
        assertEquals(2550.0, AdaptiveTdee.nextApplied(2500.0, 2550.0), eps)
    }
}
