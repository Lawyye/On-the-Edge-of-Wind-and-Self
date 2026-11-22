package com.nutritiontracker

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.nutritiontracker.core.backup.BackupReadResult
import com.nutritiontracker.core.calc.NutrientMath
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.data.BackupRepository
import com.nutritiontracker.data.BodyEntry
import com.nutritiontracker.data.BodyRepository
import com.nutritiontracker.data.DiaryRepository
import com.nutritiontracker.data.FoodRepository
import com.nutritiontracker.data.RecipeDraft
import com.nutritiontracker.data.RecipeIngredient
import com.nutritiontracker.data.RecipeRepository
import com.nutritiontracker.data.SettingsRepository
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.toNewEntry
import com.nutritiontracker.remote.RemoteFood
import com.nutritiontracker.remote.RemoteJson
import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DataLayerTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var foods: FoodRepository
    private lateinit var diary: DiaryRepository
    private lateinit var recipes: RecipeRepository
    private lateinit var body: BodyRepository
    private val dbName = "test-nutrition.db"
    private val day = LocalDate.of(2026, 11, 22)
    private val eps = 1e-9

    private fun open(): AppDatabase = AppDatabase.create(context, dbName)

    private fun wire(database: AppDatabase) {
        db = database
        // Сетевые клиенты в этих тестах не вызываются.
        val http = RemoteJson.defaultOkHttp()
        foods = FoodRepository(db, UsdaClient(http, { "" }, "http://127.0.0.1:9/"), OpenFoodFactsClient(http, "test", "http://127.0.0.1:9/"))
        diary = DiaryRepository(db)
        recipes = RecipeRepository(db)
        body = BodyRepository(db)
    }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        wire(open())
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    private val chicken = FoodData(
        name = "Куриное филе отварное",
        source = FoodSource.USER,
        nutrients = Nutrients.of(Nutrient.ENERGY to 165.0, Nutrient.PROTEIN to 31.0, Nutrient.FAT to 3.6, Nutrient.CARBS to 0.0),
        servings = listOf(ServingSize("1 филе", 120.0, ServingKind.PIECE, "Ваши данные")),
    )

    @Test
    fun unknownStaysUnknownAndZeroStaysZeroAfterStorage() = runBlocking<Unit> {
        val id = foods.createUserFood(chicken, listOf("курица"))
        val f = foods.get(id)!!
        assertEquals(0.0, f.nutrients[Nutrient.CARBS]!!, 0.0)
        assertTrue(f.nutrients.isKnown(Nutrient.CARBS))
        assertNull(f.nutrients[Nutrient.VITAMIN_C])
        assertEquals(1, f.data.servings.size)
        assertEquals(120.0, f.data.servings.first().amount, eps)
    }

    @Test
    fun diaryKeepsSnapshotWhenFoodChangesOrIsDeleted() = runBlocking<Unit> {
        val id = foods.createUserFood(chicken, emptyList())
        diary.add(foods.get(id)!!.toNewEntry(200.0), day, MealType.LUNCH)
        foods.updateUserFood(id, chicken.copy(nutrients = Nutrients.of(Nutrient.ENERGY to 999.0)), emptyList())
        val entry = diary.observeDay(day).first().single()
        assertEquals(330.0, entry.eaten[Nutrient.ENERGY]!!, eps)
        assertEquals(62.0, entry.eaten[Nutrient.PROTEIN]!!, eps)
        foods.delete(id)
        val after = diary.observeDay(day).first().single()
        assertNull(after.foodId)
        assertEquals(330.0, after.eaten[Nutrient.ENERGY]!!, eps)
    }

    @Test
    fun editingEntryRecalculatesFromSnapshot() = runBlocking<Unit> {
        val id = foods.createUserFood(chicken, emptyList())
        diary.add(foods.get(id)!!.toNewEntry(100.0), day, MealType.LUNCH)
        val e = diary.observeDay(day).first().single()
        diary.update(e.id, 250.0, MealType.DINNER, day.minusDays(1))
        assertTrue(diary.observeDay(day).first().isEmpty())
        val moved = diary.observeDay(day.minusDays(1)).first().single()
        assertEquals(MealType.DINNER, moved.meal)
        assertEquals(165.0 * 2.5, moved.eaten[Nutrient.ENERGY]!!, eps)
    }

    @Test
    fun addAllIsAllOrNothing() = runBlocking<Unit> {
        val id = foods.createUserFood(chicken, emptyList())
        val good = foods.get(id)!!.toNewEntry(100.0)
        val bad = good.copy(amount = 0.0)
        val failed = runCatching { diary.addAll(listOf(good, bad), day, MealType.BREAKFAST) }
        assertTrue(failed.isFailure)
        assertTrue(diary.observeDay(day).first().isEmpty())
        diary.addAll(listOf(good, good.copy(amount = 50.0)), day, MealType.BREAKFAST)
        assertEquals(2, diary.observeDay(day).first().size)
    }

    @Test
    fun recipeBecomesFoodPer100CookedAndKeepsIncompleteness() = runBlocking<Unit> {
        val chickenId = foods.createUserFood(chicken, emptyList())
        val c = foods.get(chickenId)!!
        val draft = RecipeDraft(
            name = "Куриный суп",
            ingredients = listOf(
                RecipeIngredient(0, c.id, c.name, 200.0, AmountUnit.GRAM, c.nutrients),
                RecipeIngredient(0, null, "Картофель", 300.0, AmountUnit.GRAM, Nutrients.of(Nutrient.ENERGY to 77.0, Nutrient.VITAMIN_C to 19.7)),
                RecipeIngredient(0, null, "Вода", 1000.0, AmountUnit.GRAM, Nutrients.of(Nutrient.ENERGY to 0.0)),
            ),
            cookedWeightG = 1400.0,
            servingsCount = 4,
        )
        val recipeId = recipes.save(draft)
        val recipe = recipes.observe(recipeId).first()!!
        val food = foods.get(recipe.foodId!!)!!
        assertEquals(FoodSource.RECIPE, food.source)
        assertEquals((330.0 + 231.0) / 1400.0 * 100.0, food.nutrients[Nutrient.ENERGY]!!, eps)
        assertTrue(Nutrient.VITAMIN_C in food.nutrients.incomplete)
        assertEquals(350.0, food.data.servings.first { it.kind == ServingKind.PORTION }.amount, eps)

        // Без веса готового блюда рецепт перестаёт быть продуктом для дневника.
        recipes.save(recipes.loadDraft(recipeId)!!.copy(cookedWeightG = null))
        assertNull(recipes.observe(recipeId).first()!!.foodId)
        assertNull(foods.get(food.id))
    }

    @Test
    fun remoteFoodIsCachedOnceAndFoundOfflineByRussianQuery() = runBlocking<Unit> {
        val remote = RemoteFood(
            FoodData(
                name = "Apples, raw, with skin", nameEn = "Apples, raw, with skin", source = FoodSource.USDA_SR_LEGACY, externalId = "171688",
                nutrients = Nutrients.of(Nutrient.ENERGY to 52.0, Nutrient.VITAMIN_C to 4.6),
            ),
            isFullRecord = false,
        )
        val id1 = foods.saveRemote(remote, "яблоко")
        val id2 = foods.saveRemote(remote, "яблоко")
        assertEquals(id1, id2)
        val hits = foods.searchLocal("яблоки")
        assertEquals(id1, hits.first().food.id)
        assertEquals(listOf("Яблоко"), foods.get(id1)!!.aliases)
        assertFalse(foods.get(id1)!!.detailsLoaded)
    }

    @Test
    fun weightEntriesAreOnePerDayAndMergeMeasurements() = runBlocking<Unit> {
        body.saveBody(BodyEntry(0, day, 80.0, null, null, null, null, null, null, null))
        body.saveBody(BodyEntry(0, day, null, 85.0, null, null, null, null, null, null))
        val list = body.observeBody().first()
        assertEquals(1, list.size)
        assertEquals(80.0, list.single().weightKg!!, eps)
        assertEquals(85.0, list.single().waistCm!!, eps)
        assertTrue(runCatching { body.saveBody(BodyEntry(0, day, 5.0, null, null, null, null, null, null, null)) }.isFailure)
    }

    @Test
    fun dataSurvivesClosingAndReopeningTheDatabase() = runBlocking<Unit> {
        val id = foods.createUserFood(chicken, emptyList())
        diary.add(foods.get(id)!!.toNewEntry(150.0), day, MealType.DINNER)
        body.addWater(day, 250)
        db.close()
        wire(open())
        val e = diary.observeDay(day).first().single()
        assertEquals(165.0 * 1.5, e.eaten[Nutrient.ENERGY]!!, eps)
        assertEquals(250, body.observeWater(day).first().sumOf { it.amountMl })
        assertNotNull(foods.get(id))
    }

    @Test
    fun backupRoundTripRestoresEverything() = runBlocking<Unit> {
        val settings = SettingsRepository(context)
        val backup = BackupRepository(context, db, settings, "test")
        val id = foods.createUserFood(chicken, listOf("курица"))
        foods.setFavorite(id, true)
        diary.add(foods.get(id)!!.toNewEntry(200.0), day, MealType.LUNCH)
        body.saveBody(BodyEntry(0, day, 79.5, 84.0, null, null, null, null, null, "утро"))
        body.addWater(day, 500)
        val text = backup.exportJson()
        val before = backup.buildPayload()

        // Портим текущие данные, затем восстанавливаем из копии.
        diary.add(foods.get(id)!!.toNewEntry(999.0), day, MealType.SNACK)
        val read = backup.read(text)
        assertTrue(read is BackupReadResult.Valid)
        val safety = backup.saveSafetyCopy()
        assertTrue(safety.exists() && safety.length() > 0)
        backup.replaceAll(read as BackupReadResult.Valid)

        val after = backup.buildPayload()
        assertEquals(before.copy(settings = null), after.copy(settings = null))
        val entry = diary.observeDay(day).first().single()
        assertEquals(330.0, NutrientMath.forAmount(entry.per100, entry.amount)[Nutrient.ENERGY]!!, eps)
        assertTrue(foods.observeFavoriteIds().first().contains(id))
        File(context.filesDir, "auto-backups").deleteRecursively()
    }

    @Test
    fun corruptedBackupDoesNotTouchData() = runBlocking<Unit> {
        val settings = SettingsRepository(context)
        val backup = BackupRepository(context, db, settings, "test")
        val id = foods.createUserFood(chicken, emptyList())
        diary.add(foods.get(id)!!.toNewEntry(100.0), day, MealType.LUNCH)
        val tampered = backup.exportJson().replace("\"amount\": 100.0", "\"amount\": 1000.0")
        assertTrue(backup.read(tampered) is BackupReadResult.Invalid)
        assertEquals(1, diary.observeDay(day).first().size)
    }

    /**
     * Миграция 1 → 2 на настоящей схеме версии 1 (app/schemas): база создаётся
     * так же, как её создавала первая версия приложения, затем открывается текущей.
     */
    @Test
    fun migrationFromVersion1KeepsProfileAndAddsProteinPerKg() = runBlocking<Unit> {
        val name = "migration-v1.db"
        context.deleteDatabase(name)
        val schemaFile = File("schemas/com.nutritiontracker.data.db.AppDatabase/1.json")
        assertTrue("Нет схемы версии 1: ${schemaFile.absolutePath}", schemaFile.exists())
        val schema = kotlinx.serialization.json.Json.parseToJsonElement(schemaFile.readText()).jsonObject.getValue("database").jsonObject
        val file = context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val raw = android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(file, null)
        schema.getValue("entities").jsonArray.forEach { e ->
            val o = e.jsonObject
            val table = o.getValue("tableName").jsonPrimitive.content
            raw.execSQL(o.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table))
            o["indices"]?.jsonArray?.forEach { ix -> raw.execSQL(ix.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", table)) }
        }
        schema.getValue("setupQueries").jsonArray.forEach { raw.execSQL(it.jsonPrimitive.content) }
        raw.execSQL(
            "INSERT INTO profile (id, sex, birthEpochDay, heightCm, startWeightKg, targetWeightKg, activityLevel, deficitMode, " +
                "customDeficitKcal, manualGoalKcal, proteinPercent, fatPercent, carbsPercent, waterGoalMl, adaptiveEnabled, " +
                "appliedAdaptiveTdee, adaptiveUpdatedEpochDay, createdAt, updatedAt) VALUES " +
                "(1, 'male', ${LocalDate.of(1995, 3, 1).toEpochDay()}, 180.0, 82.0, 76.0, 'moderate', 'moderate', NULL, NULL, 25, 30, 45, 2000, 0, NULL, NULL, 1, 1)",
        )
        raw.execSQL("INSERT INTO water_entries (epochDay, amountMl, createdAt) VALUES (${day.toEpochDay()}, 300, 1)")
        raw.version = 1
        raw.close()

        val migrated = AppDatabase.create(context, name)
        val p = migrated.profileDao().get()!!
        assertEquals(1.6, p.proteinGPerKg, eps)
        assertEquals(82.0, p.startWeightKg, eps)
        assertEquals(30, p.fatPercent)
        assertEquals(300, migrated.waterDao().all().single().amountMl)
        migrated.close()
        context.deleteDatabase(name)
    }
}
