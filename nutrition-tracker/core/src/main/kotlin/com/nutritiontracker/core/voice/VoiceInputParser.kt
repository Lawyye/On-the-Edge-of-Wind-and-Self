package com.nutritiontracker.core.voice

import com.nutritiontracker.core.model.MealType

enum class CountKind { PIECE, PORTION, PACKAGE, HOUSEHOLD }

sealed interface ParsedQuantity {
    /** Масса в граммах (из «г», «кг», «полкило»). */
    data class Mass(val grams: Double) : ParsedQuantity

    /** Объём в миллилитрах (из «мл», «л»). */
    data class Volume(val milliliters: Double) : ParsedQuantity

    /**
     * Количество штук, порций, упаковок или бытовых мер («стакан»).
     * [eachGrams]/[eachMl] заданы, если вес одной штуки назван явно:
     * «три картофелины по 100 граммов».
     */
    data class Count(
        val count: Double,
        val kind: CountKind,
        val unitWord: String? = null,
        val eachGrams: Double? = null,
        val eachMl: Double? = null,
    ) : ParsedQuantity
}

data class ParsedFoodItem(
    /** Фрагмент исходной фразы, к которому относится позиция. */
    val sourceText: String,
    /** Название продукта так, как оно прозвучало (в нижнем регистре). */
    val name: String,
    /** null — количество не прозвучало; его нужно спросить у пользователя. */
    val quantity: ParsedQuantity?,
    val notes: List<String> = emptyList(),
)

data class ParsedUtterance(
    val items: List<ParsedFoodItem>,
    val mealHint: MealType?,
    /** Смещение даты: 0 — сегодня, −1 — вчера; null — не прозвучало. */
    val dayOffset: Int?,
)

/**
 * Разбор продиктованного списка продуктов. Ничего не досчитывает за
 * пользователя: если количество не прозвучало, оно остаётся пустым.
 */
object VoiceInputParser {
    private val gramUnits = setOf("г", "гр", "грамм", "грамма", "граммов", "граммы", "грам", "грамов", "граммах", "граммам")
    private val kgUnits = setOf("кг", "килограмм", "килограмма", "килограммов", "килограммы", "кило")
    private val mlUnits = setOf("мл", "миллилитр", "миллилитра", "миллилитров", "миллилитры")
    private val literUnits = setOf("л", "литр", "литра", "литров", "литры", "литре")
    private val pieceUnits = setOf(
        "шт", "штук", "штука", "штуки", "штуку", "штучки", "штучек", "штучка",
        "кусок", "куска", "куски", "кусков", "кусочек", "кусочка", "кусочков", "кусочки",
        "ломтик", "ломтика", "ломтиков", "ломтики", "долька", "дольки", "долек", "дольку",
    )
    private val portionUnits = setOf("порция", "порции", "порций", "порцию")
    private val packageUnits = setOf(
        "пачка", "пачки", "пачку", "пачек", "упаковка", "упаковки", "упаковку", "упаковок",
        "банка", "банки", "банку", "банок", "бутылка", "бутылки", "бутылку", "бутылок",
    )
    private val householdUnits = setOf(
        "стакан", "стакана", "стаканов", "стаканчик", "стаканчика", "чашка", "чашки", "чашку", "чашек",
        "кружка", "кружки", "кружку", "кружек", "ложка", "ложки", "ложку", "ложек",
        "тарелка", "тарелки", "тарелку", "тарелок", "миска", "миски", "миску", "горсть", "горсти",
    )
    private val householdPrefixes = setOf("столовая", "столовые", "столовых", "столовую", "чайная", "чайные", "чайных", "чайную")

    private val fillers = setOf(
        "я", "мы", "съел", "съела", "съели", "поел", "поела", "поели", "покушал", "покушала", "скушал", "скушала",
        "выпил", "выпила", "выпили", "добавь", "добавить", "добавьте", "запиши", "записать", "запишите",
        "примерно", "около", "где", "то", "приблизительно", "у", "меня", "было", "был", "была", "были",
        "это", "вот", "ну", "сегодня", "вчера", "позавчера", "утром", "днем", "вечером", "ночью",
        "еще", "также", "потом", "затем", "плюс", "ещё",
    )
    private val connectorWords = setOf("и", "плюс", "еще", "потом", "затем", "также")

