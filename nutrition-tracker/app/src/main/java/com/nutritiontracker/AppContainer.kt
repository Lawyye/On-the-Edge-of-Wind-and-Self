package com.nutritiontracker

import android.content.Context
import com.nutritiontracker.data.BackupRepository
import com.nutritiontracker.data.BodyRepository
import com.nutritiontracker.data.ConnectivityMonitor
import com.nutritiontracker.data.DiaryRepository
import com.nutritiontracker.data.FoodRepository
import com.nutritiontracker.data.ProfileRepository
import com.nutritiontracker.data.RecipeRepository
import com.nutritiontracker.data.SettingsRepository
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.remote.RemoteJson
import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Ручное внедрение зависимостей: один экземпляр каждого репозитория на приложение. */
class AppContainer(context: Context, database: AppDatabase? = null) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AppDatabase = database ?: AppDatabase.create(app)
    val settings = SettingsRepository(app)
    val connectivity = ConnectivityMonitor(app)

    @Volatile
    private var usdaKey: String = ""

    init {
        appScope.launch { settings.settings.collect { usdaKey = it.usdaApiKey } }
    }

    // Без дискового HTTP-кэша: в нём оставались бы адреса запросов с ключом USDA и поисковыми словами.
    private val okHttp = RemoteJson.defaultOkHttp()

    init {
        // Удаляем кэш, оставшийся от предыдущих версий.
        appScope.launch(Dispatchers.IO) { File(app.cacheDir, "http").deleteRecursively() }
    }

    private val usda = UsdaClient(okHttp, { usdaKey.ifBlank { BuildConfig.USDA_DEV_KEY } })
    private val off = OpenFoodFactsClient(okHttp, "NutritionTracker/${BuildConfig.VERSION_NAME} (Android; personal use)")

    val foods = FoodRepository(this.database, usda, off)
    val diary = DiaryRepository(this.database)
    val profile = ProfileRepository(this.database)
    val body = BodyRepository(this.database)
    val recipes = RecipeRepository(this.database)
    val backup = BackupRepository(app, this.database, settings, BuildConfig.VERSION_NAME)
}
