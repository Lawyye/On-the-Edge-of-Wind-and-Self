package com.nutritiontracker.core.text

/**
 * Переводит русский поисковый запрос в английский для USDA FoodData Central,
 * где названия продуктов только на английском.
 *
 * Словарь содержит только названия продуктов и способов приготовления —
 * никаких пищевых значений. Корни сравниваются с началом слов запроса,
 * поэтому «куриного», «куриное», «куриная» дают одно и то же.
 */
object FoodQueryTranslator {
    data class Result(
        /**
         * Английские запросы: первый — основной продукт с уточнениями (способ
         * приготовления), далее остальные найденные в запросе продукты по отдельности.
         */
        val englishQueries: List<String>,
        val translatedTokens: List<String>,
        val untranslatedTokens: List<String>,
    ) {
        val englishQuery: String? get() = englishQueries.firstOrNull()
    }

    private class Entry(val english: String, val alternatives: List<List<String>>, val isModifier: Boolean)

    private fun food(en: String, vararg alts: String) = Entry(en, alts.map { it.split(' ') }, false)
    private fun mod(en: String, vararg alts: String) = Entry(en, alts.map { it.split(' ') }, true)

    private val entries: List<Entry> = listOf(
        // Мясо и птица
        food("chicken breast", "курин филе", "куриц филе", "филе куриц", "филе курин", "куриц грудк", "курин грудк", "грудк куриц", "курин груд", "куриц груд"),
        food("chicken thigh", "куриц бедр", "курин бедр", "бедр куриц", "бедрышк"),
        food("chicken drumstick", "голен", "курин ножк", "куриц ножк"),
        food("chicken wing", "куриц крыл", "курин крыл", "крылышк"),
        food("chicken liver", "курин печен", "куриц печен", "печень куриц", "печен куриц"),
        food("chicken", "куриц", "курятин", "цыпл", "курин"),
        food("turkey breast", "филе индейк", "индейк филе", "индейк грудк", "грудк индейк"),
        food("turkey", "индейк", "индюшатин"),
        food("ground beef", "говяж фарш", "фарш говяж", "говядин фарш", "фарш говядин"),
        food("beef liver", "говяж печен", "печень говяж", "печен говяж"),
        food("beef", "говядин", "говяж"),
        food("ground pork", "свин фарш", "фарш свин", "свинин фарш"),
        food("pork", "свинин", "свин"),
        food("lamb", "баранин", "ягнятин"),
        food("veal", "телятин"),
        food("rabbit", "кролик", "крольчатин"),
        food("duck", "утк", "утятин"),
        food("beef liver", "печень", "печени"),
        food("bacon", "бекон"),
        food("ham", "ветчин"),
        food("frankfurter", "сосиск", "сардельк"),
        food("sausage", "колбас"),
        food("salami", "салями"),
        // Рыба и морепродукты
        food("pink salmon", "горбуш"),
        food("salmon", "лосос", "семг", "кета", "кеты", "кетой"),
        food("tuna", "тунец", "тунц"),
        food("cod", "треск"),
        food("pollock", "минта"),
        food("herring", "сельд", "селедк"),
        food("mackerel", "скумбри"),
        food("trout", "форел"),
        food("hake", "хек"),
        food("carp", "карп"),
        food("shrimp", "кревет"),
        food("squid", "кальмар"),
        food("crab", "краб"),
        food("fish", "рыб"),
        // Молочные продукты и яйца
        food("egg white", "яичн белок", "белок яйц", "белк яиц", "яичн белк"),
        food("egg yolk", "желток", "желтк"),
        food("egg fried", "яичниц", "глазунь"),
        food("egg whole", "яйц", "яйцо", "яиц"),
        food("milk", "молок"),
        food("kefir", "кефир"),
        food("yogurt", "йогурт"),
        food("cottage cheese", "творог", "творож"),
        food("butter", "сливоч масл", "масл сливоч", "масло сливоч"),
        food("cream", "сливк", "сливок"),
        food("sour cream", "сметан"),
        food("mozzarella cheese", "моцарел"),
        food("parmesan cheese", "пармез"),
        food("feta cheese", "фета", "фетой", "брынз"),
        food("cheese", "сыр", "сырок", "сырн"),
        // Крупы, хлеб, макароны
        food("rice", "рис", "рисов"),
        food("buckwheat groats", "греч"),
        food("oats", "овсян", "овес", "овса", "геркулес"),
        food("pasta", "макарон", "спагет", "паста", "пасты", "пасту"),
        food("noodles", "лапш"),
        food("bread rye", "ржан хлеб", "хлеб ржан", "черн хлеб", "хлеб черн", "бородин"),
        food("bread whole wheat", "цельнозерн хлеб", "хлеб цельнозерн"),
        food("bread white", "бел хлеб", "хлеб бел", "батон"),
        food("bread", "хлеб"),
        food("wheat flour", "мука", "муки", "муку", "мукой"),
        food("bulgur", "булгур"),
        food("quinoa", "киноа"),
        food("couscous", "кускус"),
        food("millet", "пшен"),
        food("barley pearled", "перлов", "ячнев", "ячмен"),
        food("semolina", "манк", "манн"),
        food("corn flakes", "кукуруз хлоп", "хлоп кукуруз"),
        food("granola", "гранол", "мюсли"),
        food("corn", "кукуруз"),
        // Бобовые, орехи, семена
        food("lentils", "чечевиц"),
        food("chickpeas", "нут", "нута", "нутом"),
        food("green beans", "стручк фасол", "фасол стручк"),
        food("beans", "фасол"),
        food("green peas", "зелен горош", "горош зелен"),
        food("peas", "горох", "горош"),
        food("soybeans", "соев бобы", "соя", "сои", "сою"),
        food("tofu", "тофу"),
        food("peanut butter", "арахис паст", "паста арахис"),
        food("peanuts", "арахис"),
        food("almonds", "миндал"),
        food("walnuts", "грецк орех", "орех грецк", "грецк"),
        food("hazelnuts", "фундук", "лесн орех"),
        food("cashew nuts", "кешью", "кеш"),
        food("pistachio nuts", "фисташк"),
        food("sunflower seeds", "семечк", "подсолнечн семен", "семен подсолнечн"),
        food("pumpkin seeds", "тыкв семечк", "тыкв семен", "семен тыкв"),
        food("chia seeds", "чиа"),
        food("flaxseed", "льнян семен", "семен льна", "семена льна", "лен"),
        food("nuts", "орех"),
        // Овощи и зелень
        food("sweet potato", "батат"),
        food("potatoes", "картоф", "картош"),
        food("carrots", "морков"),
        food("green onions", "зелен лук", "лук зелен"),
        food("onions", "лук", "репчат"),
        food("garlic", "чеснок", "чесноч"),
        food("tomatoes", "помидор", "томат"),
        food("cucumber", "огур"),
        food("cauliflower", "цветн капуст", "капуст цветн"),
        food("cabbage", "капуст"),
        food("broccoli", "брокол"),
        food("zucchini", "кабач", "цукин"),
        food("eggplant", "баклажан"),
        food("peppers sweet", "болгар перец", "перец болгар", "сладк перец", "болгарск"),
        food("peppers sweet", "перец", "перца", "перцем"),
        food("beets", "свекл"),
        food("pumpkin", "тыкв"),
        food("spinach", "шпинат"),
        food("lettuce", "лист салат", "салат лист", "айсберг", "латук"),
        food("mushrooms", "гриб", "шампиньон"),
        food("radishes", "редис"),
        food("celery", "сельдер"),
        food("avocados", "авокад"),
        food("olives", "оливк", "маслин"),
        food("dill", "укроп"),
        food("parsley", "петрушк"),
        food("asparagus", "спарж"),
        // Фрукты и ягоды
        food("apples", "яблок", "яблоч"),
        food("bananas", "банан"),
        food("oranges", "апельсин"),
        food("tangerines", "мандарин"),
        food("lemons", "лимон"),
        food("grapefruit", "грейпфрут"),
        food("pears", "груш"),
        food("peaches", "персик"),
        food("apricots dried", "кураг"),
        food("apricots", "абрикос"),
        food("plums", "слив"),
        food("grapes", "виноград"),
        food("watermelon", "арбуз"),
        food("melons", "дын"),
        food("strawberries", "клубник", "земляник"),
        food("raspberries", "малин"),
        food("blueberries", "черник", "голубик"),
        food("cherries", "вишн", "черешн"),
        food("kiwifruit", "киви"),
        food("pineapple", "ананас"),
        food("mangos", "манго"),
        food("pomegranates", "гранат"),
        food("persimmons", "хурм"),
        food("dates", "финик"),
        food("raisins", "изюм"),
        food("plums dried", "черносл"),
        food("cranberries", "клюкв"),
        food("currants", "смородин"),
        food("figs", "инжир"),
        // Жиры, соусы
        food("olive oil", "оливк масл", "оливков масл", "масл оливк", "масло оливк"),
        food("sunflower oil", "подсолнечн масл", "растительн масл", "масл подсолнечн"),
        food("coconut oil", "кокос масл"),
        food("mayonnaise", "майонез"),
        food("ketchup", "кетчуп"),
        food("mustard", "горчиц"),
        food("soy sauce", "соев соус"),
        food("hummus", "хумус"),
        food("salt table", "соль", "соли"),
        // Сладкое и прочее
        food("sugar", "сахар"),
        food("honey", "мед", "меда", "медом"),
        food("chocolate dark", "горьк шоколад", "черн шоколад", "шоколад горьк"),
        food("milk chocolate", "молочн шоколад", "шоколад молочн"),
        food("chocolate", "шоколад"),
        food("cookies", "печенье", "печенья", "печеньк"),
        food("jams and preserves", "варень", "джем", "конфитюр"),
        food("ice cream", "морожен"),
        food("cake", "торт"),
        food("candies", "конфет"),
        food("marshmallows", "зефир"),
        food("potato chips", "чипс"),
        food("popcorn", "попкорн"),
        food("pizza", "пицц"),
        food("hamburger", "бургер", "гамбургер"),
        food("dumplings", "пельмен", "вареник"),
        food("pancakes", "блин", "блины", "оладь", "оладий"),
        food("borscht", "борщ"),
        food("soup", "суп"),
        // Напитки
        food("alcoholic beverage vodka", "водк"),
        food("water", "вода", "воды", "воду", "водой"),
        food("coffee", "кофе"),
        food("tea", "чай", "чая", "чаю", "чаем"),
        food("orange juice", "апельсин сок", "сок апельсин"),
        food("apple juice", "яблоч сок", "сок яблоч"),
        food("juice", "сок", "сока", "соком"),
        food("cola", "кока", "кола", "колы"),
        food("beer", "пив"),
        food("wine", "вино", "вина", "вином"),
        food("cocoa", "какао"),
        food("protein powder whey", "протеин", "сывороточ"),
        // Способы приготовления и состояние
        mod("boiled", "варен", "отварн", "отвар"),
        mod("fried", "жарен"),
        mod("baked", "запечен"),
        mod("stewed", "тушен"),
        mod("grilled", "гриль"),
        mod("raw", "сырой", "сырая", "сырое", "сырые", "сырых", "сырую"),
        mod("dried", "сушен", "вялен"),
        mod("canned", "консерв"),
        mod("smoked", "копчен"),
        mod("nonfat", "обезжир"),
        mod("low fat", "нежир", "маложир"),
        mod("whole", "цельн"),
    )

