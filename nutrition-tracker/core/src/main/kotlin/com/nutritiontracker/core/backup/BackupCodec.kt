package com.nutritiontracker.core.backup

import com.nutritiontracker.core.calc.ActivityLevel
import com.nutritiontracker.core.calc.DeficitMode
import com.nutritiontracker.core.calc.Sex
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.ServingKind
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
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

    fun encode(payload: BackupPayload, appVersion: String, exportedAt: Long): String {
        val file = BackupFile(
            appVersion = appVersion,
            exportedAt = exportedAt,
            payloadSha256 = checksum(payload),
            payload = payload,
        )
        return prettyJson.encodeToString(BackupFile.serializer(), file)
    }

    fun checksum(payload: BackupPayload): String {
        val canonical = json.encodeToString(BackupPayload.serializer(), payload)
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun read(text: String): BackupReadResult {
        if (text.isBlank()) return BackupReadResult.Invalid(listOf("Файл пустой."))
        val header = try {
            Json { ignoreUnknownKeys = true }.decodeFromString(Header.serializer(), text)
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
        if (!file.payloadSha256.equals(checksum(file.payload), ignoreCase = true)) {
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
            if (pr.proteinPercent + pr.fatPercent + pr.carbsPercent != 100) err("Профиль: сумма долей БЖУ не равна 100%")
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
