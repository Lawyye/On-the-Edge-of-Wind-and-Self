package com.nutritiontracker.core.voice

/**
 * Числа, записанные словами: «двести пятьдесят», «полтора», «два с половиной»,
 * «одну», «пару». Распознавание речи обычно отдаёт цифры, но не всегда.
 */
object RussianNumbers {
    private val units = mapOf(
        "ноль" to 0, "один" to 1, "одна" to 1, "одно" to 1, "одну" to 1, "одного" to 1, "одной" to 1, "одним" to 1,
        "два" to 2, "две" to 2, "двух" to 2, "двумя" to 2,
        "три" to 3, "трех" to 3, "тремя" to 3,
        "четыре" to 4, "четырех" to 4, "четырьмя" to 4,
        "пять" to 5, "пяти" to 5, "шесть" to 6, "шести" to 6, "семь" to 7, "семи" to 7,
        "восемь" to 8, "восьми" to 8, "девять" to 9, "девяти" to 9,
    )
    private val teens = mapOf(
        "десять" to 10, "десяти" to 10, "одиннадцать" to 11, "двенадцать" to 12, "тринадцать" to 13,
        "четырнадцать" to 14, "пятнадцать" to 15, "шестнадцать" to 16, "семнадцать" to 17,
        "восемнадцать" to 18, "девятнадцать" to 19,
    )
    private val tens = mapOf(
        "двадцать" to 20, "двадцати" to 20, "тридцать" to 30, "тридцати" to 30, "сорок" to 40, "сорока" to 40,
        "пятьдесят" to 50, "пятидесяти" to 50, "шестьдесят" to 60, "семьдесят" to 70,
        "восемьдесят" to 80, "девяносто" to 90,
    )
    private val hundreds = mapOf(
        "сто" to 100, "ста" to 100, "двести" to 200, "двухсот" to 200, "триста" to 300, "трехсот" to 300,
        "четыреста" to 400, "пятьсот" to 500, "пятисот" to 500, "шестьсот" to 600, "семьсот" to 700,
        "восемьсот" to 800, "девятьсот" to 900,
    )
    private val thousands = setOf("тысяча", "тысячи", "тысяч", "тысячу")
    private val fractions = mapOf(
        "полтора" to 1.5, "полторы" to 1.5,
        "пол" to 0.5, "половина" to 0.5, "половину" to 0.5, "половинка" to 0.5, "половинку" to 0.5, "половинки" to 0.5,
        "четверть" to 0.25, "четвертинку" to 0.25,
        "пара" to 2.0, "пару" to 2.0,
    )

    data class Parsed(val value: Double, val consumed: Int)

    fun isNumberWord(token: String): Boolean =
        token in units || token in teens || token in tens || token in hundreds || token in thousands || token in fractions

    fun parseDigits(token: String): Double? {
        if (!token.matches(Regex("\\d+(\\.\\d+)?"))) return null
        return token.toDoubleOrNull()
    }

    /**
     * Читает число, начиная с позиции [start]. Возвращает значение и количество
     * прочитанных слов либо null, если с этой позиции число не начинается.
     */
    fun parse(tokens: List<String>, start: Int): Parsed? {
        if (start >= tokens.size) return null
        val first = tokens[start]

        parseDigits(first)?.let { v ->
            // «2 с половиной»
            if (start + 2 < tokens.size && tokens[start + 1] == "с" && tokens[start + 2].startsWith("половин")) {
                return Parsed(v + 0.5, 3)
            }
            return Parsed(v, 1)
        }
        fractions[first]?.let { return Parsed(it, 1) }

        var total = 0
        var current = 0
        var lastMagnitude = Int.MAX_VALUE
        var i = start
        var consumedAny = false
        while (i < tokens.size) {
            val t = tokens[i]
            val (value, magnitude) = when (t) {
                in hundreds -> hundreds.getValue(t) to 100
                in tens -> tens.getValue(t) to 10
                in teens -> teens.getValue(t) to 1
                in units -> units.getValue(t) to 1
                in thousands -> -1 to 1000
                else -> break
            }
            if (magnitude == 1000) {
                total += (if (current == 0) 1 else current) * 1000
                current = 0
                lastMagnitude = Int.MAX_VALUE
                consumedAny = true
                i++
                continue
            }
            // «двести пятьдесят» — разряды убывают; «два три» — это два разных числа.
            if (magnitude >= lastMagnitude) break
            if (magnitude == 1 && t in teens && lastMagnitude == 10) break
            current += value
            lastMagnitude = if (t in teens) 0 else magnitude
            consumedAny = true
            i++
        }
        if (!consumedAny) return null
        var result = (total + current).toDouble()
        var consumed = i - start
        if (i + 1 < tokens.size && tokens[i] == "с" && tokens[i + 1].startsWith("половин")) {
            result += 0.5
            consumed += 2
        }
        return Parsed(result, consumed)
    }
}
