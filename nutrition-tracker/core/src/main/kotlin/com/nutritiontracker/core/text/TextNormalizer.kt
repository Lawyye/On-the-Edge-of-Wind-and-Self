package com.nutritiontracker.core.text

object TextNormalizer {
    private val separators = Regex("[^\\p{L}\\p{N}]+")

    /** Нижний регистр, ё → е, всё кроме букв и цифр — пробелы. */
    fun normalize(text: String): String =
        text.lowercase()
            .replace('ё', 'е')
            .replace(separators, " ")
            .trim()

    fun tokens(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /**
     * Схлопывает повторяющиеся буквы: «куринного» → «куриного», «грамм» → «грам».
     * Только для сопоставления при поиске, не для показа.
     */
    fun squeeze(token: String): String {
        val sb = StringBuilder(token.length)
        for (ch in token) {
            if (sb.isEmpty() || sb[sb.length - 1] != ch || !ch.isLetter()) sb.append(ch)
        }
        return sb.toString()
    }

    fun isCyrillic(token: String): Boolean = token.any { it in 'а'..'я' }

    fun isLatin(token: String): Boolean = token.any { it in 'a'..'z' }

    /** Основа слова для поиска: русские слова — стеммером Snowball, английские — упрощённо. */
    fun stem(token: String): String = when {
        isCyrillic(token) -> RussianStemmer.stem(token)
        isLatin(token) -> englishStem(token)
        else -> token
    }

    private fun englishStem(token: String): String = when {
        token.length > 4 && token.endsWith("ies") -> token.dropLast(3) + "y"
        token.length > 4 && (token.endsWith("oes") || token.endsWith("ches") || token.endsWith("shes")) -> token.dropLast(2)
        token.length > 3 && token.endsWith("s") && !token.endsWith("ss") -> token.dropLast(1)
        else -> token
    }

    fun stems(text: String): List<String> = tokens(text).map { stem(it) }
}