    private fun rootMatches(root: String, token: String): Boolean {
        if (!token.startsWith(root)) return false
        // Короткие корни («лук», «рис», «сыр») допускают только окончания, а не любые слова.
        return root.length >= 4 || token.length <= root.length + 2
    }

    private data class Match(val entry: Entry, val tokenIndexes: List<Int>, val weight: Int)

    fun translate(query: String): Result {
        val tokens = TextNormalizer.tokens(query)
        if (tokens.isEmpty()) return Result(emptyList(), emptyList(), emptyList())
        if (tokens.all { !TextNormalizer.isCyrillic(it) }) {
            // Запрос уже латиницей — передаём как есть.
            return Result(listOf(tokens.joinToString(" ")), tokens, emptyList())
        }

        val candidates = mutableListOf<Match>()
        for (entry in entries) {
            for (alt in entry.alternatives) {
                val used = mutableListOf<Int>()
                var ok = true
                for (root in alt) {
                    val idx = tokens.indices.firstOrNull { it !in used && rootMatches(root, tokens[it]) }
                    if (idx == null) { ok = false; break }
                    used += idx
                }
                if (ok) candidates += Match(entry, used, alt.sumOf { it.length } + (alt.size - 1) * 10)
            }
        }
        // Жадно берём самые специфичные совпадения, не используя слово дважды.
        val taken = mutableSetOf<Int>()
        val chosen = mutableListOf<Match>()
        for (m in candidates.sortedByDescending { it.weight }) {
            if (m.tokenIndexes.any { it in taken }) continue
            chosen += m
            taken += m.tokenIndexes
        }
        val foods = chosen.filter { !it.entry.isModifier }.sortedBy { it.tokenIndexes.min() }
        val mods = chosen.filter { it.entry.isModifier }.sortedBy { it.tokenIndexes.min() }
        if (foods.isEmpty()) {
            return Result(emptyList(), emptyList(), tokens)
        }
        val main = (listOf(foods.first().entry.english) + mods.map { it.entry.english }).distinct().joinToString(" ")
        val others = foods.drop(1).map { it.entry.english }
        return Result(
            englishQueries = (listOf(main) + others).distinct(),
            translatedTokens = taken.sorted().map { tokens[it] },
            untranslatedTokens = tokens.indices.filter { it !in taken }.map { tokens[it] },
        )
    }
}
