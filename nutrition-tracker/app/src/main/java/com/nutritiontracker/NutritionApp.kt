package com.nutritiontracker

import android.app.Application
import com.nutritiontracker.data.work.CacheRefreshWorker
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val ALIASES_FIX = "usda_aliases_v1"

class NutritionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        CacheRefreshWorker.schedule(this)
        container.appScope.launch {
            // Недостающие карточки продуктов догрузятся, как только появится сеть.
            if (container.foods.countNeedingRefresh(CacheRefreshWorker.MAX_AGE_MILLIS) > 0) {
                CacheRefreshWorker.syncWhenOnline(this@NutritionApp)
            }
            container.profile.maybeUpdateAdaptive(LocalDate.now())
            // Прежние версии давали продуктам USDA русское название по запросу как есть.
            if (!container.settings.isDone(ALIASES_FIX)) {
                container.foods.cleanUsdaAliases()
                container.settings.markDone(ALIASES_FIX)
            }
        }
    }
}
