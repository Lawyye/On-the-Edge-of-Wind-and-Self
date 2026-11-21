package com.nutritiontracker.core

import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class NutrientMathTest {
    private val eps = 1e-9

    private val chicken = Nutrients.of(
        Nutrient.ENERGY to 165.0,
        Nutrient.PROTEIN to 31.0,
        Nutrient.FAT to 3.6,
        Nutrient.CARBS to 0.0,
    )

    @Test
    fun `пищевая ценность на 100 г пересчитывается на 200 г`() {
        val eaten = NutrientMath.forAmount(chicken, 200.0)
        assertEquals(330.0, eaten[Nutrient.ENERGY]!!, eps)
        assertEquals(62.0, eaten[Nutrient.PROTEIN]!!, eps)
        assertEquals(7.2, eaten[Nutrient.FAT]!!, eps)
    }

    @Test
    fun `пересчёт на произвольную массу`() {
        val eaten = NutrientMath.forAmount(chicken, 37.5)
        assertEquals(165.0 * 37.5 / 100.0, eaten[Nutrient.ENERGY]!!, eps)
        assertEquals(31.0 * 0.375, eaten[Nutrient.PROTEIN]!!, eps)
    }

    @Test
    fun `ноль остаётся нулём, а неизвестное — неизвестным`() {
        val eaten = NutrientMath.forAmount(chicken, 250.0)
        assertEquals(0.0, eaten[Nutrient.CARBS]!!, 0.0)
        assertTrue(eaten.isKnown(Nutrient.CARBS))
        assertNull(eaten[Nutrient.VITAMIN_C])
        assertFalse(eaten.isKnown(Nutrient.VITAMIN_C))
    }

    @Test
    fun `нулевая масса даёт нули для известных и неизвестное для неизвестных`() {
        val eaten = NutrientMath.forAmount(chicken, 0.0)
        assertEquals(0.0, eaten[Nutrient.ENERGY]!!, 0.0)
        assertNull(eaten[Nutrient.IRON])
    }

    @Test
    fun `отрицательная и нечисловая масса отклоняются`() {
        assertThrows(IllegalArgumentException::class.java) { NutrientMath.forAmount(chicken, -1.0) }
        assertThrows(IllegalArgumentException::class.java) { NutrientMath.forAmount(chicken, Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { NutrientMath.forAmount(chicken, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `отрицательные и нечисловые значения нутриентов отклоняются`() {
        assertThrows(IllegalArgumentException::class.java) { Nutrients.of(Nutrient.IRON to -0.1) }
        assertThrows(IllegalArgumentException::class.java) { Nutrients.of(Nutrient.IRON to Double.NaN) }
    }

    @Test
    fun `сумма витаминов и минералов за день с полными данными`() {
        val a = Nutrients.of(Nutrient.VITAMIN_C to 4.6, Nutrient.IRON to 0.12, Nutrient.SELENIUM to 0.0)
        val b = Nutrients.of(Nutrient.VITAMIN_C to 53.2, Nutrient.IRON to 0.8, Nutrient.SELENIUM to 0.4)
        val totals = NutrientMath.total(listOf(a, b))
        assertEquals(57.8, totals[Nutrient.VITAMIN_C].value!!, eps)
        assertTrue(totals[Nutrient.VITAMIN_C].isComplete)
        assertEquals(0.92, totals[Nutrient.IRON].value!!, eps)
        assertEquals(0.4, totals[Nutrient.SELENIUM].value!!, eps)
    }

    @Test
    fun `если данные есть не для всех продуктов, сумма — нижняя граница`() {
        val withC = Nutrients.of(Nutrient.VITAMIN_C to 10.0, Nutrient.ENERGY to 50.0)
        val withoutC = Nutrients.of(Nutrient.ENERGY to 100.0)
        val totals = NutrientMath.total(listOf(withC, withoutC))
        val c = totals[Nutrient.VITAMIN_C]
        assertEquals(10.0, c.value!!, eps)
        assertTrue(c.isLowerBound)
        assertFalse(c.isComplete)
        assertEquals(1, c.itemsWithData)
        assertEquals(2, c.itemsTotal)
        assertTrue(totals[Nutrient.ENERGY].isComplete)
    }

    @Test
    fun `если данных нет ни у одного продукта, сумма неизвестна, а не ноль`() {
        val totals = NutrientMath.total(listOf(chicken, chicken))
        assertNull(totals[Nutrient.VITAMIN_D].value)
        assertTrue(totals[Nutrient.VITAMIN_D].isUnknown)
    }

    @Test
    fun `пустой день — это ноль съеденного`() {
        val totals = NutrientMath.total(emptyList())
        assertEquals(0.0, totals[Nutrient.ENERGY].value!!, 0.0)
        assertTrue(totals[Nutrient.ENERGY].isComplete)
    }

    @Test
    fun `неполнота ингредиента переходит в сумму`() {
        val partial = Nutrients.of(mapOf(Nutrient.ZINC to 1.0), incomplete = setOf(Nutrient.ZINC))
        val full = Nutrients.of(Nutrient.ZINC to 2.0)
        val totals = NutrientMath.total(listOf(partial, full))
        assertEquals(3.0, totals[Nutrient.ZINC].value!!, eps)
        assertTrue(totals[Nutrient.ZINC].isLowerBound)
    }

    @Test
    fun `средние за период делятся на число дней без потери признаков`() {
        val totals = NutrientMath.total(listOf(Nutrients.of(Nutrient.ENERGY to 1400.0), Nutrients.of(Nutrient.ENERGY to 2200.0)))
        val avg = totals.dividedBy(2.0)
        assertEquals(1800.0, avg[Nutrient.ENERGY].value!!, eps)
        assertNull(avg[Nutrient.IRON].value)
    }

    @Test
    fun `хранимый вид читается обратно без потерь и без лишних ключей`() {
        val n = Nutrients.of(mapOf(Nutrient.ENERGY to 52.0, Nutrient.VITAMIN_C to 4.6), setOf(Nutrient.VITAMIN_C))
        val restored = Nutrients.fromStorage(n.storageValues(), n.storageIncomplete())
        assertEquals(n, restored)
        val withUnknownKey = Nutrients.fromStorage(mapOf("energy" to 1.0, "future_nutrient" to 2.0))
        assertEquals(setOf(Nutrient.ENERGY), withUnknownKey.known)
    }

    @Test
    fun `одно и то же значение на 100 г точно сохраняется до показа`() {
        val per100 = Nutrients.of(Nutrient.ENERGY to 112.20227)
        val eaten = NutrientMath.forAmount(per100, 133.0)
        assertEquals(112.20227 * 1.33, eaten[Nutrient.ENERGY]!!, 1e-9)
    }
}