    private val mealPhrases = listOf(
        listOf("на", "завтрак") to MealType.BREAKFAST, listOf("за", "завтраком") to MealType.BREAKFAST,
        listOf("позавтракал") to MealType.BREAKFAST, listOf("позавтракала") to MealType.BREAKFAST,
        listOf("на", "обед") to MealType.LUNCH, listOf("в", "обед") to MealType.LUNCH, listOf("за", "обедом") to MealType.LUNCH,
        listOf("пообедал") to MealType.LUNCH, listOf("пообедала") to MealType.LUNCH,
        listOf("на", "ужин") to MealType.DINNER, listOf("за", "ужином") to MealType.DINNER,
        listOf("поужинал") to MealType.DINNER, listOf("поужинала") to MealType.DINNER,
        listOf("на", "перекус") to MealType.SNACK, listOf("перекусил") to MealType.SNACK,
        listOf("перекусила") to MealType.SNACK, listOf("перекус") to MealType.SNACK,
        // «Завтрак: овсянка 50 г» — одиночное слово тоже задаёт приём пищи.
        listOf("завтрак") to MealType.BREAKFAST, listOf("обед") to MealType.LUNCH,
        listOf("ужин") to MealType.DINNER, listOf("перекусы") to MealType.SNACK,
    )

    /**
     * Уточнения после количества относятся к предыдущему продукту, а не
     * начинают новый: «курица 200 г отварная», «рис 150 г варёный».
     */
    private val modifierPrefixes = listOf(
        "отварн", "варен", "жарен", "тушен", "запечен", "копчен", "вялен", "сушен", "свеж",
        "обезжир", "нежирн", "маложир", "домашн", "гриль", "консервир", "маринован", "солен",
    )
    private val modifierWords = setOf("на_пару", "сырой", "сырая", "сырое", "сырые", "сырых", "сырую", "сырого", "сырым")

    private fun isModifier(token: String): Boolean = token in modifierWords || modifierPrefixes.any { token.startsWith(it) }

    fun parse(text: String): ParsedUtterance {
        var normalized = text.lowercase().replace('ё', 'е')
        // Десятичная запятая: «1,5 литра» → «1.5 литра».
        normalized = normalized.replace(Regex("(\\d),(\\d)"), "$1.$2")
        // Сокращения с точкой: «200 г. курицы».
        normalized = normalized.replace(Regex("(?<=\\s|\\d)(г|гр|кг|мл|л|шт)\\."), "$1 ")
        // Диапазоны «200-300 г» отмечаются отдельно.
        normalized = normalized.replace(Regex("(\\d+(?:\\.\\d+)?)\\s*[-–—]\\s*(\\d+(?:\\.\\d+)?)"), " диапазон_$1_$2 ")
        // «200г» → «200 г»
        normalized = normalized.replace(Regex("(\\d)([а-я])"), "$1 $2")
        normalized = normalized.replace("пол-", "пол ")
        // «на пару» — способ приготовления, а не «пара» как число 2.
        normalized = normalized.replace(Regex("(^|\\s)на\\s+пару(?![\\p{L}])"), "$1на_пару")
        normalized = normalized.replace(':', ',')

        val allTokens = normalized.split(Regex("[^\\p{L}\\p{N}._]+")).filter { it.isNotBlank() }
        val (mealHint, dayOffset) = detectHints(allTokens)

        val segments = normalized
            .split(Regex("(?<!\\d)\\.(?!\\d)|[,;!?\\n]"))
            .flatMap { splitByConnectors(it) }

        val items = mutableListOf<ParsedFoodItem>()
        for (segment in segments) {
            val tokens = segment.split(Regex("[^\\p{L}\\p{N}._]+"))
                .filter { it.isNotBlank() }
                .let { removeMealPhrases(it) }
                .filterNot { it in fillers }
            if (tokens.isEmpty()) continue
            items += parseSegment(tokens)
        }
        return ParsedUtterance(items.filter { it.name.isNotBlank() || it.quantity != null }, mealHint, dayOffset)
    }

    private fun detectHints(tokens: List<String>): Pair<MealType?, Int?> {
        var meal: MealType? = null
        for ((phrase, type) in mealPhrases) {
            if (containsSequence(tokens, phrase)) { meal = type; break }
        }
        val day = when {
            "позавчера" in tokens -> -2
            "вчера" in tokens -> -1
            "сегодня" in tokens -> 0
            else -> null
        }
        return meal to day
    }

