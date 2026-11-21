package com.nutritiontracker.core.text

/**
 * Поисковый индекс продукта: нормализованные слова и их основы из названия,
 * альтернативных названий, английского названия, производителя и категории.
 */
object FoodSearch {
    /** Строка для хранения в базе: основы через пробел, обрамлённые пробелами для поиска по началу слова. */
    fun buildIndex(vararg parts: String?): String {
        val stems = parts.filterNotNull().flatMap { TextNormalizer.tokens(it) }
            .flatMap { listOf(it, TextNormalizer.stem(it)) }
            .filter { it.isNotEmpty() }
            .distinct()
        return stems.joinToString(" ", prefix = " ", postfix = " ")
    }

    /** Основы слов запроса. Короткие служебные слова отбрасываются. */
    fun queryStems(query: String): List<String> =
        TextNormalizer.tokens(query)
            .filter { it.length >= 2 || it.all(Char::isDigit) }
            .filterNot { it in stopWords }
            .map { TextNormalizer.stem(it) }
            .filter { it.isNotEmpty() }
            .distinct()

    private val stopWords = setOf("и", "в", "с", "со", "на", "из", "без", "для", "по", "the", "of", "and", "with", "or")

    /**
     * Совпадает ли индекс с запросом: каждая основа запроса должна быть началом
     * какого-то слова индекса (или слово индекса — началом основы, если оно не короче 4 букв:
     * «картофелин» находит «картофель»).
     */
    fun matches(index: String, stems: List<String>): Boolean {
        if (stems.isEmpty()) return false
        val words = index.trim().split(' ')
        return stems.all { s -> words.any { w -> wordMatches(w, s) } }
    }

    private fun wordMatches(word: String, stem: String): Boolean =
        word.startsWith(stem) || (word.length >= 4 && stem.startsWith(word))

    /**
     * Оценка релевантности для сортировки (больше — лучше): точные совпадения
     * слов, совпадение начала названия, короткие названия выше длинных.
     */
    fun score(name: String, index: String, stems: List<String>): Double {
        if (!matches(index, stems)) return 0.0
        val nameTokens = TextNormalizer.tokens(name).map { TextNormalizer.stem(it) }
        var score = 10.0
        for (s in stems) {
            if (nameTokens.any { it == s }) score += 3.0
            else if (nameTokens.any { it.startsWith(s) || s.startsWith(it) }) score += 1.5
        }
        if (nameTokens.isNotEmpty() && stems.isNotEmpty() && (nameTokens[0].startsWith(stems[0]) || stems[0].startsWith(nameTokens[0]))) {
            score += 2.0
        }
        score -= (nameTokens.size.coerceAtMost(20)) * 0.15
        return score
    }
}
