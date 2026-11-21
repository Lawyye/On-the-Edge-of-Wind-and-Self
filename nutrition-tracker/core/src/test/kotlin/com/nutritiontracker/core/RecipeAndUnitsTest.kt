package com.nutritiontracker.core

import com.nutritiontracker.core.calc.EnergyConsistency
import com.nutritiontracker.core.calc.RecipeCalculator
import com.nutritiontracker.core.calc.RecipeIngredientInput
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.NutrientUnit
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.SourceUnit
import com.nutritiontracker.core.model.UnitConversion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RecipeAndUnitsTest {
    private val eps = 1e-9

    private fun ing(name: String, g: Double, vararg n: Pair<Nutrient, Double>) =
        RecipeIngredientInput(name, g, AmountUnit.GRAM, Nutrients.of(*n))

    // Значения на 100 г — условные, для проверки арифметики.
    private val soup = listOf(
        ing("Куриное филе", 200.0, Nutrient.ENERGY to 165.0, Nutrient.PROTEIN to 31.0, Nutrient.VITAMIN_C to 0.0),
        ing("Картофель", 300.0, Nutrient.ENERGY to 77.0, Nutrient.PROTEIN to 2.0, Nutrient.VITAMIN_C to 19.7),
        ing("Морковь", 100.0, Nutrient.ENERGY to 41.0, Nutrient.PROTEIN to 0.9, Nutrient.VITAMIN_C to 5.9),
        ing("Лук", 50.0, Nutrient.ENERGY to 40.0, Nutrient.PROTEIN to 1.1),
        ing("Вода", 1000.0, Nutrient.ENERGY to 0.0, Nutrient.PROTEIN to 0.0, Nutrient.VITAMIN_C to 0.0),
    )

    @Test
    fun `суммируются все ингредиенты рецепта`() {
        val r = RecipeCalculator.calculate(soup, 1400.0)
        val expectedKcal = 165.0 * 2 + 77.0 * 3 + 41.0 + 40.0 * 0.5 + 0.0
        assertEquals(expectedKcal, r.total[Nutrient.ENERGY].value!!, eps)
        assertEquals(1650.0, r.rawMassGrams!!, eps)
    }

    @Test
    fun `ценность на 100 г готового блюда считается по фактическому весу`() {
        val r = RecipeCalculator.calculate(soup, 1400.0)
        val totalKcal = 165.0 * 2 + 77.0 * 3 + 41.0 + 20.0
        assertEquals(totalKcal / 1400.0 * 100.0, r.per100Cooked!![Nutrient.ENERGY]!!, eps)
        assertEquals(1400.0 / 1650.0 * 100.0, r.yieldPercent!!, eps)
    }

    @Test
    fun `без веса готового блюда ценность на 100 г не выдумывается`() {
        val r = RecipeCalculator.calculate(soup, null)
        assertNull(r.per100Cooked)
        assertTrue(r.total[Nutrient.ENERGY].isComplete)
    }

    @Test
    fun `нутриент без данных у части ингредиентов помечается как неполный`() {
        val r = RecipeCalculator.calculate(soup, 1400.0)
        // У лука нет данных о витамине C.
        assertTrue(r.per100Cooked!!.isKnown(Nutrient.VITAMIN_C))
        assertTrue(Nutrient.VITAMIN_C in r.per100Cooked!!.incomplete)
        assertTrue(r.per100Cooked!!.isComplete(Nutrient.ENERGY))
        // О железе нет данных ни у одного ингредиента.
        assertNull(r.per100Cooked!![Nutrient.IRON])
    }

    @Test
    fun `изменение веса готового блюда меняет только ценность на 100 г`() {
        val a = RecipeCalculator.calculate(soup, 1400.0)
        val b = RecipeCalculator.calculate(soup, 1200.0)
        assertEquals(a.total[Nutrient.ENERGY].value!!, b.total[Nutrient.ENERGY].value!!, eps)
        assertEquals(a.per100Cooked!![Nutrient.ENERGY]!! * 1400.0 / 1200.0, b.per100Cooked!![Nutrient.ENERGY]!!, eps)
    }

    @Test
    fun `ингредиенты в мл не складываются с граммами`() {
        val milk = RecipeIngredientInput("Молоко", 200.0, AmountUnit.MILLILITER, Nutrients.of(Nutrient.ENERGY to 52.0))
        val r = RecipeCalculator.calculate(soup + milk, 1600.0)
        assertNull(r.rawMassGrams)
        assertTrue(r.hasVolumeIngredients)
        assertEquals(1650.0 / 100 * 0 + (330.0 + 231.0 + 41.0 + 20.0 + 104.0), r.total[Nutrient.ENERGY].value!!, eps)
    }

    @Test
    fun `некорректный вес отклоняется`() {
        assertThrows(IllegalArgumentException::class.java) { RecipeCalculator.calculate(soup, 0.0) }
        assertThrows(IllegalArgumentException::class.java) { RecipeCalculator.calculate(soup, -5.0) }
        assertThrows(IllegalArgumentException::class.java) {
            RecipeCalculator.calculate(listOf(ing("x", 0.0, Nutrient.ENERGY to 1.0)), 100.0)
        }
    }

    @Test
    fun `миллиграммы и микрограммы не смешиваются`() {
        // 0,5 мг селена = 500 мкг (селен хранится в мкг)
        assertEquals(500.0, UnitConversion.toCanonical(Nutrient.SELENIUM, 0.5, SourceUnit.MG)!!, eps)
        // 200 мкг железа = 0,2 мг (железо хранится в мг)
        assertEquals(0.2, UnitConversion.toCanonical(Nutrient.IRON, 200.0, SourceUnit.UG)!!, eps)
        // Open Food Facts отдаёт всё в граммах: 0,0012 г витамина C = 1,2 мг
        assertEquals(1.2, UnitConversion.toCanonical(Nutrient.VITAMIN_C, 0.0012, SourceUnit.G)!!, eps)
        assertEquals(1.0, UnitConversion.convertMass(1000.0, NutrientUnit.UG, NutrientUnit.MG), eps)
        assertEquals(1000.0, UnitConversion.convertMass(1.0, NutrientUnit.G, NutrientUnit.MG), eps)
    }

    @Test
    fun `разные обозначения микрограмма распознаются`() {
        listOf("µg", "μg", "UG", "ug", "mcg").forEach { assertEquals(it, SourceUnit.UG, SourceUnit.parse(it)) }
        assertEquals(SourceUnit.KCAL, SourceUnit.parse("KCAL"))
        assertEquals(SourceUnit.KJ, SourceUnit.parse("kJ"))
        assertNull(SourceUnit.parse("cup"))
    }

    @Test
    fun `кДж переводятся в ккал точно, а несовместимые единицы отклоняются`() {
        assertEquals(53.776290630975143, UnitConversion.toCanonical(Nutrient.ENERGY, 225.0, SourceUnit.KJ)!!, 1e-9)
        assertNull(UnitConversion.toCanonical(Nutrient.ENERGY, 10.0, SourceUnit.G))
        assertNull(UnitConversion.toCanonical(Nutrient.PROTEIN, 10.0, SourceUnit.KCAL))
    }

    @Test
    fun `МЕ переводятся только для витамина D`() {
        assertEquals(10.0, UnitConversion.toCanonical(Nutrient.VITAMIN_D, 400.0, SourceUnit.IU)!!, eps)
        // Для витамина A коэффициент зависит от формы — значение остаётся неизвестным.
        assertNull(UnitConversion.toCanonical(Nutrient.VITAMIN_A, 65.0, SourceUnit.IU))
        assertNull(UnitConversion.toCanonical(Nutrient.VITAMIN_E, 10.0, SourceUnit.IU))
    }

    @Test
    fun `калорийность, согласованная с БЖУ, не вызывает предупреждения`() {
        // USDA Foundation, куриная грудка: 106 ккал (Atwater general), Б 22,5, Ж 1,93, У 0
        val chicken = Nutrients.of(Nutrient.ENERGY to 106.034, Nutrient.PROTEIN to 22.525, Nutrient.FAT to 1.934, Nutrient.CARBS to 0.0)
        assertEquals(EnergyConsistency.Status.CONSISTENT, EnergyConsistency.check(chicken).status)
        // Open Food Facts, Nutella: 539 ккал, Б 6,3, Ж 30,9, У 57,5, клетчатка 0
        val nutella = Nutrients.of(Nutrient.ENERGY to 539.0, Nutrient.PROTEIN to 6.3, Nutrient.FAT to 30.9, Nutrient.CARBS to 57.5, Nutrient.FIBER to 0.0)
        assertEquals(EnergyConsistency.Status.CONSISTENT, EnergyConsistency.check(nutella).status)
    }

    @Test
    fun `кДж вместо ккал обнаруживается как расхождение`() {
        val wrong = Nutrients.of(Nutrient.ENERGY to 2252.0, Nutrient.PROTEIN to 6.3, Nutrient.FAT to 30.9, Nutrient.CARBS to 57.5)
        val r = EnergyConsistency.check(wrong)
        assertEquals(EnergyConsistency.Status.MISMATCH, r.status)
        assertTrue(r.deviationPercent!! > 100)
    }

    @Test
    fun `без БЖУ проверка не делается`() {
        assertEquals(EnergyConsistency.Status.NOT_ENOUGH_DATA, EnergyConsistency.check(Nutrients.of(Nutrient.ENERGY to 50.0)).status)
    }
}
