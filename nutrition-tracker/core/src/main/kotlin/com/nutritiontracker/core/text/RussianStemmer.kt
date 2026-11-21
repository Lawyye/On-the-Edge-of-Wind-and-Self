package com.nutritiontracker.core.text

/**
 * Стеммер Snowball для русского языка (алгоритм М. Портера).
 * Нужен, чтобы «куриного филе», «куриное филе» и «Куриное филе отварное»
 * сводились к общим основам при поиске продуктов.
 */
object RussianStemmer {
    private const val VOWELS = "аеиоуыэюя"

    private val perfectiveGerund1 = listOf("в", "вши", "вшись")
    private val perfectiveGerund2 = listOf("ив", "ивши", "ившись", "ыв", "ывши", "ывшись")
    private val reflexive = listOf("ся", "сь")
    private val adjective = listOf(
        "ее", "ие", "ые", "ое", "ими", "ыми", "ей", "ий", "ый", "ой", "ем", "им", "ым", "ом",
        "его", "ого", "ему", "ому", "их", "ых", "ую", "юю", "ая", "яя", "ою", "ею",
    )
    private val participle1 = listOf("ем", "нн", "вш", "ющ", "щ")
    private val participle2 = listOf("ивш", "ывш", "ующ")
    private val verb1 = listOf(
        "ла", "на", "ете", "йте", "ли", "й", "л", "ем", "н", "ло", "но", "ет", "ют", "ны", "ть", "ешь", "нно",
    )
    private val verb2 = listOf(
        "ила", "ыла", "ена", "ейте", "уйте", "ите", "или", "ыли", "ей", "уй", "ил", "ыл", "им", "ым", "ен",
        "ило", "ыло", "ено", "ят", "ует", "уют", "ит", "ыт", "ены", "ить", "ыть", "ишь", "ую", "ю",
    )
    private val noun = listOf(
        "а", "ев", "ов", "ие", "ье", "е", "иями", "ями", "ами", "еи", "ии", "и", "ией", "ей", "ой", "ий", "й",
        "иям", "ям", "ием", "ем", "ам", "ом", "о", "у", "ах", "иях", "ях", "ы", "ь", "ию", "ью", "ю", "ия", "ья", "я",
    )
    private val derivational = listOf("ост", "ость")

    private fun isVowel(c: Char) = c in VOWELS

    fun stem(input: String): String {
        val word = input.lowercase().replace('ё', 'е')
        val sb = StringBuilder(word)

        // Области: RV — после первой гласной; R2 — после «гласная, согласная» дважды.
        var pV = -1
        var i = 0
        while (i < word.length) {
            if (isVowel(word[i])) { pV = i + 1; break }
            i++
        }
        if (pV == -1) return word
        var p2 = word.length
        run {
            var j = pV
            while (j < word.length && isVowel(word[j])) j++ // после первой гласной ищем согласную
            if (j >= word.length) return@run
            j++ // R1 начинается после неё
            while (j < word.length && !isVowel(word[j])) j++
            if (j >= word.length) return@run
            j++
            while (j < word.length && isVowel(word[j])) j++
            if (j >= word.length) return@run
            p2 = j + 1
        }

        // Шаг 1
        if (!removeWithAYa(sb, perfectiveGerund1, perfectiveGerund2, pV)) {
            removeLongest(sb, reflexive, pV)
            val removedAdjectival = removeAdjectival(sb, pV)
            if (!removedAdjectival) {
                if (!removeWithAYa(sb, verb1, verb2, pV)) {
                    removeLongest(sb, noun, pV)
                }
            }
        }
        // Шаг 2
        if (sb.length > pV && sb.endsWith("и")) sb.setLength(sb.length - 1)
        // Шаг 3: словообразовательный суффикс целиком в R2
        val der = longestSuffix(sb, derivational, maxOf(pV, p2))
        if (der != null) sb.setLength(sb.length - der.length)
        // Шаг 4
        tidyUp(sb, pV)
        return sb.toString()
    }

    private fun longestSuffix(sb: StringBuilder, suffixes: List<String>, minStart: Int): String? =
        suffixes.filter { sb.endsWith(it) && sb.length - it.length >= minStart }.maxByOrNull { it.length }

    private fun removeLongest(sb: StringBuilder, suffixes: List<String>, minStart: Int): Boolean {
        val s = longestSuffix(sb, suffixes, minStart) ?: return false
        sb.setLength(sb.length - s.length)
        return true
    }

    /**
     * Ищет самое длинное окончание среди обеих групп. Окончания первой группы
     * удаляются, только если перед ними стоит «а» или «я» внутри RV.
     */
    private fun removeWithAYa(sb: StringBuilder, group1: List<String>, group2: List<String>, pV: Int): Boolean {
        val s = longestSuffix(sb, group1 + group2, pV) ?: return false
        if (s in group1) {
            val pos = sb.length - s.length - 1
            if (pos < pV || (sb[pos] != 'а' && sb[pos] != 'я')) return false
        }
        sb.setLength(sb.length - s.length)
        return true
    }

    private fun removeAdjectival(sb: StringBuilder, pV: Int): Boolean {
        if (!removeLongest(sb, adjective, pV)) return false
        // Необязательное причастное окончание перед прилагательным.
        val s = longestSuffix(sb, participle1 + participle2, pV)
        if (s != null) {
            if (s in participle2) {
                sb.setLength(sb.length - s.length)
            } else {
                val pos = sb.length - s.length - 1
                if (pos >= pV && (sb[pos] == 'а' || sb[pos] == 'я')) sb.setLength(sb.length - s.length)
            }
        }
        return true
    }

    private fun tidyUp(sb: StringBuilder, pV: Int) {
        val s = longestSuffix(sb, listOf("ейш", "ейше", "н", "ь"), pV) ?: return
        when (s) {
            "ейш", "ейше" -> {
                sb.setLength(sb.length - s.length)
                if (sb.length - 2 >= pV && sb.endsWith("нн")) sb.setLength(sb.length - 1)
            }
            "н" -> if (sb.length - 2 >= pV && sb.endsWith("нн")) sb.setLength(sb.length - 1)
            "ь" -> sb.setLength(sb.length - 1)
        }
    }
}
