package com.nutritiontracker.data

import com.nutritiontracker.core.calc.WeightPoint
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.WaterEntryEntity
import com.nutritiontracker.data.db.WeightEntryEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

data class BodyEntry(
    val id: Long,
    val date: LocalDate,
    val weightKg: Double?,
    val waistCm: Double?,
    val hipsCm: Double?,
    val chestCm: Double?,
    val neckCm: Double?,
    val armCm: Double?,
    val thighCm: Double?,
    val note: String?,
)

fun WeightEntryEntity.toBody() = BodyEntry(id, LocalDate.ofEpochDay(epochDay), weightKg, waistCm, hipsCm, chestCm, neckCm, armCm, thighCm, note)

data class WaterEntry(val id: Long, val date: LocalDate, val amountMl: Int, val createdAt: Long)

class BodyRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val weightDao = db.weightDao()
    private val waterDao = db.waterDao()

    fun observeBody(): Flow<List<BodyEntry>> = weightDao.observeAll().map { l -> l.map { it.toBody() } }

    fun observeWeights(): Flow<List<WeightPoint>> = weightDao.observeAll().map { l ->
        l.mapNotNull { w -> w.weightKg?.let { WeightPoint(LocalDate.ofEpochDay(w.epochDay), it) } }
    }

    fun observeLatestWeight(): Flow<BodyEntry?> = weightDao.observeLatestWeight().map { it?.toBody() }

    /** Одна запись на дату: повторное сохранение за тот же день обновляет её. */
    suspend fun saveBody(e: BodyEntry) {
        e.weightKg?.let { require(it in 20.0..400.0) { "Вес должен быть от 20 до 400 кг" } }
        listOf(e.waistCm, e.hipsCm, e.chestCm, e.neckCm, e.armCm, e.thighCm).filterNotNull().forEach {
            require(it in 5.0..300.0) { "Обхват должен быть от 5 до 300 см" }
        }
        require(listOf(e.weightKg, e.waistCm, e.hipsCm, e.chestCm, e.neckCm, e.armCm, e.thighCm).any { it != null }) {
            "Укажите вес или хотя бы одно измерение"
        }
        val now = clock()
        val existing = weightDao.forDay(e.date.toEpochDay())
        val target = if (e.id != 0L) e.id else existing?.id ?: 0L
        // Новая запись на дату, где запись уже есть, дополняет её, а не стирает другие измерения.
        val base = if (e.id == 0L) existing else null
        val entity = WeightEntryEntity(
            id = target,
            epochDay = e.date.toEpochDay(),
            weightKg = e.weightKg ?: base?.weightKg,
            waistCm = e.waistCm ?: base?.waistCm,
            hipsCm = e.hipsCm ?: base?.hipsCm,
            chestCm = e.chestCm ?: base?.chestCm,
            neckCm = e.neckCm ?: base?.neckCm,
            armCm = e.armCm ?: base?.armCm,
            thighCm = e.thighCm ?: base?.thighCm,
            note = e.note?.trim()?.takeIf { it.isNotEmpty() } ?: base?.note,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now,
        )
        if (target == 0L) {
            weightDao.insert(entity)
        } else {
            // При переносе записи на дату, где уже есть другая, та заменяется.
            if (existing != null && existing.id != target) weightDao.delete(existing.id)
            weightDao.update(entity)
        }
    }

    suspend fun deleteBody(id: Long) = weightDao.delete(id)

    fun observeWater(date: LocalDate): Flow<List<WaterEntry>> = waterDao.observeDay(date.toEpochDay()).map { l ->
        l.map { WaterEntry(it.id, LocalDate.ofEpochDay(it.epochDay), it.amountMl, it.createdAt) }
    }

    fun observeWaterTotals(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, Int>> =
        waterDao.observeTotals(from.toEpochDay(), to.toEpochDay()).map { l -> l.associate { LocalDate.ofEpochDay(it.epochDay) to it.total } }

    suspend fun addWater(date: LocalDate, ml: Int) {
        require(ml in 1..5000) { "Объём должен быть от 1 до 5000 мл" }
        waterDao.insert(WaterEntryEntity(epochDay = date.toEpochDay(), amountMl = ml, createdAt = clock()))
    }

    suspend fun deleteWater(id: Long) = waterDao.delete(id)
}
