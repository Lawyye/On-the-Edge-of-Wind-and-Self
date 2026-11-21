package com.nutritiontracker.core.model

/**
 * Единицы, в которых хранится каждый нутриент. Для каждого нутриента единица
 * одна и та же везде: в базе, в расчётах, в нормах. Переводы между единицами
 * выполняются только через [UnitConversion].
 */
enum class NutrientUnit(val symbol: String) {
    KCAL("ккал"),
    G("г"),
    MG("мг"),
    UG("мкг"),
}

enum class NutrientGroup(val titleRu: String) {
    ENERGY("Энергия"),
    MACRO("Макронутриенты"),
    FAT_DETAIL("Жиры подробно"),
    VITAMIN("Витамины"),
    MINERAL("Минералы"),
    OTHER("Прочее"),
}

/**
 * Полный список отслеживаемых нутриентов.
 *
 * [key] — стабильный идентификатор для хранения и резервных копий. Его нельзя
 * менять: по нему читаются старые записи.
 */
enum class Nutrient(
    val key: String,
    val unit: NutrientUnit,
    val group: NutrientGroup,
    val titleRu: String,
    val shortRu: String = titleRu,
    val noteRu: String? = null,
) {
    ENERGY("energy", NutrientUnit.KCAL, NutrientGroup.ENERGY, "Энергетическая ценность", "Калории"),

    PROTEIN("protein", NutrientUnit.G, NutrientGroup.MACRO, "Белки"),
    FAT("fat", NutrientUnit.G, NutrientGroup.MACRO, "Жиры"),
    CARBS("carbs", NutrientUnit.G, NutrientGroup.MACRO, "Углеводы"),
    FIBER("fiber", NutrientUnit.G, NutrientGroup.MACRO, "Клетчатка"),
    SUGARS("sugars", NutrientUnit.G, NutrientGroup.MACRO, "Сахара"),
    ADDED_SUGARS("added_sugars", NutrientUnit.G, NutrientGroup.MACRO, "Добавленные сахара"),

    SATURATED_FAT("saturated_fat", NutrientUnit.G, NutrientGroup.FAT_DETAIL, "Насыщенные жиры"),
    MONOUNSATURATED_FAT("monounsaturated_fat", NutrientUnit.G, NutrientGroup.FAT_DETAIL, "Мононенасыщенные жиры"),
    POLYUNSATURATED_FAT("polyunsaturated_fat", NutrientUnit.G, NutrientGroup.FAT_DETAIL, "Полиненасыщенные жиры"),
    TRANS_FAT("trans_fat", NutrientUnit.G, NutrientGroup.FAT_DETAIL, "Трансжиры"),
    CHOLESTEROL("cholesterol", NutrientUnit.MG, NutrientGroup.FAT_DETAIL, "Холестерин"),

    VITAMIN_A(
        "vitamin_a", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин A", "A",
        "В эквивалентах активности ретинола (RAE). На этикетках встречаются ретиноловые эквиваленты (RE), они могут быть выше RAE.",
    ),
    VITAMIN_B1("vitamin_b1", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин B1 (тиамин)", "B1"),
    VITAMIN_B2("vitamin_b2", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин B2 (рибофлавин)", "B2"),
    VITAMIN_B3(
        "vitamin_b3", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин B3 (ниацин)", "B3",
        "Базы продуктов указывают готовый ниацин. Норма задана в ниациновых эквивалентах, которые учитывают ещё и триптофан, поэтому фактическое обеспечение обычно выше показанного.",
    ),
    VITAMIN_B5("vitamin_b5", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин B5 (пантотеновая кислота)", "B5"),
    VITAMIN_B6("vitamin_b6", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин B6", "B6"),
    VITAMIN_B7("vitamin_b7", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин B7 (биотин)", "B7"),
    VITAMIN_B9(
        "vitamin_b9", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин B9 (фолат)", "B9",
        "В диетических фолатных эквивалентах (DFE). Для данных с этикеток пересчёт в DFE не всегда возможен.",
    ),
    VITAMIN_B12("vitamin_b12", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин B12", "B12"),
    VITAMIN_C("vitamin_c", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин C", "C"),
    VITAMIN_D("vitamin_d", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин D", "D"),
    VITAMIN_E("vitamin_e", NutrientUnit.MG, NutrientGroup.VITAMIN, "Витамин E (альфа-токоферол)", "E"),
    VITAMIN_K(
        "vitamin_k", NutrientUnit.UG, NutrientGroup.VITAMIN, "Витамин K", "K",
        "USDA приводит филлохинон (K1) — основную пищевую форму витамина K.",
    ),

    CALCIUM("calcium", NutrientUnit.MG, NutrientGroup.MINERAL, "Кальций", "Ca"),
    MAGNESIUM("magnesium", NutrientUnit.MG, NutrientGroup.MINERAL, "Магний", "Mg"),
    POTASSIUM("potassium", NutrientUnit.MG, NutrientGroup.MINERAL, "Калий", "K"),
    SODIUM("sodium", NutrientUnit.MG, NutrientGroup.MINERAL, "Натрий", "Na"),
    PHOSPHORUS("phosphorus", NutrientUnit.MG, NutrientGroup.MINERAL, "Фосфор", "P"),
    IRON("iron", NutrientUnit.MG, NutrientGroup.MINERAL, "Железо", "Fe"),
    ZINC("zinc", NutrientUnit.MG, NutrientGroup.MINERAL, "Цинк", "Zn"),
    COPPER("copper", NutrientUnit.MG, NutrientGroup.MINERAL, "Медь", "Cu"),
    MANGANESE("manganese", NutrientUnit.MG, NutrientGroup.MINERAL, "Марганец", "Mn"),
    SELENIUM("selenium", NutrientUnit.UG, NutrientGroup.MINERAL, "Селен", "Se"),
    IODINE("iodine", NutrientUnit.UG, NutrientGroup.MINERAL, "Йод", "I"),
    CHLORIDE("chloride", NutrientUnit.MG, NutrientGroup.MINERAL, "Хлорид", "Cl"),

    ALCOHOL("alcohol", NutrientUnit.G, NutrientGroup.OTHER, "Алкоголь (этанол)"),
    ;

    companion object {
        private val byKey = entries.associateBy { it.key }

        fun fromKey(key: String): Nutrient? = byKey[key]

        val vitamins: List<Nutrient> = entries.filter { it.group == NutrientGroup.VITAMIN }
        val minerals: List<Nutrient> = entries.filter { it.group == NutrientGroup.MINERAL }
        val macros: List<Nutrient> = entries.filter { it.group == NutrientGroup.MACRO }
        val fatDetails: List<Nutrient> = entries.filter { it.group == NutrientGroup.FAT_DETAIL }
    }
}