    private fun containsSequence(tokens: List<String>, seq: List<String>): Boolean {
        if (seq.size > tokens.size) return false
        for (i in 0..tokens.size - seq.size) {
            if ((seq.indices).all { tokens[i + it] == seq[it] }) return true
        }
        return false
    }

    private fun removeMealPhrases(tokens: List<String>): List<String> {
        val result = tokens.toMutableList()
        for ((phrase, _) in mealPhrases.sortedByDescending { it.first.size }) {
            var i = 0
            while (i <= result.size - phrase.size) {
                if (phrase.indices.all { result[i + it] == phrase[it] }) {
                    repeat(phrase.size) { result.removeAt(i) }
                } else {
                    i++
                }
            }
        }
        return result
    }

    private fun splitByConnectors(segment: String): List<String> {
        val words = segment.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val parts = mutableListOf<MutableList<String>>(mutableListOf())
        var i = 0
        while (i < words.size) {
            val w = words[i]
            val isAlso = w == "а" && i + 1 < words.size && words[i + 1] == "также"
            if (w in connectorWords || isAlso) {
                if (parts.last().isNotEmpty()) parts += mutableListOf<String>()
                i += if (isAlso) 2 else 1
                continue
            }
            // «с» / «со» разделяет позиции, только если за ним идёт количество: «гречка 200 г с 10 г масла».
            if ((w == "с" || w == "со") && i + 1 < words.size && startsQuantity(words, i + 1) && parts.last().isNotEmpty()) {
                parts += mutableListOf<String>()
                i++
                continue
            }
            parts.last() += w
            i++
        }
        return parts.filter { it.isNotEmpty() }.map { it.joinToString(" ") }
    }

    private fun startsQuantity(words: List<String>, index: Int): Boolean {
        val w = words[index]
        return RussianNumbers.parseDigits(w) != null || RussianNumbers.isNumberWord(w) ||
            w == "полкило" || w == "поллитра" || w.startsWith("диапазон_")
    }

    private data class QuantityMatch(val quantity: ParsedQuantity?, val consumed: Int, val note: String? = null)

    private fun unitAt(tokens: List<String>, i: Int): Pair<String, Int>? {
        if (i >= tokens.size) return null
        val t = tokens[i]
        if (t in householdPrefixes && i + 1 < tokens.size && tokens[i + 1] in householdUnits) {
            return "${t} ${tokens[i + 1]}" to 2
        }
        val known = t in gramUnits || t in kgUnits || t in mlUnits || t in literUnits ||
            t in pieceUnits || t in portionUnits || t in packageUnits || t in householdUnits
        return if (known) t to 1 else null
    }

    private fun quantityFor(value: Double, unit: String?): ParsedQuantity {
        val last = unit?.substringAfterLast(' ')
        return when {
            last == null -> ParsedQuantity.Count(value, CountKind.PIECE)
            last in gramUnits -> ParsedQuantity.Mass(value)
            last in kgUnits -> ParsedQuantity.Mass(value * 1000.0)
            last in mlUnits -> ParsedQuantity.Volume(value)
            last in literUnits -> ParsedQuantity.Volume(value * 1000.0)
            last in pieceUnits -> ParsedQuantity.Count(value, CountKind.PIECE, unit)
            last in portionUnits -> ParsedQuantity.Count(value, CountKind.PORTION, unit)
            last in packageUnits -> ParsedQuantity.Count(value, CountKind.PACKAGE, unit)
            else -> ParsedQuantity.Count(value, CountKind.HOUSEHOLD, unit)
        }
    }

