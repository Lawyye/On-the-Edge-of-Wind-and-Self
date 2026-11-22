package com.nutritiontracker.data.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nutritiontracker.NutritionApp
import java.util.concurrent.TimeUnit

/**
 * Синхронизация кэша продуктов: догружает недостающие карточки USDA и
 * обновляет данные, которые старше месяца. Запускается, только когда есть
 * сеть, — если интернета нет, WorkManager подождёт его появления.
 * Дневник и личные данные никуда не отправляются.
 */
class CacheRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as NutritionApp).container
        if (!container.settings.current().autoRefreshEnabled) return Result.success()
        return try {
            container.foods.refreshStale(MAX_AGE_MILLIS)
            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Отмена (например, пропала сеть) — не ошибка: WorkManager перезапустит работу сам.
            throw e
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    companion object {
        private const val PERIODIC = "food-cache-refresh"
        private const val ONE_TIME = "food-cache-refresh-now"
        val MAX_AGE_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

        private val networkConstraint = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<CacheRefreshWorker>(7, TimeUnit.DAYS)
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }

        /** Разовая синхронизация, как только появится сеть. */
        fun syncWhenOnline(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME,
                ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<CacheRefreshWorker>()
                    .setConstraints(networkConstraint)
                    .build(),
            )
        }
    }
}
