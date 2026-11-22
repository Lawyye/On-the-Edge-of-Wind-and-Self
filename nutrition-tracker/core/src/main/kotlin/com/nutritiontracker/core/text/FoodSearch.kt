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

    /** Штраф за каждое слово названия, которого нет в запросе, — для русских названий продуктов. */
    const val EXTRA_WORD_PENALTY = 0.8

    /** Мягкий штраф для описаний USDA («Chicken, breast, boneless, skinless, raw»), где уточнения — норма. */
    const val USDA_EXTRA_WORD_PENALTY = 0.15

    /**
     * Оценка релевантности для сортировки (больше — лучше; 0 — не совпадает).
     *
     * В русских названиях главное слово обычно первое: «Ветчина из куриного
     * филе» — это ветчина, а не филе. Поэтому первое слово названия должно
     * совпадать с каким-то словом запроса, а каждое лишнее слово снижает оценку.
     */
    fun score(
        name: String,
        index: String,
        stems: List<String>,
        extraWordPenalty: Double = EXTRA_WORD_PENALTY,
        mildWords: Set<String> = emptySet(),
        mildPenalty: Double = extraWordPenalty,
    ): Double {
        if (!matches(index, stems)) return 0.0
        val nameTokens = TextNormalizer.tokens(name).map { keyOf(it) }
        var score = 10.0
        for (s in stems) {
            if (nameTokens.any { it == s }) score += 3.0
            else if (nameTokens.any { it.startsWith(s) || s.startsWith(it) }) score += 1.5
            else if (nameTokens.any { wordMatches(it, s) }) score += 1.0
        }
        val head = nameTokens.firstOrNull { it !in stopWords && it.any(Char::isLetter) }
        if (head != null && stems.any { s -> head.startsWith(s) || s.startsWith(head) || wordMatches(head, s) }) {
            score += 3.0
        }
        val extra = nameTokens.filter { t ->
            t.length >= 3 && t !in stopWords && t.any(Char::isLetter) &&
                stems.none { s -> t.startsWith(s) || s.startsWith(t) || wordMatches(t, s) }
        }.take(8)
        score -= extra.sumOf { t -> if (t in mildWords) mildPenalty else extraWordPenalty }
        return score.coerceAtLeast(0.1)
    }

    /** Релевантность продукта запросу без сохранённого индекса (для результатов из интернета). */
    fun relevance(query: String, name: String, vararg other: String?): Double =
        score(name, buildIndex(name, *other), queryStems(query))

    /**
     * То же для английских описаний USDA. Слова состояния и обработки («raw»,
     * «cooked», «lowfat») почти не снижают оценку, а слова, которые делают из
     * продукта другое блюдо («sauce», «dressing», «crackers»), — снижают
     * заметно: на «spaghetti» нужны спагетти, а не соус для них.
     */
    fun relevanceUsda(query: String, name: String, vararg other: String?): Double =
        score(name, buildIndex(name, *other), queryStems(query), USDA_DISH_WORD_PENALTY, usdaDescriptors, USDA_DESCRIPTOR_PENALTY)

    const val USDA_DISH_WORD_PENALTY = 1.2
    const val USDA_DESCRIPTOR_PENALTY = 0.1

    /** Слова описаний USDA, которые уточняют продукт (состояние, обработку, сорт), но не меняют его. */
    private val usdaDescriptors: Set<String> = (
        "raw cooked boiled roasted baked broiled grilled braised stewed steamed simmered poached fried pan dry dried " +
            "enriched unenriched fortified regular plain white brown wild whole grain long medium short instant quick " +
            "rolled old fashioned flakes groats pearled hulled kernels mature seeds sprouted " +
            "without salt added unsalted salted skin skinless boneless bone meat only lean fat lowfat nonfat milkfat " +
            "reduced creamed uncreamed curd small large fresh frozen canned drained solids liquid prepared unprepared " +
            "nfs ns made from water fluid grade grades choice select trimmed separable all commercial includes " +
            "broilers broiler fryers fryer roasting not eaten ripe peeled unpeeled flesh yellow red green golden " +
            "sweet extra virgin light dark cultured vitamin added dairy cow mixed pieces sliced chopped florets " +
            "uncooked thawed ready serve heated stick sticks whipped"
        ).split(' ').filter { it.isNotBlank() }.map { keyOf(it) }.toSet()

    /**
     * Оценка кандидата для автоматического выбора в голосовом вводе.
     * Сохранённые и онлайн-варианты сравниваются на равных. Плюс — полноте
     * состава, базовым продуктам USDA (Foundation, SR Legacy), своим продуктам
     * и рецептам, продуктам, которые уже добавлялись в дневник. Продукт без
     * калорийности сам не выбирается.
     */
    fun autoPickScore(
        query: String,
        englishQuery: String?,
        data: com.nutritiontracker.core.model.FoodData,
        aliases: List<String> = emptyList(),
        useCount: Int = 0,
    ): Double {
        val base = if (data.source.isUsda) {
            maxOf(
                englishQuery?.let { relevanceUsda(it, data.name, data.category) } ?: 0.0,
                aliases.maxOfOrNull { a -> relevance(query, a) } ?: 0.0,
            )
        } else {
            relevance(query, aliases.firstOrNull() ?: data.name, data.name, data.brand, data.category, data.nameEn)
        }
        if (base <= 0.0 || !data.nutrients.isKnown(com.nutritiontracker.core.model.Nutrient.ENERGY)) return 0.0
        var bonus = data.nutrients.known.size / 20.0
        val src = data.source
        if (src == com.nutritiontracker.core.model.FoodSource.USDA_FOUNDATION || src == com.nutritiontracker.core.model.FoodSource.USDA_SR_LEGACY) bonus += 1.5
        if (src == com.nutritiontracker.core.model.FoodSource.USER || src == com.nutritiontracker.core.model.FoodSource.RECIPE) bonus += 3.0
        bonus += minOf(useCount, 5) * 0.4
        return base + bonus
    }
}