    /** Пытается прочитать количество с позиции [i]. */
    private fun quantityAt(tokens: List<String>, i: Int): QuantityMatch? {
        val t = tokens[i]
        if (t == "полкило") return QuantityMatch(ParsedQuantity.Mass(500.0), 1)
        if (t == "поллитра") return QuantityMatch(ParsedQuantity.Volume(500.0), 1)
        if (t.startsWith("полстакан") || t.startsWith("полчашк") || t.startsWith("полтарелк")) {
            return QuantityMatch(ParsedQuantity.Count(0.5, CountKind.HOUSEHOLD, t.removePrefix("пол")), 1)
        }
        if (t.startsWith("диапазон_")) {
            val unit = unitAt(tokens, i + 1)
            val parts = t.removePrefix("диапазон_").replace('_', '–')
            return QuantityMatch(
                null,
                1 + (unit?.second ?: 0),
                "Названо «$parts${unit?.first?.let { " $it" } ?: ""}» — укажите точное количество",
            )
        }
        val number = RussianNumbers.parse(tokens, i)
        if (number != null) {
            if (number.value <= 0.0) return null
            val unit = unitAt(tokens, i + number.consumed)
            return QuantityMatch(quantityFor(number.value, unit?.first), number.consumed + (unit?.second ?: 0))
        }
        // Мера без числа: «килограмм картошки», «литр молока», «стакан кефира».
        val unit = unitAt(tokens, i)
        if (unit != null) {
            val u = unit.first.substringAfterLast(' ')
            return when {
                u in gramUnits -> QuantityMatch(null, unit.second, "Не прозвучало, сколько граммов")
                else -> QuantityMatch(quantityFor(1.0, unit.first), unit.second)
            }
        }
        return null
    }

    private class Builder {
        val nameTokens = mutableListOf<String>()
        val sourceTokens = mutableListOf<String>()
        var quantity: ParsedQuantity? = null
        var quantityAfterName = false
        val notes = mutableListOf<String>()
        var hadQuantityToken = false

        fun build(): ParsedFoodItem {
            val name = nameTokens.dropWhile { it in edgeWords }.dropLastWhile { it in edgeWords }.joinToString(" ")
            if (quantity == null && notes.isEmpty()) notes += "Количество не прозвучало — укажите его"
            return ParsedFoodItem(sourceTokens.joinToString(" "), name, quantity, notes.toList())
        }

        val isEmpty get() = nameTokens.isEmpty() && !hadQuantityToken
    }

    private val edgeWords = setOf("с", "со", "из", "в", "на", "и", "а", "по")

    private fun parseSegment(tokens: List<String>): List<ParsedFoodItem> {
        val items = mutableListOf<ParsedFoodItem>()
        var cur = Builder()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            // «три картофелины по 100 граммов» — вес одной штуки.
            if (t == "по" && i + 1 < tokens.size) {
                val q = cur.quantity
                val each = quantityAt(tokens, i + 1)
                if (q is ParsedQuantity.Count && each != null) {
                    when (val e = each.quantity) {
                        is ParsedQuantity.Mass -> cur.quantity = q.copy(eachGrams = e.grams)
                        is ParsedQuantity.Volume -> cur.quantity = q.copy(eachMl = e.milliliters)
                        else -> {}
                    }
                    if (each.quantity is ParsedQuantity.Mass || each.quantity is ParsedQuantity.Volume) {
                        cur.sourceTokens += tokens.subList(i, i + 1 + each.consumed)
                        i += 1 + each.consumed
                        continue
                    }
                }
            }
            val match = quantityAt(tokens, i)
            if (match != null) {
                if (cur.hadQuantityToken && cur.nameTokens.isNotEmpty()) {
                    items += cur.build()
                    cur = Builder()
                }
                val existing = cur.quantity
                val incoming = match.quantity
                cur.quantity = when {
                    // «1 кг 200 г»
                    existing is ParsedQuantity.Mass && incoming is ParsedQuantity.Mass -> ParsedQuantity.Mass(existing.grams + incoming.grams)
                    existing is ParsedQuantity.Volume && incoming is ParsedQuantity.Volume -> ParsedQuantity.Volume(existing.milliliters + incoming.milliliters)
                    else -> incoming
                }
                cur.quantityAfterName = cur.nameTokens.isNotEmpty()
                cur.hadQuantityToken = true
                match.note?.let { cur.notes += it }
                cur.sourceTokens += tokens.subList(i, i + match.consumed)
                i += match.consumed
                continue
            }
            // После «банан 2» новое слово — уже следующий продукт.
            if (cur.hadQuantityToken && cur.quantityAfterName && t !in edgeWords && !isModifier(t)) {
                items += cur.build()
                cur = Builder()
            }
            cur.nameTokens += t
            cur.sourceTokens += t
            i++
        }
        if (!cur.isEmpty) items += cur.build()
        return items.map { it.copy(name = it.name.replace("на_пару", "на пару")) }
    }
}
