package com.nutritiontracker.core.text

/**
 * Поисковый индекс продукта: нормализованные слова и их основы из названия,
 * альтернативных названий, английского названия, производителя и категории.
 *
 * Сопоставление терпимо к типичным опечаткам: удвоенные буквы схлопываются
 * («куринного» = «куриного»), а для основ длиной от 6 букв допускается одна
 * ошибка (замена, пропуск, лишняя буква или перестановка соседних); короче — нет,
 * чтобы «сливки» не находили «сливы».
 */
object FoodSearch {
    private const val FUZZY_MIN_LENGTH = 6

    private fun keyOf(token: String): String = TextNormalizer.stem(TextNormalizer.squeeze(token))

    /** Строка для хранения в базе: слова и основы через пробел, обрамлённые пробелами. */
    fun buildIndex(vararg parts: String?): String {
        val stems = parts.filterNotNull().flatMap { TextNormalizer.tokens(it) }
            .flatMap { listOf(it, TextNormalizer.squeeze(it), keyOf(it)) }
            .filter { it.isNotEmpty() }
            .distinct()
        return stems.joinToString(" ", prefix = " ", postfix = " ")
    }

    /** Основы слов запроса. Короткие служебные слова отбрасываются. */
    fun queryStems(query: String): List<String> =
        TextNormalizer.tokens(query)
            .filter { it.length >= 2 || it.all(Char::isDigit) }
            .filterNot { it in stopWords }
            .map { keyOf(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    private val stopWords = setOf("и", "в", "с", "со", "на", "из", "без", "для", "по", "the", "of", "and", "with", "or")

    /**
     * Совпадает ли индекс с запросом: каждая основа запроса должна быть началом
     * какого-то слова индекса (или слово индекса — началом основы, если оно не короче 6 букв:
     * «картофелин» находит «картофель»).
     */
    fun matches(index: String, stems: List<String>): Boolean {
        if (stems.isEmpty()) return false
        val words = index.trim().split(' ')
        return stems.all { s -> words.any { w -> wordMatches(w, s) } }
    }

    internal fun wordMatches(word: String, stem: String): Boolean {
        if (word.startsWith(stem) || (word.length >= 6 && stem.startsWith(word))) return true
        if (stem.length < FUZZY_MIN_LENGTH || word.length < FUZZY_MIN_LENGTH - 1) return false
        // Одна опечатка в начале слова: сравниваем основу с началом слова той же длины (±1).
        return (stem.length - 1..stem.length + 1).any { len ->
            len <= word.length && osaDistance(word.substring(0, len), stem) <= 1
        }
    }

    /** Расстояние Дамерау — Левенштейна (оптимальное выравнивание строк). */
    internal fun osaDistance(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }

    /**
     * Оценка релевантности для сортировки (больше — лучше; 0 — не совпадает):
     * точные совпадения слов, совпадение начала названия, короткие названия выше длинных.
     */
    fun score(name: String, index: String, stems: List<String>): Double {
        if (!matches(index, stems)) return 0.0
        val nameTokens = TextNormalizer.tokens(name).map { keyOf(it) }
        var score = 10.0
        for (s in stems) {
            if (nameTokens.any { it == s }) score += 3.0
            else if (nameTokens.any { it.startsWith(s) || s.startsWith(it) }) score += 1.5
            else if (nameTokens.any { wordMatches(it, s) }) score += 1.0
        }
        if (nameTokens.isNotEmpty() && stems.isNotEmpty() && (nameTokens[0].startsWith(stems[0]) || stems[0].startsWith(nameTokens[0]))) {
            score += 2.0
        }
        score -= (nameTokens.size.coerceAtMost(20)) * 0.15
        return score
    }

    /** Релевантность продукта запросу без сохранённого индекса (для результатов из интернета). */
    fun relevance(query: String, name: String, vararg other: String?): Double =
        score(name, buildIndex(name, *other), queryStems(query))
}
