package com.nutritiontracker.data

import android.content.Context
import androidx.room.withTransaction
import com.nutritiontracker.core.backup.BackupCodec
import com.nutritiontracker.core.backup.BackupPayload
import com.nutritiontracker.core.backup.BackupReadResult
import com.nutritiontracker.core.backup.DiaryCsvExporter
import com.nutritiontracker.core.backup.FavoriteDto
import com.nutritiontracker.core.backup.FoodDto
import com.nutritiontracker.core.backup.FoodEntryDto
import com.nutritiontracker.core.backup.MealTemplateDto
import com.nutritiontracker.core.backup.MealTemplateItemDto
import com.nutritiontracker.core.backup.NutrientGoalDto
import com.nutritiontracker.core.backup.ProfileDto
import com.nutritiontracker.core.backup.RecipeDto
import com.nutritiontracker.core.backup.RecipeIngredientDto
import com.nutritiontracker.core.backup.SearchHistoryDto
import com.nutritiontracker.core.backup.SettingsDto
import com.nutritiontracker.core.backup.WaterDto
import com.nutritiontracker.core.backup.WeightDto
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.FavoriteEntity
import com.nutritiontracker.data.db.FoodEntity
import com.nutritiontracker.data.db.FoodEntryEntity
import com.nutritiontracker.data.db.MealTemplateEntity
import com.nutritiontracker.data.db.MealTemplateItemEntity
import com.nutritiontracker.data.db.NutrientGoalEntity
import com.nutritiontracker.data.db.ProfileEntity
import com.nutritiontracker.data.db.RecipeEntity
import com.nutritiontracker.data.db.RecipeIngredientEntity
import com.nutritiontracker.data.db.SearchHistoryEntity
import com.nutritiontracker.data.db.WaterEntryEntity
import com.nutritiontracker.data.db.WeightEntryEntity
import java.io.File
import java.time.LocalDate

/**
 * Резервная копия всех данных в JSON и экспорт дневника в CSV.
 * Импорт заменяет данные целиком и только после проверки файла; перед
 * заменой текущие данные автоматически сохраняются во внутреннюю папку приложения.
 */
class BackupRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val settings: SettingsRepository,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun buildPayload(): BackupPayload {
        val s = settings.current()
        return db.withTransaction {
            BackupPayload(
                profile = db.profileDao().get()?.let { p ->
                    ProfileDto(
                        p.sex, p.birthEpochDay, p.heightCm, p.startWeightKg, p.targetWeightKg, p.activityLevel, p.deficitMode,
                        p.customDeficitKcal, p.manualGoalKcal, p.proteinPercent, p.fatPercent, p.carbsPercent, p.waterGoalMl,
                        p.adaptiveEnabled, p.appliedAdaptiveTdee, p.adaptiveUpdatedEpochDay, p.createdAt, p.updatedAt,
                    )
                },
                foods = db.foodDao().all().map { f ->
                    FoodDto(
                        id = f.id, name = f.name, nameEn = f.nameEn, aliases = StorageJson.readStrings(f.aliases), brand = f.brand,
                        category = f.category, source = f.source, externalId = f.externalId, basis = f.basis,
                        nutrients = StorageJson.mapValues(f.nutrients), incompleteNutrients = StorageJson.readStrings(f.incompleteNutrients),
                        servings = StorageJson.readServings(f.servings).map { com.nutritiontracker.core.backup.ServingDto(it.label, it.amount, it.kind.name, it.sourceNote) },
                        sourceUpdated = f.sourceUpdated, dataNotes = StorageJson.readStrings(f.dataNotes), recipeId = f.recipeId,
                        detailsLoaded = f.detailsLoaded, fetchedAt = f.fetchedAt, createdAt = f.createdAt, updatedAt = f.updatedAt,
                        useCount = f.useCount, lastUsedAt = f.lastUsedAt,
                    )
                },
                entries = db.entryDao().all().map { e ->
                    FoodEntryDto(
                        e.id, e.foodId, e.epochDay, e.mealType, e.amount, e.unit, e.foodName, e.brand, e.source,
                        StorageJson.mapValues(e.nutrientsPer100), StorageJson.readStrings(e.incompleteNutrients), e.servingLabel,
                        e.createdAt, e.updatedAt,
                    )
                },
                recipes = db.recipeDao().all().map { RecipeDto(it.id, it.name, it.cookedWeightG, it.servingsCount, it.foodId, it.notes, it.createdAt, it.updatedAt) },
                recipeIngredients = db.recipeDao().allIngredients().map {
                    RecipeIngredientDto(
                        it.id, it.recipeId, it.foodId, it.name, it.amount, it.unit, StorageJson.mapValues(it.nutrientsPer100),
                        StorageJson.readStrings(it.incompleteNutrients), it.position,
                    )
                },
                weights = db.weightDao().all().map { WeightDto(it.id, it.epochDay, it.weightKg, it.waistCm, it.hipsCm, it.chestCm, it.neckCm, it.armCm, it.thighCm, it.note, it.createdAt, it.updatedAt) },
                water = db.waterDao().all().map { WaterDto(it.id, it.epochDay, it.amountMl, it.createdAt) },
                nutrientGoals = db.goalDao().all().map { NutrientGoalDto(it.id, it.nutrientKey, it.target, it.maximum, it.unit, it.source, it.updatedAt) },
                favorites = db.favoriteDao().all().map { FavoriteDto(it.id, it.foodId, it.createdAt) },
                searchHistory = db.searchHistoryDao().all().map { SearchHistoryDto(it.id, it.query, it.lastUsedAt) },
                mealTemplates = db.templateDao().all().map { MealTemplateDto(it.id, it.name, it.mealType, it.createdAt) },
                mealTemplateItems = db.templateDao().allItems().map {
                    MealTemplateItemDto(
                        it.id, it.templateId, it.foodId, it.name, it.brand, it.source, it.amount, it.unit,
                        StorageJson.mapValues(it.nutrientsPer100), StorageJson.readStrings(it.incompleteNutrients), it.position,
                    )
                },
                // Личный ключ USDA в копию не попадает.
                settings = SettingsDto(s.onlineSearchEnabled, s.usdaEnabled, s.openFoodFactsEnabled, s.autoRefreshEnabled),
            )
        }
    }

    suspend fun exportJson(): String = BackupCodec.encode(buildPayload(), appVersion, clock())

    suspend fun exportCsv(from: LocalDate?, to: LocalDate?): String {
        val entries = db.entryDao().all().map { it.toEntry() }
            .filter { (from == null || !it.date.isBefore(from)) && (to == null || !it.date.isAfter(to)) }
        return DiaryCsvExporter.export(
            entries.map { DiaryCsvExporter.Row(it.date, it.meal, it.foodName, it.brand, it.amount, it.unit, it.per100) },
        )
    }

    fun read(text: String): BackupReadResult = BackupCodec.read(text)

    /** Страховочные копии, созданные перед импортом, — новые первыми. */
    fun safetyCopies(): List<File> =
        File(context.filesDir, "auto-backups").listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() } ?: emptyList()

    /** Сохраняет текущие данные во внутреннюю папку перед импортом. Возвращает путь к файлу. */
    suspend fun saveSafetyCopy(): File {
        val dir = File(context.filesDir, "auto-backups").apply { mkdirs() }
        // Храним только несколько последних страховочных копий.
        dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(4)?.forEach { it.delete() }
        val file = File(dir, "before-import-${clock()}.json")
        file.writeText(exportJson())
        return file
    }

    /** Заменяет все данные содержимым проверенной копии. Всё или ничего. */
    suspend fun replaceAll(valid: BackupReadResult.Valid) {
        val p = valid.file.payload
        db.withTransaction {
            db.favoriteDao().deleteAll()
            db.templateDao().deleteAllItems()
            db.templateDao().deleteAll()
            db.entryDao().deleteAll()
            db.recipeDao().deleteAllIngredients()
            db.recipeDao().deleteAll()
            db.foodDao().deleteAll()
            db.weightDao().deleteAll()
            db.waterDao().deleteAll()
            db.goalDao().deleteAll()
            db.searchHistoryDao().deleteAll()
            db.profileDao().deleteAll()

            p.profile?.let { d ->
                db.profileDao().upsert(
                    ProfileEntity(
                        1, d.sex, d.birthEpochDay, d.heightCm, d.startWeightKg, d.targetWeightKg, d.activityLevel, d.deficitMode,
                        d.customDeficitKcal, d.manualGoalKcal, d.proteinPercent, d.fatPercent, d.carbsPercent, d.waterGoalMl,
                        d.adaptiveEnabled, d.appliedAdaptiveTdee, d.adaptiveUpdatedEpochDay, d.createdAt, d.updatedAt,
                    ),
                )
            }
            db.foodDao().insertAll(
                p.foods.map { f ->
                    val servings = f.servings.mapNotNull { s ->
                        val kind = com.nutritiontracker.core.model.ServingKind.entries.firstOrNull { it.name == s.kind } ?: return@mapNotNull null
                        com.nutritiontracker.core.model.ServingSize(s.label, s.amount, kind, s.sourceNote)
                    }
                    val entity = FoodEntity(
                        id = f.id, name = f.name, nameEn = f.nameEn, aliases = StorageJson.strings(f.aliases), brand = f.brand,
                        category = f.category, source = f.source, externalId = f.externalId, basis = f.basis,
                        nutrients = StorageJson.nutrients(com.nutritiontracker.core.model.Nutrients.fromStorage(f.nutrients, f.incompleteNutrients)),
                        incompleteNutrients = StorageJson.strings(f.incompleteNutrients.sorted()),
                        servings = StorageJson.servings(servings), sourceUpdated = f.sourceUpdated, dataNotes = StorageJson.strings(f.dataNotes),
                        recipeId = f.recipeId, detailsLoaded = f.detailsLoaded, fetchedAt = f.fetchedAt, createdAt = f.createdAt,
                        updatedAt = f.updatedAt, useCount = f.useCount, lastUsedAt = f.lastUsedAt, searchIndex = "",
                    )
                    entity.copy(searchIndex = buildSearchIndex(entity.toItem().data, f.aliases))
                },
            )
            db.recipeDao().insertAll(p.recipes.map { RecipeEntity(it.id, it.name, it.cookedWeightG, it.servingsCount, it.foodId, it.notes, it.createdAt, it.updatedAt) })
            db.recipeDao().insertIngredients(
                p.recipeIngredients.map {
                    RecipeIngredientEntity(it.id, it.recipeId, it.foodId, it.name, it.amount, it.unit, nutrientsJson(it.nutrientsPer100, it.incompleteNutrients), StorageJson.strings(it.incompleteNutrients.sorted()), it.position)
                },
            )
            db.entryDao().insertAll(
                p.entries.map {
                    FoodEntryEntity(
                        it.id, it.foodId, it.epochDay, it.mealType, it.amount, it.unit, it.foodName, it.brand, it.source,
                        nutrientsJson(it.nutrientsPer100, it.incompleteNutrients), StorageJson.strings(it.incompleteNutrients.sorted()),
                        it.servingLabel, it.createdAt, it.updatedAt,
                    )
                },
            )
            db.weightDao().insertAll(p.weights.map { WeightEntryEntity(it.id, it.epochDay, it.weightKg, it.waistCm, it.hipsCm, it.chestCm, it.neckCm, it.armCm, it.thighCm, it.note, it.createdAt, it.updatedAt) })
            db.waterDao().insertAll(p.water.map { WaterEntryEntity(it.id, it.epochDay, it.amountMl, it.createdAt) })
            db.goalDao().insertAll(p.nutrientGoals.map { NutrientGoalEntity(it.id, it.nutrientKey, it.target, it.maximum, it.unit, it.source, it.updatedAt) })
            db.favoriteDao().insertAll(p.favorites.map { FavoriteEntity(it.id, it.foodId, it.createdAt) })
            db.searchHistoryDao().insertAll(p.searchHistory.map { SearchHistoryEntity(it.id, it.query, it.lastUsedAt) })
            db.templateDao().insertAll(p.mealTemplates.map { MealTemplateEntity(it.id, it.name, it.mealType, it.createdAt) })
            db.templateDao().insertItems(
                p.mealTemplateItems.map {
                    MealTemplateItemEntity(
                        it.id, it.templateId, it.foodId, it.name, it.brand, it.source, it.amount, it.unit,
                        nutrientsJson(it.nutrientsPer100, it.incompleteNutrients), StorageJson.strings(it.incompleteNutrients.sorted()), it.position,
                    )
                },
            )
        }
        p.settings?.let { s ->
            settings.update { it.copy(onlineSearchEnabled = s.onlineSearchEnabled, usdaEnabled = s.usdaEnabled, openFoodFactsEnabled = s.openFoodFactsEnabled, autoRefreshEnabled = s.autoRefreshEnabled) }
        }
    }

    private fun nutrientsJson(values: Map<String, Double>, incomplete: List<String>): String =
        StorageJson.nutrients(com.nutritiontracker.core.model.Nutrients.fromStorage(values, incomplete))
}
