package com.nutritiontracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profile")
    suspend fun deleteAll()
}

@Dao
interface FoodDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(food: FoodEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(foods: List<FoodEntity>)

    @Update
    suspend fun update(food: FoodEntity)

    @Query("SELECT * FROM foods WHERE id = :id")
    suspend fun get(id: Long): FoodEntity?

    @Query("SELECT * FROM foods WHERE id = :id")
    fun observe(id: Long): Flow<FoodEntity?>

    @Query("SELECT * FROM foods WHERE source = :source AND externalId = :externalId LIMIT 1")
    suspend fun findExternal(source: String, externalId: String): FoodEntity?

    @Query("SELECT id, name, searchIndex, source, useCount, lastUsedAt FROM foods")
    suspend fun searchCandidates(): List<FoodSearchRow>

    @Query("SELECT * FROM foods WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<FoodEntity>

    @Query("SELECT * FROM foods WHERE source = 'user' ORDER BY name COLLATE NOCASE")
    fun observeUserFoods(): Flow<List<FoodEntity>>

    @Query("SELECT * FROM foods WHERE lastUsedAt IS NOT NULL ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodEntity>>

    @Query("SELECT f.* FROM foods f INNER JOIN favorites fav ON fav.foodId = f.id ORDER BY f.name COLLATE NOCASE")
    fun observeFavorites(): Flow<List<FoodEntity>>

    @Query("UPDATE foods SET useCount = useCount + 1, lastUsedAt = :now WHERE id = :id")
    suspend fun markUsed(id: Long, now: Long)

    @Query(
        "SELECT * FROM foods WHERE source NOT IN ('user', 'recipe') " +
            "AND (detailsLoaded = 0 OR fetchedAt IS NULL OR fetchedAt < :before) " +
            "ORDER BY detailsLoaded ASC, lastUsedAt DESC LIMIT :limit",
    )
    suspend fun needingRefresh(before: Long, limit: Int): List<FoodEntity>

    @Query("SELECT COUNT(*) FROM foods WHERE source NOT IN ('user', 'recipe') AND (detailsLoaded = 0 OR fetchedAt IS NULL OR fetchedAt < :before)")
    suspend fun countNeedingRefresh(before: Long): Int

    @Query("SELECT COUNT(*) FROM foods")
    suspend fun count(): Int

    @Query("SELECT * FROM foods ORDER BY id")
    suspend fun all(): List<FoodEntity>

    @Query("DELETE FROM foods WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM foods")
    suspend fun deleteAll()
}

@Dao
interface FavoriteDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<FavoriteEntity>)

    @Query("DELETE FROM favorites WHERE foodId = :foodId")
    suspend fun deleteByFood(foodId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE foodId = :foodId)")
    fun observeIsFavorite(foodId: Long): Flow<Boolean>

    @Query("SELECT foodId FROM favorites")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT * FROM favorites ORDER BY id")
    suspend fun all(): List<FavoriteEntity>

    @Query("DELETE FROM favorites")
    suspend fun deleteAll()
}

@Dao
interface EntryDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: FoodEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<FoodEntryEntity>)

    @Update
    suspend fun update(entry: FoodEntryEntity)

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun get(id: Long): FoodEntryEntity?

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY createdAt, id")
    fun observeDay(day: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE epochDay = :day ORDER BY createdAt, id")
    suspend fun day(day: Long): List<FoodEntryEntity>

    @Query("SELECT * FROM food_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, createdAt, id")
    fun observeRange(from: Long, to: Long): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE epochDay BETWEEN :from AND :to ORDER BY epochDay, createdAt, id")
    suspend fun range(from: Long, to: Long): List<FoodEntryEntity>

    @Query("SELECT MIN(epochDay) FROM food_entries")
    suspend fun firstDay(): Long?

    @Query("SELECT COUNT(*) FROM food_entries")
    suspend fun count(): Int

    @Query("SELECT * FROM food_entries ORDER BY id")
    suspend fun all(): List<FoodEntryEntity>

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()
}

