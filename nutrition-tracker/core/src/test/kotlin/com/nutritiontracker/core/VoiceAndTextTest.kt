package com.nutritiontracker.core

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.MealType
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.core.text.FoodQueryTranslator
import com.nutritiontracker.core.text.FoodSearch
import com.nutritiontracker.core.text.RussianStemmer
import com.nutritiontracker.core.voice.CountKind
import com.nutritiontracker.core.voice.ParsedQuantity
import com.nutritiontracker.core.voice.QuantityResolver
import com.nutritiontracker.core.voice.ResolvedAmount
import com.nutritiontracker.core.voice.RussianNumbers
import com.nutritiontracker.core.voice.VoiceInputParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceAndTextTest {
    private val eps = 1e-9

    @Test
    fun `пример из ТЗ разбирается на три продукта`() {
        val r = VoiceInputParser.parse("Сегодня я съел 200 граммов куриного филе, три картофелины по 100 граммов и одно яблоко")
        assertEquals(0, r.dayOffset)
        assertEquals(3, r.items.size)
        val (chicken, potato, apple) = r.items
        assertEquals("куриного филе", chicken.name)
        assertEquals(ParsedQuantity.Mass(200.0), chicken.quantity)
        assertEquals("картофелины", potato.name)
        assertEquals(ParsedQuantity.Count(3.0, CountKind.PIECE, eachGrams = 100.0), potato.quantity)
        assertEquals("яблоко", apple.name)
        assertEquals(ParsedQuantity.Count(1.0, CountKind.PIECE), apple.quantity)
    }

    @Test
    fun `числа словами`() {
        fun n(s: String) = RussianNumbers.parse(s.split(' '), 0)?.value
        assertEquals(250.0, n("двести пятьдесят")!!, eps)
        assertEquals(125.0, n("сто двадцать пять")!!, eps)
        assertEquals(1.5, n("полтора")!!, eps)
        assertEquals(2.5, n("два с половиной")!!, eps)
        assertEquals(2000.0, n("две тысячи")!!, eps)
        assertEquals(1200.0, n("тысяча двести")!!, eps)
        assertEquals(15.0, n("пятнадцать")!!, eps)
        assertEquals(3.0, n("три четыре")!!, eps) // это два разных числа
        assertNull(n("курица"))
    }

    @Test
    fun `единицы массы, объёма, штук и порций`() {
        val r = VoiceInputParser.parse("полтора литра воды, 1,5 кг картошки, полкило творога, 2 порции супа, 500 мл молока, двести грамм гречки")
        val q = r.items.map { it.quantity }
        assertEquals(ParsedQuantity.Volume(1500.0), q[0])
        assertEquals(ParsedQuantity.Mass(1500.0), q[1])
        assertEquals(ParsedQuantity.Mass(500.0), q[2])
        assertEquals(ParsedQuantity.Count(2.0, CountKind.PORTION, "порции"), q[3])
        assertEquals(ParsedQuantity.Volume(500.0), q[4])
        assertEquals(ParsedQuantity.Mass(200.0), q[5])
        assertEquals("гречки", r.items[5].name)
    }

    @Test
    fun `количество после названия и без запятых`() {
        val r = VoiceInputParser.parse("курица 200 г рис 150 г банан 2")
        assertEquals(listOf("курица", "рис", "банан"), r.items.map { it.name })
        assertEquals(ParsedQuantity.Mass(200.0), r.items[0].quantity)
        assertEquals(ParsedQuantity.Mass(150.0), r.items[1].quantity)
        assertEquals(ParsedQuantity.Count(2.0, CountKind.PIECE), r.items[2].quantity)
    }

    @Test
    fun `если количество не прозвучало, оно не придумывается`() {
        val r = VoiceInputParser.parse("яблоко и кофе с молоком")
        assertEquals(2, r.items.size)
        assertNull(r.items[0].quantity)
        assertEquals("кофе с молоком", r.items[1].name)
        assertNull(r.items[1].quantity)
        assertTrue(r.items[0].notes.isNotEmpty())
    }

    @Test
    fun `диапазон не превращается в число`() {
        val r = VoiceInputParser.parse("200-300 г гречки")
        assertEquals(1, r.items.size)
        assertNull(r.items[0].quantity)
        assertEquals("гречки", r.items[0].name)
        assertTrue(r.items[0].notes.single().contains("200–300"))
    }

    @Test
    fun `приём пищи и день распознаются`() {
        val r = VoiceInputParser.parse("вчера на ужин съела 150 г лосося")
        assertEquals(MealType.DINNER, r.mealHint)
        assertEquals(-1, r.dayOffset)
        assertEquals("лосося", r.items.single().name)
    }

    @Test
    fun `бытовые меры и сокращения с точкой`() {
        val r = VoiceInputParser.parse("стакан кефира, две столовые ложки масла, 100 г. сыра")
        assertEquals(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "стакан"), r.items[0].quantity)
        assertEquals(ParsedQuantity.Count(2.0, CountKind.HOUSEHOLD, "столовые ложки"), r.items[1].quantity)
        assertEquals(ParsedQuantity.Mass(100.0), r.items[2].quantity)
        assertEquals("сыра", r.items[2].name)
    }

    @Test
    fun `разделение по «с» только перед количеством`() {
        val r = VoiceInputParser.parse("гречка 200 г с 10 г масла")
        assertEquals(listOf("гречка", "масла"), r.items.map { it.name })
        assertEquals(ParsedQuantity.Mass(10.0), r.items[1].quantity)
    }

    private val apple = listOf(
        ServingSize("1 medium (3\" dia)", 182.0, ServingKind.PIECE, "USDA"),
        ServingSize("NLEA serving", 242.0, ServingKind.PORTION, "USDA"),
    )

    @Test
    fun `штуки пересчитываются по справочному весу и с пояснением`() {
        val r = QuantityResolver.resolve(ParsedQuantity.Count(2.0, CountKind.PIECE), AmountUnit.GRAM, apple)
        r as ResolvedAmount.Resolved
        assertEquals(364.0, r.amount, eps)
        assertTrue(r.usesReferenceWeight)
        assertTrue(r.explanation!!.contains("182"))
        assertTrue(r.explanation!!.contains("USDA"))
    }

    @Test
    fun `названный вес штуки важнее справочного`() {
        val r = QuantityResolver.resolve(ParsedQuantity.Count(3.0, CountKind.PIECE, eachGrams = 100.0), AmountUnit.GRAM, apple)
        r as ResolvedAmount.Resolved
        assertEquals(300.0, r.amount, eps)
        assertFalse(r.usesReferenceWeight)
    }

    @Test
    fun `без справочного веса количество запрашивается`() {
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.PIECE), AmountUnit.GRAM, emptyList()) is ResolvedAmount.NeedsInput)
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "стакан"), AmountUnit.GRAM, apple) is ResolvedAmount.NeedsInput)
        assertTrue(QuantityResolver.resolve(null, AmountUnit.GRAM, apple) is ResolvedAmount.NeedsInput)
    }

    @Test
    fun `граммы и миллилитры не подменяют друг друга`() {
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Volume(1000.0), AmountUnit.GRAM, emptyList()) is ResolvedAmount.NeedsInput)
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Mass(200.0), AmountUnit.MILLILITER, emptyList()) is ResolvedAmount.NeedsInput)
        val ml = QuantityResolver.resolve(ParsedQuantity.Volume(250.0), AmountUnit.MILLILITER, emptyList()) as ResolvedAmount.Resolved
        assertEquals(250.0, ml.amount, eps)
    }

    @Test
    fun `стеммер сводит формы слова к общей основе`() {
        assertEquals("вагон", RussianStemmer.stem("вагонов"))
        assertEquals("важн", RussianStemmer.stem("важная"))
        assertEquals("яблок", RussianStemmer.stem("яблоки"))
        assertEquals("яблок", RussianStemmer.stem("яблоко"))
        assertEquals(RussianStemmer.stem("куриное"), RussianStemmer.stem("куриного"))
        assertEquals(RussianStemmer.stem("гречка"), RussianStemmer.stem("гречки"))
        assertEquals(RussianStemmer.stem("творог"), RussianStemmer.stem("творога"))
        assertEquals("ход", RussianStemmer.stem("ходивший"))
        assertEquals("красив", RussianStemmer.stem("красивейший"))
        assertEquals("картофел", RussianStemmer.stem("картофель"))
    }

    @Test
    fun `поиск находит продукт по другой форме слова`() {
        val index = FoodSearch.buildIndex("Куриное филе отварное", "Chicken breast boiled", "Мой продукт")
        assertTrue(FoodSearch.matches(index, FoodSearch.queryStems("куриного филе")))
        assertTrue(FoodSearch.matches(index, FoodSearch.queryStems("филе")))
        assertTrue(FoodSearch.matches(index, FoodSearch.queryStems("chicken")))
        assertFalse(FoodSearch.matches(index, FoodSearch.queryStems("говядина")))
        val potato = FoodSearch.buildIndex("Картофель отварной")
        assertTrue(FoodSearch.matches(potato, FoodSearch.queryStems("картофелины")))
    }

    @Test
    fun `русский запрос переводится для USDA`() {
        assertEquals("chicken breast", FoodQueryTranslator.translate("куриного филе").englishQuery)
        assertEquals("chicken breast boiled", FoodQueryTranslator.translate("Куриное филе отварное").englishQuery)
        assertEquals("potatoes", FoodQueryTranslator.translate("картофелины").englishQuery)
        assertEquals("apples", FoodQueryTranslator.translate("яблоко").englishQuery)
        assertEquals("cream", FoodQueryTranslator.translate("сливки").englishQuery)
        assertEquals("plums", FoodQueryTranslator.translate("слива").englishQuery)
        assertEquals("cookies", FoodQueryTranslator.translate("печенье").englishQuery)
        assertEquals("beef liver", FoodQueryTranslator.translate("печень").englishQuery)
        assertEquals("cheese", FoodQueryTranslator.translate("сыр").englishQuery)
        assertEquals("carrots raw", FoodQueryTranslator.translate("сырая морковь").englishQuery)
        assertEquals("butter", FoodQueryTranslator.translate("сливочное масло").englishQuery)
        assertEquals("oats", FoodQueryTranslator.translate("овсянка").englishQuery)
        assertNull(FoodQueryTranslator.translate("кнедлики").englishQuery)
        assertEquals("tea", FoodQueryTranslator.translate("чай").englishQuery)
        assertNull(FoodQueryTranslator.translate("чайная").englishQuery)
        assertEquals("greek yogurt", FoodQueryTranslator.translate("greek yogurt").englishQuery)
    }

    @Test
    fun `опечатки и удвоенные буквы не мешают поиску`() {
        val index = FoodSearch.buildIndex("Куриное филе отварное")
        assertTrue(FoodSearch.matches(index, FoodSearch.queryStems("куринного филе")))
        assertTrue(FoodSearch.matches(index, FoodSearch.queryStems("куринное филле")))
        // Короткие основы без нечёткого сравнения: «сливки» не находят «сливы».
        assertFalse(FoodSearch.matches(FoodSearch.buildIndex("Сливы свежие"), FoodSearch.queryStems("сливки")))
        // Одна опечатка в длинном слове.
        assertTrue(FoodSearch.matches(FoodSearch.buildIndex("Картофель отварной"), FoodSearch.queryStems("картофль")))
        assertEquals(1, FoodSearch.osaDistance("курни", "курин"))
    }

    @Test
    fun `филе выше, чем ветчина из филе, а сырое филе USDA проходит по переводу`() {
        val q = "куриного филе"
        val fillet = FoodSearch.relevance(q, "Филе куриное охлаждённое", "Петелинка")
        val ham = FoodSearch.relevance(q, "Ветчина из куриного филе", "Мясницкий ряд")
        val roll = FoodSearch.relevance(q, "Рулет куриный из филе с паприкой")
        assertTrue("$fillet vs $ham", fillet > ham + 2)
        assertTrue("$fillet vs $roll", fillet > roll + 2)
        val usda = FoodSearch.relevanceUsda("chicken breast", "Chicken, breast, boneless, skinless, raw", "Poultry Products")
        assertTrue("$usda vs $fillet", usda >= fillet)
    }

    @Test
    fun `релевантный продукт выше нерелевантного`() {
        val good = FoodSearch.relevance("куринного филе", "Филе куриное охлаждённое", "Петелинка")
        val bad = FoodSearch.relevance("куринного филе", "Картофель молодой", "Агрокомплекс")
        assertTrue(good > 0)
        assertEquals(0.0, bad, 0.0)
    }

    @Test
    fun `перевод для USDA понимает опечатки`() {
        assertEquals("chicken breast", FoodQueryTranslator.translate("куринного филе").englishQuery)
        assertEquals("pizza", FoodQueryTranslator.translate("пица").englishQuery)
    }

    @Test
    fun `способ приготовления после количества не создаёт новую позицию`() {
        val r = VoiceInputParser.parse("курица 200 г отварная, рис 150 г на пару")
        assertEquals(listOf("курица отварная", "рис на пару"), r.items.map { it.name })
        assertEquals(ParsedQuantity.Mass(150.0), r.items[1].quantity)
    }

    @Test
    fun `приём пищи через двоеточие`() {
        val r = VoiceInputParser.parse("завтрак: овсянка 50 г, кофе 200 мл")
        assertEquals(MealType.BREAKFAST, r.mealHint)
        assertEquals(listOf("овсянка", "кофе"), r.items.map { it.name })
    }

    @Test
    fun `стакан и ложки сопоставляются со справочными порциями USDA`() {
        val servings = listOf(
            ServingSize("1 cup", 245.0, ServingKind.HOUSEHOLD, "USDA"),
            ServingSize("1 tbsp", 15.3, ServingKind.HOUSEHOLD, "USDA"),
        )
        val glass = QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "стакан"), AmountUnit.GRAM, servings) as ResolvedAmount.Resolved
        assertEquals(245.0, glass.amount, eps)
        assertTrue(glass.explanation!!.contains("240 мл"))
        val spoons = QuantityResolver.resolve(ParsedQuantity.Count(2.0, CountKind.HOUSEHOLD, "столовые ложки"), AmountUnit.GRAM, servings) as ResolvedAmount.Resolved
        assertEquals(30.6, spoons.amount, eps)
        // «Ложка» без уточнения неоднозначна.
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "ложка"), AmountUnit.GRAM, servings) is ResolvedAmount.NeedsInput)
        // Полстакана.
        val half = VoiceInputParser.parse("полстакана кефира").items.single().quantity
        val r = QuantityResolver.resolve(half, AmountUnit.GRAM, servings) as ResolvedAmount.Resolved
        assertEquals(122.5, r.amount, eps)
    }

    @Test
    fun `единицы распознаются в любом падеже`() {
        val r = VoiceInputParser.parse("110 г спагетти, 180 г творога, на двух чайных ложках масла")
        assertEquals(listOf("спагетти", "творога", "масла"), r.items.map { it.name })
        assertEquals(ParsedQuantity.Mass(110.0), r.items[0].quantity)
        assertEquals(ParsedQuantity.Mass(180.0), r.items[1].quantity)
        assertEquals(ParsedQuantity.Count(2.0, CountKind.HOUSEHOLD, "чайных ложках"), r.items[2].quantity)

        val more = VoiceInputParser.parse("три чашек чая, 2 кусками хлеба, 300 граммами риса, 2 стаканами молока, 1,5 литрами воды")
        assertEquals(listOf("чая", "хлеба", "риса", "молока", "воды"), more.items.map { it.name })
        assertEquals(CountKind.HOUSEHOLD, (more.items[0].quantity as ParsedQuantity.Count).kind)
        assertEquals(CountKind.PIECE, (more.items[1].quantity as ParsedQuantity.Count).kind)
        assertEquals(ParsedQuantity.Mass(300.0), more.items[2].quantity)
        assertEquals(CountKind.HOUSEHOLD, (more.items[3].quantity as ParsedQuantity.Count).kind)
        assertEquals(ParsedQuantity.Volume(1500.0), more.items[4].quantity)
    }

    @Test
    fun `слова похожие на единицы не принимаются за них`() {
        val r = VoiceInputParser.parse("2 банана, 100 г кускуса, 1 банка тунца")
        assertEquals(listOf("банана", "кускуса", "тунца"), r.items.map { it.name })
        assertEquals(ParsedQuantity.Count(2.0, CountKind.PIECE), r.items[0].quantity)
        assertEquals(ParsedQuantity.Mass(100.0), r.items[1].quantity)
        assertEquals(CountKind.PACKAGE, (r.items[2].quantity as ParsedQuantity.Count).kind)
    }

    @Test
    fun `чайная ложка выводится из столовой и мера приводится к одной единице`() {
        val oil = listOf(ServingSize("1 tbsp", 13.5, ServingKind.HOUSEHOLD, "USDA"))
        val tsp = QuantityResolver.resolve(ParsedQuantity.Count(2.0, CountKind.HOUSEHOLD, "чайных ложках"), AmountUnit.GRAM, oil) as ResolvedAmount.Resolved
        assertEquals(9.0, tsp.amount, eps)
        assertTrue(tsp.explanation!!.contains("÷ 3"))
        // «0.5 cup» весом 120 г — это 240 г на стакан, а не 120.
        val milk = listOf(ServingSize("0.5 cup", 122.0, ServingKind.HOUSEHOLD, "USDA"))
        val glass = QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "стакан"), AmountUnit.GRAM, milk) as ResolvedAmount.Resolved
        assertEquals(244.0, glass.amount, eps)
        val cups = QuantityResolver.resolve(ParsedQuantity.Count(2.0, CountKind.HOUSEHOLD, "чашек"), AmountUnit.GRAM, milk) as ResolvedAmount.Resolved
        assertEquals(488.0, cups.amount, eps)
        // Продукт в миллилитрах без справочных мер: ложки — метрические.
        val ml = QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "столовая ложка"), AmountUnit.MILLILITER, emptyList()) as ResolvedAmount.Resolved
        assertEquals(15.0, ml.amount, eps)
        // Граммы без мер — спросить, а не выдумывать.
        assertTrue(QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.HOUSEHOLD, "чайная ложка"), AmountUnit.GRAM, emptyList()) is ResolvedAmount.NeedsInput)
    }

    @Test
    fun `русское название в начальной форме и уточнение для масла`() {
        assertEquals("Творог", FoodQueryTranslator.translate("творога").russianName)
        assertEquals("Куриное филе", FoodQueryTranslator.translate("куриного филе").russianName)
        assertEquals("Спагетти", FoodQueryTranslator.translate("спагетти").russianName)
        assertEquals("spaghetti", FoodQueryTranslator.translate("спагетти").englishQuery)
        val oil = FoodQueryTranslator.translate("масла")
        assertNull(oil.englishQuery)
        assertTrue(oil.clarifications.contains("Сливочное масло"))
        assertTrue(FoodQueryTranslator.translate("оливкового масла").clarifications.isEmpty())
        assertEquals("olive oil", FoodQueryTranslator.translate("оливкового масла").englishQuery)
        assertTrue(FoodQueryTranslator.translate("маслины").clarifications.isEmpty())
    }

    @Test
    fun `на spaghetti выбираются спагетти а не соус`() {
        fun usda(name: String, source: com.nutritiontracker.core.model.FoodSource = com.nutritiontracker.core.model.FoodSource.USDA_SURVEY) =
            com.nutritiontracker.core.model.FoodData(
                name = name, source = source, externalId = name,
                nutrients = com.nutritiontracker.core.model.Nutrients.of(com.nutritiontracker.core.model.Nutrient.ENERGY to 150.0),
            )
        fun best(query: String, names: List<String>): String {
            val en = FoodQueryTranslator.translate(query).englishQuery
            return names.maxBy { FoodSearch.autoPickScore(query, en, usda(it)) }
        }
        assertEquals("Spaghetti, cooked", best("спагетти", listOf("Spaghetti sauce", "Spaghetti sauce with meat", "DENNY'S, spaghetti and meatballs", "Spaghetti, cooked")))
        assertEquals("Rice, white, long-grain, regular, cooked", best("рис", listOf("Dirty rice", "Rice dressing", "Crackers, rice", "Rice paper", "Rice, white, long-grain, regular, cooked")))
        assertEquals("Cheese, cottage, lowfat, 2% milkfat", best("творог", listOf("Cheese, cottage, with fruit", "Cheese, cottage, with vegetables", "Cheese, cottage, lowfat, 2% milkfat")))
        assertEquals(
            "Chicken, broilers or fryers, breast, meat only, cooked, roasted",
            best("куриное филе", listOf("Lunchmeat, chicken breast, sliced", "Chicken breast tenders, breaded, uncooked", "Chicken, broilers or fryers, breast, meat only, cooked, roasted")),
        )
        // Без калорийности — не выбирается автоматически.
        val noEnergy = com.nutritiontracker.core.model.FoodData(name = "Творог", source = com.nutritiontracker.core.model.FoodSource.OPEN_FOOD_FACTS, externalId = "1", nutrients = com.nutritiontracker.core.model.Nutrients.of(com.nutritiontracker.core.model.Nutrient.PROTEIN to 17.0))
        assertEquals(0.0, FoodSearch.autoPickScore("творог", "cottage cheese", noEnergy), 0.0)
    }
}
