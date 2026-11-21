package com.nutritiontracker.core

import com.nutritiontracker.core.backup.BackupCodec
import com.nutritiontracker.core.backup.BackupPayload
import com.nutritiontracker.core.backup.BackupReadResult
import com.nutritiontracker.core.backup.DiaryCsvExporter
import com.nutritiontracker.core.backup.FavoriteDto
import com.nutritiontracker.core.backup.FoodDto
import com.nutritiontracker.core.backup.FoodEntryDto
import com.nutritiontracker.core.backup.ProfileDto
import com.nutritiontracker.core.backup.RecipeDto
import com.nutritiontracker.core.backup.RecipeIngredientDto
import com.nutritiontracker.core.backup.WaterDto
import com.nutritiontracker.core.backup.WeightDto
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BackupTest {
    private val now = 1_795_000_000_000L
    private val day = LocalDate.of(2026, 11, 21).toEpochDay()

    private fun payload() = BackupPayload(
        profile = ProfileDto(
            sex = "female", birthEpochDay = LocalDate.of(1990, 5, 1).toEpochDay(), heightCm = 168.0,
            startWeightKg = 74.0, targetWeightKg = 66.0, activityLevel = "light", deficitMode = "small",
            proteinPercent = 25, fatPercent = 30, carbsPercent = 45, waterGoalMl = 2000,
            adaptiveEnabled = false, createdAt = now, updatedAt = now,
        ),
        foods = listOf(
            FoodDto(
                id = 1, name = "Куриное филе отварное", source = "user", basis = "GRAM",
                nutrients = mapOf("energy" to 165.0, "protein" to 31.0, "fat" to 3.6, "carbs" to 0.0),
                createdAt = now, updatedAt = now,
            ),
            FoodDto(
                id = 2, name = "Куриный суп", source = "recipe", basis = "GRAM", recipeId = 10,
                nutrients = mapOf("energy" to 44.0, "vitamin_c" to 4.6), incompleteNutrients = listOf("vitamin_c"),
                createdAt = now, updatedAt = now,
            ),
        ),
        entries = listOf(
            FoodEntryDto(
                id = 100, foodId = 1, epochDay = day, mealType = "lunch", amount = 200.0, unit = "GRAM",
                foodName = "Куриное филе отварное", source = "user",
                nutrientsPer100 = mapOf("energy" to 165.0, "protein" to 31.0), createdAt = now, updatedAt = now,
            ),
            // Продукт удалён, но запись дневника хранит снимок.
            FoodEntryDto(
                id = 101, foodId = null, epochDay = day, mealType = "snack", amount = 1.5, unit = "GRAM",
                foodName = "Удалённый продукт", source = "off", nutrientsPer100 = mapOf("energy" to 400.0),
                createdAt = now, updatedAt = now,
            ),
        ),
        recipes = listOf(RecipeDto(id = 10, name = "Куриный суп", cookedWeightG = 1400.0, foodId = 2, createdAt = now, updatedAt = now)),
        recipeIngredients = listOf(
            RecipeIngredientDto(id = 1, recipeId = 10, foodId = 1, name = "Куриное филе", amount = 200.0, unit = "GRAM", nutrientsPer100 = mapOf("energy" to 165.0), position = 0),
        ),
        weights = listOf(WeightDto(id = 1, epochDay = day, weightKg = 73.4, waistCm = 80.0, createdAt = now, updatedAt = now)),
        water = listOf(WaterDto(id = 1, epochDay = day, amountMl = 250, createdAt = now)),
        favorites = listOf(FavoriteDto(id = 1, foodId = 1, createdAt = now)),
    )

    @Test
    fun `копия записывается и читается без потерь`() {
        val text = BackupCodec.encode(payload(), "1.0.0", now)
        val r = BackupCodec.read(text)
        assertTrue(r.toString(), r is BackupReadResult.Valid)
        r as BackupReadResult.Valid
        assertEquals(payload(), r.file.payload)
        assertEquals(2, r.summary.entries)
        assertEquals(day, r.summary.firstEpochDay)
    }

    @Test
    fun `изменённый файл не проходит проверку контрольной суммы`() {
        val text = BackupCodec.encode(payload(), "1.0.0", now).replace("\"amount\": 200.0", "\"amount\": 2000.0")
        val r = BackupCodec.read(text)
        assertTrue(r is BackupReadResult.Invalid)
        assertTrue((r as BackupReadResult.Invalid).errors.single().contains("Контрольная сумма"))
    }

    @Test
    fun `посторонний и повреждённый файл отклоняются`() {
        assertTrue(BackupCodec.read("") is BackupReadResult.Invalid)
        assertTrue(BackupCodec.read("{\"hello\": 1}") is BackupReadResult.Invalid)
        assertTrue(BackupCodec.read("не json") is BackupReadResult.Invalid)
        val text = BackupCodec.encode(payload(), "1.0.0", now)
        assertTrue(BackupCodec.read(text.dropLast(40)) is BackupReadResult.Invalid)
        val future = text.replace("\"schemaVersion\": 1", "\"schemaVersion\": 99")
        val r = BackupCodec.read(future) as BackupReadResult.Invalid
        assertTrue(r.errors.single().contains("более новой версией"))
    }

    @Test
    fun `битые ссылки и недопустимые значения находятся`() {
        val bad = payload().copy(
            entries = payload().entries + FoodEntryDto(
                id = 102, foodId = 999, epochDay = day, mealType = "brunch", amount = -5.0, unit = "CUP",
                foodName = "x", source = "user", nutrientsPer100 = mapOf("energy" to -1.0, "mystery" to 1.0),
                createdAt = now, updatedAt = now,
            ),
            favorites = listOf(FavoriteDto(id = 1, foodId = 555, createdAt = now)),
        )
        val errors = BackupCodec.validate(bad)
        assertTrue(errors.any { it.contains("несуществующий продукт #999") })
        assertTrue(errors.any { it.contains("неизвестный приём пищи") })
        assertTrue(errors.any { it.contains("недопустимое количество") })
        assertTrue(errors.any { it.contains("неизвестная единица") })
        assertTrue(errors.any { it.contains("недопустимое значение energy") })
        assertTrue(errors.any { it.contains("неизвестный нутриент «mystery»") })
        assertTrue(errors.any { it.contains("Избранное") })
        // Файл с ошибками целиком отклоняется.
        assertTrue(BackupCodec.read(BackupCodec.encode(bad, "1.0.0", now)) is BackupReadResult.Invalid)
    }

    @Test
    fun `CSV дневника — неизвестное пустая ячейка, ноль это ноль`() {
        val rows = listOf(
            DiaryCsvExporter.Row(
                LocalDate.of(2026, 11, 21), MealType.LUNCH, "Суп; домашний", null, 250.0, AmountUnit.GRAM,
                Nutrients.of(mapOf(Nutrient.ENERGY to 44.0, Nutrient.CARBS to 0.0, Nutrient.VITAMIN_C to 4.6), setOf(Nutrient.VITAMIN_C)),
            ),
        )
        val csv = DiaryCsvExporter.export(rows)
        assertTrue(csv.startsWith(DiaryCsvExporter.BOM))
        val lines = csv.removePrefix(DiaryCsvExporter.BOM).trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        val header = lines[0].split(";")
        val cells = lines[1].split(";").let { it } // поле с «;» в кавычках
        assertTrue(lines[1].startsWith("2026-11-21;Обед;\"Суп; домашний\";;250;г;110;"))
        val energyIdx = header.indexOf("Энергетическая ценность, ккал")
        val carbsIdx = header.indexOf("Углеводы, г")
        val ironIdx = header.indexOf("Железо, мг")
        val shift = 1 // из-за «;» внутри названия при простом split
        assertEquals("110", cells[energyIdx + shift])
        assertEquals("0", cells[carbsIdx + shift])
        assertEquals("", cells[ironIdx + shift])
        assertTrue(lines[1].endsWith("Витамин C"))
        assertEquals("11,5", cells[header.indexOf("Витамин C, мг") + shift])
    }
}
