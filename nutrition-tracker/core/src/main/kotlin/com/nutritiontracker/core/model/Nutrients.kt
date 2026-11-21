package com.nutritiontracker.core.model

/**
 * Набор значений нутриентов.
 *
 * Семантика отсутствия данных — главное правило проекта:
 * - нутриента нет в наборе → содержание неизвестно (в интерфейсе «нет данных»);
 * - значение 0.0 → содержание действительно равно нулю;
 * - нутриент в [incomplete] → известна только нижняя граница: значение собрано
 *   из нескольких продуктов, и не для всех из них есть данные (рецепт).
 *
 * Неизвестное значение никогда не превращается в ноль.
 */
class Nutrients private constructor(
    private val values: Map<Nutrient, Double>,
    val incomplete: Set<Nutrient>,
) {
    /** Значение или null, если содержание неизвестно. */
    operator fun get(nutrient: Nutrient): Double? = values[nutrient]

    fun isKnown(nutrient: Nutrient): Boolean = nutrient in values

    /** Значение известно полностью (а не как нижняя граница). */
    fun isComplete(nutrient: Nutrient): Boolean = nutrient in values && nutrient !in incomplete

    val known: Set<Nutrient> get() = values.keys

    val isEmpty: Boolean get() = values.isEmpty()

    fun toMap(): Map<Nutrient, Double> = values

    /** Умножает все известные значения на коэффициент. Неизвестные остаются неизвестными. */
    fun scaled(factor: Double): Nutrients {
        require(factor.isFinite() && factor >= 0.0) { "Коэффициент должен быть неотрицательным числом: $factor" }
        return Nutrients(values.mapValues { (_, v) -> v * factor }, incomplete)
    }

    /** Копия без указанного нутриента (он становится неизвестным). */
    fun without(nutrient: Nutrient): Nutrients =
        Nutrients(values - nutrient, incomplete - nutrient)

    /** Копия с заданным значением; null делает нутриент неизвестным. */
    fun with(nutrient: Nutrient, value: Double?, isComplete: Boolean = true): Nutrients {
        if (value == null) return without(nutrient)
        requireValid(nutrient, value)
        return Nutrients(
            values + (nutrient to value),
            if (isComplete) incomplete - nutrient else incomplete + nutrient,
        )
    }

    override fun equals(other: Any?): Boolean =
        other is Nutrients && other.values == values && other.incomplete == incomplete

    override fun hashCode(): Int = values.hashCode() * 31 + incomplete.hashCode()

    override fun toString(): String =
        values.entries.joinToString(prefix = "Nutrients(", postfix = ")") { (n, v) ->
            "${n.key}=$v${if (n in incomplete) "+" else ""}"
        }

    companion object {
        val EMPTY = Nutrients(emptyMap(), emptySet())

        fun of(values: Map<Nutrient, Double>, incomplete: Set<Nutrient> = emptySet()): Nutrients {
            values.forEach { (n, v) -> requireValid(n, v) }
            return Nutrients(values.toMap(), incomplete.intersect(values.keys))
        }

        fun of(vararg pairs: Pair<Nutrient, Double>): Nutrients = of(pairs.toMap())

        /** Читает набор из хранимого вида: ключи — [Nutrient.key]. Неизвестные ключи пропускаются. */
        fun fromStorage(values: Map<String, Double>, incomplete: Collection<String> = emptyList()): Nutrients {
            val parsed = buildMap {
                values.forEach { (key, v) -> Nutrient.fromKey(key)?.let { put(it, v) } }
            }
            val inc = incomplete.mapNotNull { Nutrient.fromKey(it) }.toSet()
            return of(parsed, inc)
        }

        private fun requireValid(nutrient: Nutrient, value: Double) {
            require(value.isFinite()) { "Значение ${nutrient.key} не является числом: $value" }
            require(value >= 0.0) { "Значение ${nutrient.key} отрицательное: $value" }
        }
    }

    fun storageValues(): Map<String, Double> = values.mapKeys { it.key.key }

    fun storageIncomplete(): List<String> = incomplete.map { it.key }.sorted()
}
