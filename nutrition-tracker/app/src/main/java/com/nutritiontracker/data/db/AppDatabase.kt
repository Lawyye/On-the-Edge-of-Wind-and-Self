package com.nutritiontracker.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Локальная база. При изменении схемы версия увеличивается и добавляется
 * миграция в [MIGRATIONS]; разрушающая миграция не используется никогда —
 * данные пользователя при обновлении приложения не удаляются.
 * Схемы каждой версии сохраняются в app/schemas.
 */
@Database(
    entities = [
        ProfileEntity::class,
        FoodEntity::class,
        FoodEntryEntity::class,
        RecipeEntity::class,
        RecipeIngredientEntity::class,
        WeightEntryEntity::class,
        WaterEntryEntity::class,
        NutrientGoalEntity::class,
        FavoriteEntity::class,
        SearchHistoryEntity::class,
        MealTemplateEntity::class,
        MealTemplateItemEntity::class,
    ],
    version = AppDatabase.VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun foodDao(): FoodDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun entryDao(): EntryDao
    abstract fun recipeDao(): RecipeDao
    abstract fun weightDao(): WeightDao
    abstract fun waterDao(): WaterDao
    abstract fun goalDao(): GoalDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun templateDao(): TemplateDao

    companion object {
        const val VERSION = 2
        const val NAME = "nutrition.db"

        /** 1 → 2: белок задаётся в г/кг (по умолчанию 1,6). Данные профиля сохраняются. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE profile ADD COLUMN proteinGPerKg REAL NOT NULL DEFAULT 1.6")
            }
        }

        /** Миграции между версиями схемы. Разрушающая миграция не используется никогда. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

        fun create(context: Context, name: String? = NAME): AppDatabase {
            val builder = if (name == null) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, name)
            }
            return builder
                .addMigrations(*MIGRATIONS)
                .build()
        }
    }
}
