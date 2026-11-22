package com.nutritiontracker.data

import androidx.room.withTransaction
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.text.FoodQueryTranslator
import com.nutritiontracker.core.text.FoodSearch
import com.nutritiontracker.core.text.TextNormalizer
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.FavoriteEntity
import com.nutritiontracker.data.db.SearchHistoryEntity
import com.nutritiontracker.remote.RemoteError
import com.nutritiontracker.remote.RemoteErrorKind
import com.nutritiontracker.remote.RemoteFood
import com.nutritiontracker.remote.RemoteResult
import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class LocalSearchHit(val food: FoodItem, val score: Double)

data class OnlineSearchResult(
    val usda: List<RemoteFood>,
    val openFoodFacts: List<RemoteFood>,
    val errors: List<RemoteError>,
    /** Английский запрос, с которым искали в USDA (для русского запроса — перевод). */
    val usdaQuery: String?,
    /** USDA не опрашивался: русский запрос не удалось перевести. */
    val usdaSkippedNoTranslation: Boolean,
)

class FoodRepository(
    private val db: AppDatabase,
    private val usda: UsdaClient,
    private val off: OpenFoodFactsClient,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val foodDao = db.foodDao()
    private val favoriteDao = db.favoriteDao()
    private val historyDao = db.searchHistoryDao()

    fun observeFood(id: Long): Flow<FoodItem?> = foodDao.observe(id).map { it?.toItem() }
    suspend fun get(id: Long): FoodItem? = foodDao.get(id)?.toItem()

    fun observeFavorites(): Flow<List<FoodItem>> = foodDao.observeFavorites().map { l -> l.map { it.toItem() } }
    fun observeFavoriteIds(): Flow<Set<Long>> = favoriteDao.observeIds().map { it.toSet() }
    fun observeIsFavorite(id: Long): Flow<Boolean> = favoriteDao.observeIsFavorite(id)
    fun observeRecent(limit: Int = 30): Flow<List<FoodItem>> = foodDao.observeRecent(limit).map { l -> l.map { it.toItem() } }
    fun observeUserFoods(): Flow<List<FoodItem>> = foodDao.observeUserFoods().map { l -> l.map { it.toItem() } }
    fun observeSearchHistory(limit: Int = 12): Flow<List<String>> = historyDao.observeRecent(limit).map { l -> l.map { it.query } }

    suspend fun setFavorite(foodId: Long, favorite: Boolean) {
        if (favorite) favoriteDao.insert(FavoriteEntity(foodId = foodId, createdAt = clock())) else favoriteDao.deleteByFood(foodId)
    }

    suspend fun rememberQuery(query: String) {
        val q = query.trim()
        if (q.length < 2) return
        historyDao.upsert(SearchHistoryEntity(query = q, lastUsedAt = clock()))
    }

    suspend fun clearSearchHistory() = historyDao.deleteAll()

    /** Поиск по локальной базе: работает без интернета. */
    suspend fun searchLocal(query: String, limit: Int = 50): List<LocalSearchHit> {
        val stems = FoodSearch.queryStems(query)
        if (stems.isEmpty()) return emptyList()
        val scored = foodDao.searchCandidates()
            .mapNotNull { row ->
                val penalty = if (row.source.startsWith("usda")) FoodSearch.USDA_EXTRA_WORD_PENALTY else FoodSearch.EXTRA_WORD_PENALTY
                val s = FoodSearch.score(row.name, row.searchIndex, stems, penalty)
                if (s <= 0) null else row to (s + usageBonus(row.useCount, row.source))
            }
            .sortedByDescending { it.second }
            .take(limit)
        val byId = foodDao.getByIds(scored.map { it.first.id }).associateBy { it.id }
        return scored.mapNotNull { (row, score) -> byId[row.id]?.let { LocalSearchHit(it.toItem(), score) } }
    }

    private fun usageBonus(useCount: Int, source: String): Double {
        val sourceBonus = when (source) {
            FoodSource.USER.key, FoodSource.RECIPE.key -> 1.0
            else -> 0.0
        }
        return minOf(useCount, 20) * 0.25 + sourceBonus
    }

    /**
     * Поиск в USDA и Open Food Facts параллельно. Ошибка одного источника не
     * мешает показать результаты другого.
     */
    suspend fun searchOnline(query: String, settings: AppSettings): OnlineSearchResult = coroutineScope {
        val translation = FoodQueryTranslator.translate(query)
        val usdaQuery = if (settings.usdaEnabled) translation.englishQuery else null
        val usdaJob = usdaQuery?.let { q -> async { usda.search(q) } }
        val offJob = if (settings.openFoodFactsEnabled) async { off.search(query.trim()) } else null
        val errors = mutableListOf<RemoteError>()
        val usdaFoods = when (val r = usdaJob?.await()) {
            is RemoteResult.Success -> r.value
            is RemoteResult.Failure -> { errors += r.error; emptyList() }
            null -> emptyList()
        }
        val offFoods = when (val r = offJob?.await()) {
            is RemoteResult.Success -> r.value
            is RemoteResult.Failure -> { errors += r.error; emptyList() }
            null -> emptyList()
        }
        // Сначала то, что действительно совпадает с запросом: поиск Open Food Facts
        // не знает русской морфологии и возвращает много лишнего.
        val offRanked = offFoods.sortedByDescending { FoodSearch.relevance(query, it.food.name, it.food.brand, it.food.category, it.food.nameEn) }
        val usdaRanked = if (usdaQuery == null) usdaFoods else usdaFoods.sortedByDescending { FoodSearch.relevanceUsda(usdaQuery, it.food.name, it.food.category) }
        OnlineSearchResult(
            usda = usdaRanked,
            openFoodFacts = offRanked,
            errors = errors,
            usdaQuery = usdaQuery,
            usdaSkippedNoTranslation = settings.usdaEnabled && usdaQuery == null,
        )
    }

    /** Ищет сохранённую копию продукта из источника. */
    suspend fun findCached(source: FoodSource, externalId: String): FoodItem? =
        foodDao.findExternal(source.key, externalId)?.toItem()

    /**
     * Сохраняет продукт из источника в локальный кэш и возвращает его id.
     * Если продукт найден по русскому запросу, а называется по-английски,
     * запрос запоминается как русское название — тогда он найдётся и без интернета.
     */
    suspend fun saveRemote(remote: RemoteFood, searchQuery: String?): Long = db.withTransaction {
        val now = clock()
        val data = remote.food
        val alias = searchQuery?.trim()?.takeIf { q ->
            q.length >= 2 && TextNormalizer.isCyrillic(TextNormalizer.normalize(q)) && !TextNormalizer.isCyrillic(TextNormalizer.normalize(data.name))
        }?.replaceFirstChar { it.uppercase() }
        val existing = data.externalId?.let { foodDao.findExternal(data.source.key, it) }
        if (existing == null) {
            foodDao.insert(newFoodEntity(data, listOfNotNull(alias), remote.isFullRecord, now, now))
        } else {
            val aliases = StorageJson.readStrings(existing.aliases).let { if (alias != null && alias !in it) it + alias else it }
            val updated = if (!remote.isFullRecord && existing.detailsLoaded) {
                existing.copy(aliases = StorageJson.strings(aliases), searchIndex = buildSearchIndex(existing.toItem().data, aliases))
            } else {
                existing.withData(data, remote.isFullRecord || existing.detailsLoaded, now, now, aliases)
            }
            foodDao.update(updated)
            existing.id
        }
    }

    /** Догружает полную карточку USDA (порции, полный состав), если её ещё нет. */
    suspend fun ensureDetails(foodId: Long): RemoteError? {
        val entity = foodDao.get(foodId) ?: return null
        if (entity.detailsLoaded) return null
        return refreshFromSource(foodId)
    }

    /** Обновляет продукт из источника. Записи дневника не меняются: в них снимок. */
    suspend fun refreshFromSource(foodId: Long): RemoteError? {
        val entity = foodDao.get(foodId) ?: return null
        val source = FoodSource.fromKey(entity.source) ?: return null
        val externalId = entity.externalId ?: return null
        val result = when {
            source.isUsda -> usda.details(externalId)
            source == FoodSource.OPEN_FOOD_FACTS -> off.product(externalId)
            else -> return null
        }
        return when (result) {
            is RemoteResult.Success -> {
                val fresh = foodDao.get(foodId) ?: return null
                foodDao.update(fresh.withData(result.value.food, true, clock(), clock()))
                null
            }
            is RemoteResult.Failure -> result.error
        }
    }

    /**
     * Фоновое обновление кэша: догружает недостающие карточки и обновляет
     * данные старше [maxAgeMillis]. Возвращает число обновлённых продуктов.
     */
    suspend fun refreshStale(maxAgeMillis: Long, limit: Int = 20): Int {
        val candidates = foodDao.needingRefresh(clock() - maxAgeMillis, limit)
        var updated = 0
        val usdaIds = candidates.filter { FoodSource.fromKey(it.source)?.isUsda == true && it.externalId != null }
        if (usdaIds.isNotEmpty()) {
            when (val r = usda.detailsBatch(usdaIds.mapNotNull { it.externalId })) {
                is RemoteResult.Success -> {
                    val byExternal = r.value.associateBy { it.food.externalId }
                    for (e in usdaIds) {
                        val fresh = byExternal[e.externalId] ?: continue
                        val current = foodDao.get(e.id) ?: continue
                        foodDao.update(current.withData(fresh.food, true, clock(), clock()))
                        updated++
                    }
                }
                is RemoteResult.Failure -> if (r.error.kind == RemoteErrorKind.NO_NETWORK) return updated
            }
        }
        for (e in candidates.filter { it.source == FoodSource.OPEN_FOOD_FACTS.key }.take(10)) {
            if (refreshFromSource(e.id) == null) updated++
        }
        return updated
    }

    suspend fun countNeedingRefresh(maxAgeMillis: Long): Int = foodDao.countNeedingRefresh(clock() - maxAgeMillis)

    suspend fun createUserFood(data: FoodData, aliases: List<String>): Long =
        foodDao.insert(newFoodEntity(data.copy(source = FoodSource.USER, externalId = null), aliases, true, clock(), null))

    suspend fun updateUserFood(id: Long, data: FoodData, aliases: List<String>) {
        val existing = foodDao.get(id) ?: return
        foodDao.update(existing.withData(data.copy(source = FoodSource.fromKey(existing.source) ?: FoodSource.USER, externalId = existing.externalId), true, clock(), existing.fetchedAt, aliases))
    }

    /** Копия продукта из источника как собственного — например, чтобы поправить значения. */
    suspend fun duplicateAsUserFood(id: Long): Long? {
        val item = get(id) ?: return null
        return createUserFood(item.data.copy(name = item.aliases.firstOrNull() ?: item.data.name, dataNotes = item.data.dataNotes + "Скопировано из: ${item.data.source.titleRu}"), item.aliases)
    }

    suspend fun setAliases(id: Long, aliases: List<String>) {
        val existing = foodDao.get(id) ?: return
        val clean = aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        foodDao.update(existing.copy(aliases = StorageJson.strings(clean), searchIndex = buildSearchIndex(existing.toItem().data, clean), updatedAt = clock()))
    }

    /** Удаляет продукт. Записи дневника сохраняются: в них снимок пищевой ценности. */
    suspend fun delete(id: Long) = foodDao.delete(id)

    suspend fun markUsed(id: Long) = foodDao.markUsed(id, clock())
}
