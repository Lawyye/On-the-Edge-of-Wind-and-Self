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
        /** Русское название основного продукта в начальной форме: «творога» → «Творог». */
        val russianName: String? = null,
        /**
         * Слово допускает разные продукты («масло» — сливочное или растительное):
         * варианты для уточнения. Пусто — запрос однозначен.
         */
        val clarifications: List<String> = emptyList(),
        /** Вопрос к вариантам [clarifications]. */
        val clarifyQuestion: String? = null,
        /**
         * Ближайший продукт USDA заметно отличается от привычного русского
         * («творог» и американский cottage cheese: 11 г белка против 16–18 г).
         * Тогда при автоматическом выборе предпочтительнее русские товары.
         */
        val usdaApproximate: Boolean = false,
    ) {
        val englishQuery: String? get() = englishQueries.firstOrNull()
    }

    private class Entry(
        val english: String,
        val alternatives: List<List<String>>,
        val isModifier: Boolean,
        val ru: String? = null,
        val usdaApproximate: Boolean = false,
    )

    private fun food(en: String, vararg alts: String, ru: String? = null, approx: Boolean = false) =
        Entry(en, alts.map { it.split(' ') }, false, ru, approx)
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
        food("cottage cheese", "творог", "творож", approx = true),
        food("butter", "сливоч масл", "масл сливоч", "масло сливоч"),
        food("cream", "сливк", "сливок"),
        food("sour cream", "сметан"),
        food("mozzarella cheese", "моцарел"),
        food("parmesan cheese", "пармез"),
        food("feta cheese", "фета", "фетой", "брынз"),
        food("cheese", "сыр", "сырок", "сырн"),
        // Крупы, хлеб, макароны
        // «Рис» в России — почти всегда белый.
        food("rice white", "рис", "рисов", ru = "Рис"),
        food("buckwheat groats", "греч"),
        food("oats", "овсян", "овес", "овса", "геркулес"),
        // В USDA спагетти — это «Pasta» (сухие: «Pasta, dry», готовые: «Pasta, cooked»).
        food("pasta", "спагет", ru = "Спагетти"),
        food("pasta", "макарон", "паста", "пасты", "пасту"),
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
        // В названиях USDA готовый продукт — «cooked», сухой — «dry» или «raw».
        mod("cooked", "варен", "отварн", "отвар", "готов"),
        mod("dry", "сух"),
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

    /** Русские названия продуктов словаря в начальной форме. */
    private val russianNames: Map<String, String> = mapOf(
        "chicken breast" to "Куриное филе",
        "chicken thigh" to "Куриное бедро",
        "chicken drumstick" to "Куриная голень",
        "chicken wing" to "Куриные крылья",
        "chicken liver" to "Куриная печень",
        "chicken" to "Курица",
        "turkey breast" to "Филе индейки",
        "turkey" to "Индейка",
        "ground beef" to "Говяжий фарш",
        "beef liver" to "Говяжья печень",
        "beef" to "Говядина",
        "ground pork" to "Свиной фарш",
        "pork" to "Свинина",
        "lamb" to "Баранина",
        "veal" to "Телятина",
        "rabbit" to "Кролик",
        "duck" to "Утка",
        "bacon" to "Бекон",
        "ham" to "Ветчина",
        "frankfurter" to "Сосиски",
        "sausage" to "Колбаса",
        "salami" to "Салями",
        "pink salmon" to "Горбуша",
        "salmon" to "Лосось",
        "tuna" to "Тунец",
        "cod" to "Треска",
        "pollock" to "Минтай",
        "herring" to "Сельдь",
        "mackerel" to "Скумбрия",
        "trout" to "Форель",
        "hake" to "Хек",
        "carp" to "Карп",
        "shrimp" to "Креветки",
        "squid" to "Кальмар",
        "crab" to "Краб",
        "fish" to "Рыба",
        "egg white" to "Яичный белок",
        "egg yolk" to "Яичный желток",
        "egg fried" to "Яичница",
        "egg whole" to "Яйцо",
        "milk" to "Молоко",
        "kefir" to "Кефир",
        "yogurt" to "Йогурт",
        "cottage cheese" to "Творог",
        "butter" to "Сливочное масло",
        "cream" to "Сливки",
        "sour cream" to "Сметана",
        "mozzarella cheese" to "Моцарелла",
        "parmesan cheese" to "Пармезан",
        "feta cheese" to "Фета",
        "cheese" to "Сыр",
        "buckwheat groats" to "Гречка",
        "oats" to "Овсянка",
        "pasta" to "Макароны",
        "noodles" to "Лапша",
        "bread rye" to "Ржаной хлеб",
        "bread whole wheat" to "Цельнозерновой хлеб",
        "bread white" to "Белый хлеб",
        "bread" to "Хлеб",
        "wheat flour" to "Пшеничная мука",
        "bulgur" to "Булгур",
        "quinoa" to "Киноа",
        "couscous" to "Кускус",
        "millet" to "Пшено",
        "barley pearled" to "Перловка",
        "semolina" to "Манная крупа",
        "corn flakes" to "Кукурузные хлопья",
        "granola" to "Гранола",
        "corn" to "Кукуруза",
        "lentils" to "Чечевица",
        "chickpeas" to "Нут",
        "green beans" to "Стручковая фасоль",
        "beans" to "Фасоль",
        "green peas" to "Зелёный горошек",
        "peas" to "Горох",
        "soybeans" to "Соя",
        "tofu" to "Тофу",
        "peanut butter" to "Арахисовая паста",
        "peanuts" to "Арахис",
        "almonds" to "Миндаль",
        "walnuts" to "Грецкие орехи",
        "hazelnuts" to "Фундук",
        "cashew nuts" to "Кешью",
        "pistachio nuts" to "Фисташки",
        "sunflower seeds" to "Семечки подсолнечника",
        "pumpkin seeds" to "Тыквенные семечки",
        "chia seeds" to "Семена чиа",
        "flaxseed" to "Семена льна",
        "nuts" to "Орехи",
        "sweet potato" to "Батат",
        "potatoes" to "Картофель",
        "carrots" to "Морковь",
        "green onions" to "Зелёный лук",
        "onions" to "Лук",
        "garlic" to "Чеснок",
        "tomatoes" to "Помидоры",
        "cucumber" to "Огурец",
        "cauliflower" to "Цветная капуста",
        "cabbage" to "Капуста",
        "broccoli" to "Брокколи",
        "zucchini" to "Кабачок",
        "eggplant" to "Баклажан",
        "peppers sweet" to "Сладкий перец",
        "beets" to "Свёкла",
        "pumpkin" to "Тыква",
        "spinach" to "Шпинат",
        "lettuce" to "Листовой салат",
        "mushrooms" to "Грибы",
        "radishes" to "Редис",
        "celery" to "Сельдерей",
        "avocados" to "Авокадо",
        "olives" to "Оливки",
        "dill" to "Укроп",
        "parsley" to "Петрушка",
        "asparagus" to "Спаржа",
        "apples" to "Яблоко",
        "bananas" to "Банан",
        "oranges" to "Апельсин",
        "tangerines" to "Мандарин",
        "lemons" to "Лимон",
        "grapefruit" to "Грейпфрут",
        "pears" to "Груша",
        "peaches" to "Персик",
        "apricots dried" to "Курага",
        "apricots" to "Абрикос",
        "plums" to "Слива",
        "grapes" to "Виноград",
        "watermelon" to "Арбуз",
        "melons" to "Дыня",
        "strawberries" to "Клубника",
        "raspberries" to "Малина",
        "blueberries" to "Черника",
        "cherries" to "Вишня",
        "kiwifruit" to "Киви",
        "pineapple" to "Ананас",
        "mangos" to "Манго",
        "pomegranates" to "Гранат",
        "persimmons" to "Хурма",
        "dates" to "Финики",
        "raisins" to "Изюм",
        "plums dried" to "Чернослив",
        "cranberries" to "Клюква",
        "currants" to "Смородина",
        "figs" to "Инжир",
        "olive oil" to "Оливковое масло",
        "sunflower oil" to "Подсолнечное масло",
        "coconut oil" to "Кокосовое масло",
        "mayonnaise" to "Майонез",
        "ketchup" to "Кетчуп",
        "mustard" to "Горчица",
        "soy sauce" to "Соевый соус",
        "hummus" to "Хумус",
        "salt table" to "Соль",
        "sugar" to "Сахар",
        "honey" to "Мёд",
        "chocolate dark" to "Горький шоколад",
        "milk chocolate" to "Молочный шоколад",
        "chocolate" to "Шоколад",
        "cookies" to "Печенье",
        "jams and preserves" to "Варенье",
        "ice cream" to "Мороженое",
        "cake" to "Торт",
        "candies" to "Конфеты",
        "marshmallows" to "Зефир",
        "potato chips" to "Чипсы",
        "popcorn" to "Попкорн",
        "pizza" to "Пицца",
        "hamburger" to "Гамбургер",
        "dumplings" to "Пельмени",
        "pancakes" to "Блины",
        "borscht" to "Борщ",
        "soup" to "Суп",
        "alcoholic beverage vodka" to "Водка",
        "water" to "Вода",
        "coffee" to "Кофе",
        "tea" to "Чай",
        "orange juice" to "Апельсиновый сок",
        "apple juice" to "Яблочный сок",
        "juice" to "Сок",
        "cola" to "Кола",
        "beer" to "Пиво",
        "wine" to "Вино",
        "cocoa" to "Какао",
        "protein powder whey" to "Сывороточный протеин",
    )

    /**
     * Слова, за которыми стоят разные продукты. Если в запросе нет уточнения
     * («сливочное», «оливковое»), продукт выбирает пользователь.
     */
    private val ambiguous: List<Pair<String, List<String>>> = listOf(
        "масл" to listOf("Сливочное масло", "Подсолнечное масло", "Оливковое масло"),
    )

    fun russianName(english: String): String? = russianNames[english]

    /**
     * Продукты, которые взвешивают и сухими, и готовыми: без уточнения не
     * понятно, какой вес назван. Значение — род для формы прилагательного.
     */
    private val cookable: Map<String, Char> = mapOf(
        "rice white" to 'm', "buckwheat groats" to 'f', "oats" to 'f', "pasta" to 'p', "noodles" to 'f', "bulgur" to 'm',
        "quinoa" to 'f', "couscous" to 'm', "millet" to 'n', "barley pearled" to 'f', "semolina" to 'f',
        "lentils" to 'f', "chickpeas" to 'm', "beans" to 'f', "peas" to 'm',
    )
    private val stateModifiers = setOf("cooked", "dry", "raw", "fried", "baked", "stewed", "grilled", "canned", "smoked")

    private fun stateOptions(ru: String, gender: Char): List<String> = when (gender) {
        'f' -> listOf("$ru сухая", "$ru варёная")
        'n' -> listOf("$ru сухое", "$ru варёное")
        'p' -> listOf("$ru сухие", "$ru варёные")
        else -> listOf("$ru сухой", "$ru варёный")
    }

    private fun rootMatches(root: String, token: String): Boolean {
        // Удвоенные буквы схлопываются с обеих сторон: «куринного», «пица», «моцарелла».
        val r = TextNormalizer.squeeze(root)
        val t = TextNormalizer.squeeze(token)
        if (!t.startsWith(r)) return false
        // Короткие корни («лук», «рис», «сыр») допускают только окончания, а не любые слова.
        return r.length >= 4 || t.length <= r.length + 2
    }

    private data class Match(val entry: Entry, val tokenIndexes: List<Int>, val weight: Int)

    /** Жадно берёт самые специфичные совпадения словаря, не используя слово дважды. */
    private fun chooseMatches(tokens: List<String>): List<Match> {
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
        val taken = mutableSetOf<Int>()
        val chosen = mutableListOf<Match>()
        for (m in candidates.sortedByDescending { it.weight }) {
            if (m.tokenIndexes.any { it in taken }) continue
            chosen += m
            taken += m.tokenIndexes
        }
        return chosen
    }

    /**
     * Позиции, с которых в списке слов начинаются разные продукты словаря
     * (включая неоднозначные вроде «масла»): «масла творог» → [0, 1],
     * «куриного филе» → [0]. Нужно разбору фразы, чтобы разделить два
     * продукта, названных подряд.
     */
    fun foodStarts(words: List<String>): List<Int> {
        val tokens = words.map { TextNormalizer.normalize(it) }
        val chosen = chooseMatches(tokens)
        val taken = chosen.flatMap { it.tokenIndexes }.toSet()
        val starts = chosen.filter { !it.entry.isModifier }.map { it.tokenIndexes.min() } +
            tokens.indices.filter { i -> i !in taken && ambiguous.any { (root, _) -> rootMatches(root, tokens[i]) } }
        return starts.distinct().sorted()
    }

    fun translate(query: String): Result {
        val tokens = TextNormalizer.tokens(query)
        if (tokens.isEmpty()) return Result(emptyList(), emptyList(), emptyList())
        if (tokens.all { !TextNormalizer.isCyrillic(it) }) {
            // Запрос уже латиницей — передаём как есть.
            return Result(listOf(tokens.joinToString(" ")), tokens, emptyList())
        }

        val chosen = chooseMatches(tokens)
        val taken = chosen.flatMap { it.tokenIndexes }.toSet()
        val foods = chosen.filter { !it.entry.isModifier }.sortedBy { it.tokenIndexes.min() }
        val mods = chosen.filter { it.entry.isModifier }.sortedBy { it.tokenIndexes.min() }
        if (foods.isEmpty()) {
            val options = ambiguous.firstOrNull { (root, _) -> tokens.any { rootMatches(root, it) } }?.second.orEmpty()
            return Result(
                emptyList(), emptyList(), tokens,
                clarifications = options,
                clarifyQuestion = if (options.isEmpty()) null else "Какое именно? Калорийность разная",
            )
        }
        val mainEntry = foods.first().entry
        val mainRu = mainEntry.ru ?: russianNames[mainEntry.english]
        val gender = cookable[mainEntry.english]
        val stateChoice = if (gender != null && mainRu != null && mods.none { it.entry.english in stateModifiers }) stateOptions(mainRu, gender) else emptyList()
        val main = (listOf(foods.first().entry.english) + mods.map { it.entry.english }).distinct().joinToString(" ")
        val others = foods.drop(1).map { it.entry.english }
        return Result(
            englishQueries = (listOf(main) + others).distinct(),
            translatedTokens = taken.sorted().map { tokens[it] },
            untranslatedTokens = tokens.indices.filter { it !in taken }.map { tokens[it] },
            russianName = mainRu,
            usdaApproximate = mainEntry.usdaApproximate,
            clarifications = stateChoice,
            clarifyQuestion = if (stateChoice.isEmpty()) null else "Вес сухого продукта или готового? Калорийность отличается в 2–3 раза",
        )
    }
}
