package com.nutritiontracker.core.backup

import com.nutritiontracker.core.calc.ActivityLevel
import com.nutritiontracker.core.calc.DeficitMode
import com.nutritiontracker.core.calc.MacroPlan
import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.ServingKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import java.security.MessageDigest

data class BackupSummary(
    val foods: Int,
    val entries: Int,
    val recipes: Int,
    val weights: Int,
    val water: Int,
    val hasProfile: Boolean,
    val firstEpochDay: Long?,
    val lastEpochDay: Long?,
)

sealed interface BackupReadResult {
    data class Valid(val file: BackupFile, val summary: BackupSummary, val warnings: List<String>) : BackupReadResult
    data class Invalid(val errors: List<String>) : BackupReadResult
}

/**
 * Запись и чтение резервной копии. Перед импортом проверяются формат,
 * версия схемы, контрольная сумма, допустимость значений и ссылки между
 * записями. Файл с ошибками не импортируется вовсе — частичного импорта нет.
 */
object BackupCodec {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = true
        prettyPrint = false
        ignoreUnknownKeys = false
    }
    private val prettyJson = Json(json) { prettyPrint = true }
    private val headerJson = Json { ignoreUnknownKeys = true }

    fun encode(payload: BackupPayload, appVersion: String, exportedAt: Long): String {
        val file = BackupFile(
            appVersion = appVersion,
            exportedAt = exportedAt,
            payloadSha256 = checksum(payload),
            payload = payload,
        )
        return prettyJson.encodeToString(BackupFile.serializer(), file)
    }

    fun checksum(payload: BackupPayload): String =
        sha256(json.encodeToString(BackupPayload.serializer(), payload))

    /**
     * Контрольная сумма по самому JSON поля payload из файла, а не по
     * перекодированной модели: так копии старых версий проверяются честно,
     * даже если в модели с тех пор появились новые поля со значениями по умолчанию.
     */
    private fun checksumOfRawPayload(payload: JsonElement): String =
        sha256(json.encodeToString(JsonElement.serializer(), payload))

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** Ограничение размера файла копии: защищает от исчерпания памяти. */
    const val MAX_FILE_BYTES = 64L * 1024 * 1024

    fun read(text: String): BackupReadResult = try {
        readUnsafe(text)
    } catch (e: StackOverflowError) {
        BackupReadResult.Invalid(listOf("Структура файла слишком глубоко вложена — это не резервная копия приложения."))
    } catch (e: OutOfMemoryError) {
        BackupReadResult.Invalid(listOf("Файл слишком большой для разбора."))
    }

    private fun readUnsafe(text: String): BackupReadResult {
        if (text.isBlank()) return BackupReadResult.Invalid(listOf("Файл пустой."))
        if (text.length > MAX_FILE_BYTES) return BackupReadResult.Invalid(listOf("Файл слишком большой."))
        if (BackupCrypto.isEncrypted(text)) return BackupReadResult.Invalid(listOf("Копия зашифрована — нужен пароль."))
        val header = try {
            headerJson.decodeFromString(Header.serializer(), text)
        } catch (e: SerializationException) {
            return BackupReadResult.Invalid(listOf("Это не резервная копия приложения: файл не является корректным JSON нужной структуры."))
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.Invalid(listOf("Это не резервная копия приложения: файл не является корректным JSON нужной структуры."))
        }
        if (header.format != BackupFile.FORMAT) {
            return BackupReadResult.Invalid(listOf("Это не резервная копия приложения (формат «${header.format ?: "не указан"}»)."))
        }
        val version = header.schemaVersion
        if (version == null || version < 1) return BackupReadResult.Invalid(listOf("В файле не указана версия схемы."))
        if (version > BackupFile.SCHEMA_VERSION) {
            return BackupReadResult.Invalid(listOf("Копия создана более новой версией приложения (схема $version). Обновите приложение."))
        }
        val file = try {
            json.decodeFromString(BackupFile.serializer(), text)
        } catch (e: SerializationException) {
            return BackupReadResult.Invalid(listOf("Структура файла повреждена: ${e.message?.take(200)}"))
        } catch (e: IllegalArgumentException) {
            return BackupReadResult.Invalid(listOf("Структура файла повреждена: ${e.message?.take(200)}"))
        }
        val rawPayload = try {
            json.parseToJsonElement(text).jsonObject["payload"]
        } catch (e: SerializationException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        } ?: return BackupReadResult.Invalid(listOf("В файле нет данных."))
        if (!file.payloadSha256.equals(checksumOfRawPayload(rawPayload), ignoreCase = true)) {
            return BackupReadResult.Invalid(listOf("Контрольная сумма не совпадает: файл повреждён или изменён вручную."))
        }
        val errors = validate(file.payload)
        if (errors.isNotEmpty()) return BackupReadResult.Invalid(errors)
        return BackupReadResult.Valid(file, summarize(file.payload), emptyList())
    }

    @kotlinx.serialization.Serializable
    private data class Header(val format: String? = null, val schemaVersion: Int? = null)

    private fun summarize(p: BackupPayload): BackupSummary {
        val days = p.entries.map { it.epochDay } + p.weights.map { it.epochDay } + p.water.map { it.epochDay }
        return BackupSummary(
            foods = p.foods.size,
            entries = p.entries.size,
            recipes = p.recipes.size,
            weights = p.weights.size,
            water = p.water.size,
            hasProfile = p.profile != null,
            firstEpochDay = days.minOrNull(),
            lastEpochDay = days.maxOrNull(),
        )
    }

    private const val MIN_DAY = 0L // 1970-01-01
    private val BIRTH_MIN_DAY = java.time.LocalDate.of(1900, 1, 1).toEpochDay()
    private const val MAX_DAY = 84_000L // ≈ 2199 год

    fun validate(p: BackupPayload): List<String> {
        val errors = mutableListOf<String>()
        fun err(msg: String) { if (errors.size < 50) errors += msg }

        fun checkNutrients(where: String, values: Map<String, Double>, incomplete: List<String>) {
            for ((k, v) in values) {
                if (Nutrient.fromKey(k) == null) err("$where: неизвестный нутриент «$k»")
                if (!v.isFinite() || v < 0) err("$where: недопустимое значение $k = $v")
            }
            for (k in incomplete) if (k !in values) err("$where: пометка неполноты для отсутствующего нутриента «$k»")
        }
        fun checkUnit(where: String, unit: String) {
            if (AmountUnit.entries.none { it.name == unit }) err("$where: неизвестная единица «$unit»")
        }
        fun checkDay(where: String, day: Long) {
            if (day !in MIN_DAY..MAX_DAY) err("$where: недопустимая дата ($day)")
        }
        fun checkAmount(where: String, v: Double) {
            if (!v.isFinite() || v <= 0 || v > 100_000) err("$where: недопустимое количество $v")
        }
        fun <T> checkUniqueIds(name: String, items: List<T>, id: (T) -> Long) {
            val dup = items.groupBy(id).filter { it.value.size > 1 }.keys
            if (dup.isNotEmpty()) err("$name: повторяющиеся идентификаторы ${dup.take(5)}")
        }

        p.profile?.let { pr ->
            if (Sex.fromKey(pr.sex) == null) err("Профиль: неизвестный пол «${pr.sex}»")
            if (ActivityLevel.fromKey(pr.activityLevel) == null) err("Профиль: неизвестный уровень активности")
            if (DeficitMode.fromKey(pr.deficitMode) == null) err("Профиль: неизвестный режим дефицита")
            if (pr.heightCm !in 100.0..250.0) err("Профиль: недопустимый рост ${pr.heightCm}")
            if (pr.startWeightKg !in 20.0..400.0) err("Профиль: недопустимый начальный вес")
            if (pr.targetWeightKg !in 20.0..400.0) err("Профиль: недопустимый желаемый вес")
            // Дата рождения: от 1900 года до возраста 18 лет на момент проверки.
            val latestBirth = java.time.LocalDate.now().minusYears(18).toEpochDay()
            if (pr.birthEpochDay !in BIRTH_MIN_DAY..latestBirth) err("Профиль: недопустимая дата рождения")
            pr.customDeficitKcal?.let { if (!it.isFinite() || it !in 0.0..2000.0) err("Профиль: дефицит должен быть от 0 до 2000 ккал") }
            pr.manualGoalKcal?.let { if (!it.isFinite() || it !in 500.0..6000.0) err("Профиль: калорийность должна быть от 500 до 6000 ккал") }
            if (pr.deficitMode == DeficitMode.CUSTOM_DEFICIT.key && pr.customDeficitKcal == null) err("Профиль: не указан дефицит")
            if (pr.deficitMode == DeficitMode.MANUAL_GOAL.key && pr.manualGoalKcal == null) err("Профиль: не указана калорийность")
            if (pr.fatPercent !in MacroPlan.FAT_PERCENT_RANGE) err("Профиль: доля жиров должна быть от 20 до 40%")
            if (pr.proteinPercent !in 0..100 || pr.carbsPercent !in 0..100) err("Профиль: недопустимые доли БЖУ")
            if (!pr.proteinGPerKg.isFinite() || pr.proteinGPerKg !in MacroPlan.MIN_PROTEIN_G_PER_KG..MacroPlan.MAX_PROTEIN_G_PER_KG) {
                err("Профиль: белок должен быть от 0,8 до 2,2 г/кг")
            }
            pr.appliedAdaptiveTdee?.let { if (!it.isFinite() || it !in 1000.0..6000.0) err("Профиль: недопустимая оценка расхода") }
            if (pr.waterGoalMl !in 0..10_000) err("Профиль: недопустимая цель по воде")
        }

        checkUniqueIds("Продукты", p.foods) { it.id }
        checkUniqueIds("Записи дневника", p.entries) { it.id }
        checkUniqueIds("Рецепты", p.recipes) { it.id }
        checkUniqueIds("Ингредиенты", p.recipeIngredients) { it.id }
        checkUniqueIds("Вес", p.weights) { it.id }
        checkUniqueIds("Вода", p.water) { it.id }
        checkUniqueIds("Шаблоны", p.mealTemplates) { it.id }
        checkUniqueIds("Позиции шаблонов", p.mealTemplateItems) { it.id }

        val foodIds = p.foods.map { it.id }.toSet()
        val recipeIds = p.recipes.map { it.id }.toSet()
        val templateIds = p.mealTemplates.map { it.id }.toSet()

        for (f in p.foods) {
            val w = "Продукт «${f.name}»"
            if (f.name.isBlank()) err("Продукт #${f.id}: пустое название")
            if (FoodSource.fromKey(f.source) == null) err("$w: неизвестный источник «${f.source}»")
            checkUnit(w, f.basis)
            checkNutrients(w, f.nutrients, f.incompleteNutrients)
            for (s in f.servings) {
                if (!s.amount.isFinite() || s.amount <= 0) err("$w: недопустимый размер порции")
                if (ServingKind.entries.none { it.name == s.kind }) err("$w: неизвестный вид порции «${s.kind}»")
            }
            if (f.recipeId != null && f.recipeId !in recipeIds) err("$w: ссылка на несуществующий рецепт")
        }
        val dupExternal = p.foods.filter { it.externalId != null }.groupBy { it.source to it.externalId }.filter { it.value.size > 1 }
        if (dupExternal.isNotEmpty()) err("Продукты: один и тот же продукт источника записан несколько раз")

        for (e in p.entries) {
            val w = "Запись дневника #${e.id}"
            checkDay(w, e.epochDay)
            if (MealType.fromKey(e.mealType) == null) err("$w: неизвестный приём пищи «${e.mealType}»")
            checkAmount(w, e.amount)
            checkUnit(w, e.unit)
            if (FoodSource.fromKey(e.source) == null) err("$w: неизвестный источник")
            checkNutrients(w, e.nutrientsPer100, e.incompleteNutrients)
            if (e.foodId != null && e.foodId !in foodIds) err("$w: ссылка на несуществующий продукт #${e.foodId}")
        }
        for (r in p.recipes) {
            val w = "Рецепт «${r.name}»"
            if (r.cookedWeightG != null && (!r.cookedWeightG.isFinite() || r.cookedWeightG <= 0)) err("$w: недопустимый вес готового блюда")
            if (r.servingsCount != null && r.servingsCount <= 0) err("$w: недопустимое число порций")
            if (r.foodId != null && r.foodId !in foodIds) err("$w: ссылка на несуществующий продукт")
        }
        for (i in p.recipeIngredients) {
            val w = "Ингредиент «${i.name}»"
            if (i.recipeId !in recipeIds) err("$w: ссылка на несуществующий рецепт #${i.recipeId}")
            if (i.foodId != null && i.foodId !in foodIds) err("$w: ссылка на несуществующий продукт")
            checkAmount(w, i.amount)
            checkUnit(w, i.unit)
            checkNutrients(w, i.nutrientsPer100, i.incompleteNutrients)
        }
        val dupWeightDays = p.weights.groupBy { it.epochDay }.filter { it.value.size > 1 }
        if (dupWeightDays.isNotEmpty()) err("Вес: несколько записей на одну дату")
        for (wt in p.weights) {
            val w = "Запись веса #${wt.id}"
            checkDay(w, wt.epochDay)
            if (listOf(wt.weightKg, wt.waistCm, wt.hipsCm, wt.chestCm, wt.neckCm, wt.armCm, wt.thighCm).all { it == null }) {
                err("$w: пустая запись без веса и измерений")
            }
            wt.weightKg?.let { if (it !in 20.0..400.0) err("$w: недопустимый вес $it") }
            listOf(wt.waistCm, wt.hipsCm, wt.chestCm, wt.neckCm, wt.armCm, wt.thighCm).filterNotNull().forEach {
                if (it !in 5.0..300.0) err("$w: недопустимый обхват $it")
            }
        }
        for (wa in p.water) {
            checkDay("Вода #${wa.id}", wa.epochDay)
            if (wa.amountMl !in 1..10_000) err("Вода #${wa.id}: недопустимый объём ${wa.amountMl}")
        }
        for (g in p.nutrientGoals) {
            if (Nutrient.fromKey(g.nutrientKey) == null) err("Цели: неизвестный нутриент «${g.nutrientKey}»")
            listOfNotNull(g.target, g.maximum).forEach { if (!it.isFinite() || it < 0) err("Цели: недопустимое значение") }
        }
        for (f in p.favorites) if (f.foodId !in foodIds) err("Избранное: ссылка на несуществующий продукт #${f.foodId}")
        for (t in p.mealTemplates) if (t.mealType != null && MealType.fromKey(t.mealType) == null) err("Шаблон «${t.name}»: неизвестный приём пищи")
        for (it in p.mealTemplateItems) {
            val w = "Позиция шаблона «${it.name}»"
            if (it.templateId !in templateIds) err("$w: ссылка на несуществующий шаблон")
            if (it.foodId != null && it.foodId !in foodIds) err("$w: ссылка на несуществующий продукт")
            checkAmount(w, it.amount)
            checkUnit(w, it.unit)
            checkNutrients(w, it.nutrientsPer100, it.incompleteNutrients)
        }
        return errors
    }
}