@Dao
interface RecipeDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(recipe: RecipeEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(recipes: List<RecipeEntity>)

    @Update
    suspend fun update(recipe: RecipeEntity)

    @Query("SELECT * FROM recipes WHERE id = :id")
    suspend fun get(id: Long): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id")
    fun observe(id: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RecipeEntity>>

    @Query("DELETE FROM recipes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position, id")
    suspend fun ingredients(recipeId: Long): List<RecipeIngredientEntity>

    @Query("SELECT * FROM recipe_ingredients WHERE recipeId = :recipeId ORDER BY position, id")
    fun observeIngredients(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertIngredients(items: List<RecipeIngredientEntity>)

    @Query("DELETE FROM recipe_ingredients WHERE recipeId = :recipeId")
    suspend fun deleteIngredients(recipeId: Long)

    @Query("SELECT * FROM recipes ORDER BY id")
    suspend fun all(): List<RecipeEntity>

    @Query("SELECT * FROM recipe_ingredients ORDER BY id")
    suspend fun allIngredients(): List<RecipeIngredientEntity>

    @Query("DELETE FROM recipe_ingredients")
    suspend fun deleteAllIngredients()

    @Query("DELETE FROM recipes")
    suspend fun deleteAll()
}

@Dao
interface WeightDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WeightEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<WeightEntryEntity>)

    @Update
    suspend fun update(entry: WeightEntryEntity)

    @Query("SELECT * FROM weight_entries WHERE epochDay = :day LIMIT 1")
    suspend fun forDay(day: Long): WeightEntryEntity?

    @Query("SELECT * FROM weight_entries ORDER BY epochDay")
    fun observeAll(): Flow<List<WeightEntryEntity>>

    @Query("SELECT * FROM weight_entries ORDER BY epochDay")
    suspend fun all(): List<WeightEntryEntity>

    @Query("SELECT * FROM weight_entries WHERE weightKg IS NOT NULL ORDER BY epochDay DESC LIMIT 1")
    fun observeLatestWeight(): Flow<WeightEntryEntity?>

    @Query("DELETE FROM weight_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM weight_entries")
    suspend fun deleteAll()
}

@Dao
interface WaterDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: WaterEntryEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(entries: List<WaterEntryEntity>)

    @Query("DELETE FROM water_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM water_entries WHERE epochDay = :day ORDER BY createdAt, id")
    fun observeDay(day: Long): Flow<List<WaterEntryEntity>>

    @Query("SELECT epochDay AS epochDay, SUM(amountMl) AS total FROM water_entries WHERE epochDay BETWEEN :from AND :to GROUP BY epochDay ORDER BY epochDay")
    fun observeTotals(from: Long, to: Long): Flow<List<DayWater>>

    @Query("SELECT * FROM water_entries ORDER BY id")
    suspend fun all(): List<WaterEntryEntity>

    @Query("DELETE FROM water_entries")
    suspend fun deleteAll()
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM nutrient_goals ORDER BY nutrientKey")
    fun observeAll(): Flow<List<NutrientGoalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: NutrientGoalEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(goals: List<NutrientGoalEntity>)

    @Query("DELETE FROM nutrient_goals WHERE nutrientKey = :key")
    suspend fun delete(key: String)

    @Query("SELECT * FROM nutrient_goals ORDER BY id")
    suspend fun all(): List<NutrientGoalEntity>

    @Query("DELETE FROM nutrient_goals")
    suspend fun deleteAll()
}

@Dao
interface SearchHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: SearchHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(items: List<SearchHistoryEntity>)

    @Query("SELECT * FROM search_history ORDER BY lastUsedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<SearchHistoryEntity>>

    @Query("DELETE FROM search_history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM search_history ORDER BY id")
    suspend fun all(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history")
    suspend fun deleteAll()
}

@Dao
interface TemplateDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(template: MealTemplateEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(templates: List<MealTemplateEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertItems(items: List<MealTemplateItemEntity>)

    @Query("SELECT * FROM meal_templates ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<MealTemplateEntity>>

    @Query("SELECT * FROM meal_template_items ORDER BY templateId, position, id")
    fun observeAllItems(): Flow<List<MealTemplateItemEntity>>

    @Query("SELECT * FROM meal_template_items WHERE templateId = :templateId ORDER BY position, id")
    suspend fun items(templateId: Long): List<MealTemplateItemEntity>

    @Query("DELETE FROM meal_templates WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM meal_templates ORDER BY id")
    suspend fun all(): List<MealTemplateEntity>

    @Query("SELECT * FROM meal_template_items ORDER BY id")
    suspend fun allItems(): List<MealTemplateItemEntity>

    @Query("DELETE FROM meal_template_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM meal_templates")
    suspend fun deleteAll()
}
