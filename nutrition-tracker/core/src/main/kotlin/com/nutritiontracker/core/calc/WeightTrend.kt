package com.nutritiontracker.core.calc

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

data class WeightPoint(val date: LocalDate, val weightKg: Double)

data class WeightSummary(
    val latest: WeightPoint?,
    /** Среднее по записям за последние 7 дней (включая сегодня). */
    val average7: Double?,
    /** Изменение сглаженного веса за 30 дней: среднее за 7 дней сейчас минус среднее 30 дней назад. */
    val change30Days: Double?,
    /** Сглаженный текущий вес минус начальный. */
    val changeFromStart: Double?,
    /** Доля пройденного пути к цели (0 — старт, 1 — цель достигнута). */
    val progressToTarget: Double?,
    val remainingToTargetKg: Double?,
)

/**
 * Тенденция веса. Ежедневные колебания (вода, соль, содержимое кишечника)
 * не считаются изменением жировой массы: все сравнения делаются по
 * скользящему среднему за 7 дней.
 */
object WeightTrend {
    const val WINDOW_DAYS = 7L

    /** Одна точка на дату: если записей несколько, берётся их среднее. */
    fun daily(points: List<WeightPoint>): List<WeightPoint> =
        points.filter { it.weightKg.isFinite() && it.weightKg > 0 }
            .groupBy { it.date }
            .map { (d, ps) -> WeightPoint(d, ps.map { it.weightKg }.average()) }
            .sortedBy { it.date }

    /** Среднее по записям в окне [date − 6 дней; date]. */
    fun trailingAverage(points: List<WeightPoint>, date: LocalDate, windowDays: Long = WINDOW_DAYS): Double? {
        val from = date.minusDays(windowDays - 1)
        val inWindow = daily(points).filter { !it.date.isBefore(from) && !it.date.isAfter(date) }
        return if (inWindow.isEmpty()) null else inWindow.map { it.weightKg }.average()
    }

    /** Сглаженная линия: скользящее среднее за 7 дней на дату каждой записи. */
    fun smoothed(points: List<WeightPoint>): List<WeightPoint> {
        val d = daily(points)
        return d.map { p -> WeightPoint(p.date, trailingAverage(d, p.date)!!) }
    }

    /** Наклон линейной регрессии веса по дням, кг/сутки. */
    fun slopeKgPerDay(points: List<WeightPoint>): Double? {
        val d = daily(points)
        if (d.size < 2) return null
        val origin = d.first().date
        val xs = d.map { ChronoUnit.DAYS.between(origin, it.date).toDouble() }
        val ys = d.map { it.weightKg }
        val mx = xs.average()
        val my = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - mx) * (ys[i] - my)
            den += (xs[i] - mx) * (xs[i] - mx)
        }
        return if (den == 0.0) null else num / den
    }

    fun summary(
        points: List<WeightPoint>,
        startWeightKg: Double?,
        targetWeightKg: Double?,
        today: LocalDate,
    ): WeightSummary {
        val d = daily(points).filter { !it.date.isAfter(today) }
        val latest = d.lastOrNull()
        val avg7 = trailingAverage(d, today)
        val current = avg7 ?: latest?.weightKg
        val past = trailingAverage(d, today.minusDays(30))
        val start = startWeightKg ?: d.firstOrNull()?.weightKg
        val progress = if (start != null && targetWeightKg != null && current != null && abs(start - targetWeightKg) > 1e-9) {
            (start - current) / (start - targetWeightKg)
        } else {
            null
        }
        return WeightSummary(
            latest = latest,
            average7 = avg7,
            change30Days = if (avg7 != null && past != null) avg7 - past else null,
            changeFromStart = if (current != null && start != null) current - start else null,
            progressToTarget = progress,
            remainingToTargetKg = if (current != null && targetWeightKg != null) current - targetWeightKg else null,
        )
    }
}
