package com.nutritiontracker.data

import androidx.room.withTransaction
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.Nutrients
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.FoodEntryEntity
import com.nutritiontracker.data.db.MealTemplateEntity
import com.nutritiontracker.data.db.MealTemplateItemEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/** Что добавить в дневник: снимок продукта и количество. */
data class NewEntry(
    val foodId: Long?,
    val foodName: String,
    val brand: String?,
    val source: FoodSource,
    val per100: Nutrients,
    val amount: Double,
    val unit: AmountUnit,
    val servingLabel: String? = null,
)

fun FoodItem.toNewEntry(amount: Double, servingLabel: String? = null) = NewEntry(
    foodId = id,
    foodName = displayName,
    brand = data.brand,
    source = data.source,
    per100 = data.nutrients,
    amount = amount,
    unit = data.basis,
    servingLabel = servingLabel,
)

/** Название для показа: для продуктов USDA — русское, если пользователь его задал. */
val FoodItem.displayName: String
    get() = if (data.source.isUsda && aliases.isNotEmpty()) aliases.first() else data.name

data class MealTemplate(val id: Long, val name: String, val meal: MealType?, val items: List<TemplateItem>)

class DiaryRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.entryDao()
    private val foodDao = db.foodDao()
    private val templateDao = db.templateDao()

    fun observeDay(date: LocalDate): Flow<List<DiaryEntry>> = dao.observeDay(date.toEpochDay()).map { l -> l.map { it.toEntry() } }

    fun observeRange(from: LocalDate, to: LocalDate): Flow<List<DiaryEntry>> =
        dao.observeRange(from.toEpochDay(), to.toEpochDay()).map { l -> l.map { it.toEntry() } }

    suspend fun range(from: LocalDate, to: LocalDate): List<DiaryEntry> =
        dao.range(from.toEpochDay(), to.toEpochDay()).map { it.toEntry() }

    suspend fun get(id: Long): DiaryEntry? = dao.get(id)?.toEntry()

    suspend fun firstDay(): LocalDate? = dao.firstDay()?.let { LocalDate.ofEpochDay(it) }

    private fun entity(e: NewEntry, date: LocalDate, meal: MealType, now: Long): FoodEntryEntity {
        require(e.amount.isFinite() && e.amount > 0) { "Количество должно быть больше нуля" }
        return FoodEntryEntity(
            foodId = e.foodId,
            epochDay = date.toEpochDay(),
            mealType = meal.key,
            amount = e.amount,
            unit = e.unit.name,
            foodName = e.foodName,
            brand = e.brand,
            source = e.source.key,
            nutrientsPer100 = StorageJson.nutrients(e.per100),
            incompleteNutrients = StorageJson.incomplete(e.per100),
            servingLabel = e.servingLabel,
            createdAt = now,
            updatedAt = now,
        )
    }

    suspend fun add(entry: NewEntry, date: LocalDate, meal: MealType): Long = db.withTransaction {
        val now = clock()
        val id = dao.insert(entity(entry, date, meal, now))
        entry.foodId?.let { foodDao.markUsed(it, now) }
        id
    }

    /** Добавляет несколько записей одной транзакцией: либо все, либо ни одной. */
    suspend fun addAll(entries: List<NewEntry>, date: LocalDate, meal: MealType) = db.withTransaction {
        val now = clock()
        dao.insertAll(entries.map { entity(it, date, meal, now) })
        entries.mapNotNull { it.foodId }.distinct().forEach { foodDao.markUsed(it, now) }
    }

    suspend fun update(id: Long, amount: Double, meal: MealType, date: LocalDate) {
        require(amount.isFinite() && amount > 0) { "Количество должно быть больше нуля" }
        val e = dao.get(id) ?: return
        dao.update(e.copy(amount = amount, mealType = meal.key, epochDay = date.toEpochDay(), updatedAt = clock(), servingLabel = if (amount == e.amount) e.servingLabel else null))
    }

    suspend fun delete(id: Long): FoodEntryEntity? {
        val e = dao.get(id) ?: return null
        dao.delete(id)
        return e
    }

    /** Возвращает удалённую запись (отмена удаления). */
    suspend fun restore(entity: FoodEntryEntity) {
        val foodStillExists = entity.foodId?.let { foodDao.get(it) != null } ?: false
        dao.insert(entity.copy(id = 0, foodId = if (foodStillExists) entity.foodId else null))
    }

    /** Повторяет запись на другую дату/приём пищи с тем же снимком. */
    suspend fun duplicate(id: Long, date: LocalDate, meal: MealType) {
        val e = dao.get(id) ?: return
        val now = clock()
        dao.insert(e.copy(id = 0, epochDay = date.toEpochDay(), mealType = meal.key, createdAt = now, updatedAt = now))
    }

    suspend fun copyMeal(fromDate: LocalDate, fromMeal: MealType, toDate: LocalDate, toMeal: MealType): Int = db.withTransaction {
        val now = clock()
        val src = dao.day(fromDate.toEpochDay()).filter { it.mealType == fromMeal.key }
        dao.insertAll(src.map { it.copy(id = 0, epochDay = toDate.toEpochDay(), mealType = toMeal.key, createdAt = now, updatedAt = now) })
        src.size
    }

    fun observeTemplates(): Flow<List<MealTemplate>> =
        templateDao.observeAll().combine(templateDao.observeAllItems()) { ts, items ->
            val byTemplate = items.groupBy { it.templateId }
            ts.map { t ->
                MealTemplate(t.id, t.name, t.mealType?.let { MealType.fromKey(it) }, (byTemplate[t.id] ?: emptyList()).map { it.toTemplateItem() })
            }
        }

    suspend fun saveMealAsTemplate(name: String, date: LocalDate, meal: MealType): Long? = db.withTransaction {
        val src = dao.day(date.toEpochDay()).filter { it.mealType == meal.key }
        if (src.isEmpty()) return@withTransaction null
        val id = templateDao.insert(MealTemplateEntity(name = name.trim(), mealType = meal.key, createdAt = clock()))
        templateDao.insertItems(
            src.mapIndexed { i, e ->
                MealTemplateItemEntity(
                    templateId = id, foodId = e.foodId, name = e.foodName, brand = e.brand, source = e.source,
                    amount = e.amount, unit = e.unit, nutrientsPer100 = e.nutrientsPer100,
                    incompleteNutrients = e.incompleteNutrients, position = i,
                )
            },
        )
        id
    }

    suspend fun applyTemplate(templateId: Long, date: LocalDate, meal: MealType): Int = db.withTransaction {
        val items = templateDao.items(templateId)
        val now = clock()
        dao.insertAll(
            items.map {
                FoodEntryEntity(
                    foodId = it.foodId?.takeIf { id -> foodDao.get(id) != null }, epochDay = date.toEpochDay(), mealType = meal.key,
                    amount = it.amount, unit = it.unit, foodName = it.name, brand = it.brand, source = it.source,
                    nutrientsPer100 = it.nutrientsPer100, incompleteNutrients = it.incompleteNutrients,
                    servingLabel = null, createdAt = now, updatedAt = now,
                )
            },
        )
        items.size
    }

    suspend fun deleteTemplate(id: Long) = templateDao.delete(id)
}
