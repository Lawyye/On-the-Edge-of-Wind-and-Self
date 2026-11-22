package com.nutritiontracker.data

import androidx.room.withTransaction
import com.nutritiontracker.core.calc.RecipeCalculation
import com.nutritiontracker.core.calc.RecipeCalculator
import com.nutritiontracker.core.calc.RecipeIngredientInput
import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodData
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.model.ServingSize
import com.nutritiontracker.core.format.NutrientFormat
import com.nutritiontracker.data.db.AppDatabase
import com.nutritiontracker.data.db.RecipeEntity
import com.nutritiontracker.data.db.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

data class Recipe(
    val id: Long,
    val name: String,
    val cookedWeightG: Double?,
    val servingsCount: Int?,
    val foodId: Long?,
    val notes: String?,
    val updatedAt: Long,
)

fun RecipeEntity.toRecipe() = Recipe(id, name, cookedWeightG, servingsCount, foodId, notes, updatedAt)

/** Черновик рецепта в редакторе. */
data class RecipeDraft(
    val id: Long = 0,
    val name: String = "",
    val ingredients: List<RecipeIngredient> = emptyList(),
    val cookedWeightG: Double? = null,
    val servingsCount: Int? = null,
    val notes: String? = null,
) {
    fun calculate(): RecipeCalculation = RecipeCalculator.calculate(
        ingredients.map { RecipeIngredientInput(it.name, it.amount, it.unit, it.per100) },
        cookedWeightG,
    )
}

class RecipeRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = db.recipeDao()
    private val foodDao = db.foodDao()

    fun observeAll(): Flow<List<Recipe>> = dao.observeAll().map { l -> l.map { it.toRecipe() } }
    fun observe(id: Long): Flow<Recipe?> = dao.observe(id).map { it?.toRecipe() }

    suspend fun loadDraft(id: Long): RecipeDraft? {
        val r = dao.get(id) ?: return null
        return RecipeDraft(
            id = r.id,
            name = r.name,
            ingredients = dao.ingredients(id).map { it.toIngredient() },
            cookedWeightG = r.cookedWeightG,
            servingsCount = r.servingsCount,
            notes = r.notes,
        )
    }

    /**
     * Сохраняет рецепт. Если указан вес готового блюда, рецепт становится
     * продуктом с ценностью на 100 г готового блюда: его можно искать и
     * добавлять в дневник порциями. Записи дневника, сделанные раньше,
     * не меняются — в них снимок.
     */
    suspend fun save(draft: RecipeDraft): Long = db.withTransaction {
        require(draft.name.isNotBlank()) { "Укажите название рецепта" }
        require(draft.ingredients.isNotEmpty()) { "Добавьте хотя бы один ингредиент" }
        val calc = draft.calculate()
        val now = clock()
        val existing = if (draft.id != 0L) dao.get(draft.id) else null
        val recipeId = existing?.id ?: dao.insert(
            RecipeEntity(
                name = draft.name.trim(), cookedWeightG = draft.cookedWeightG, servingsCount = draft.servingsCount,
                foodId = null, notes = draft.notes, createdAt = now, updatedAt = now,
            ),
        )
        dao.deleteIngredients(recipeId)
        dao.insertIngredients(
            draft.ingredients.mapIndexed { i, ing ->
                RecipeIngredientEntity(
                    recipeId = recipeId, foodId = ing.foodId, name = ing.name, amount = ing.amount, unit = ing.unit.name,
                    nutrientsPer100 = StorageJson.nutrients(ing.per100), incompleteNutrients = StorageJson.incomplete(ing.per100),
                    position = i,
                )
            },
        )

        var foodId = existing?.foodId?.takeIf { foodDao.get(it) != null }
        val per100 = calc.per100Cooked
        if (per100 != null && draft.cookedWeightG != null) {
            val servings = buildList {
                draft.servingsCount?.takeIf { it > 0 }?.let { n ->
                    add(ServingSize("1 порция из $n", draft.cookedWeightG / n, ServingKind.PORTION, "Ваш рецепт"))
                }
                add(ServingSize("Всё блюдо", draft.cookedWeightG, ServingKind.PACKAGE, "Ваш рецепт"))
            }
            val notes = buildList {
                add("Рассчитано по ${draft.ingredients.size} ингредиентам и весу готового блюда ${NutrientFormat.amount(draft.cookedWeightG)} г")
                if (per100.incomplete.isNotEmpty()) add("Для части нутриентов данные есть не у всех ингредиентов — значения указаны как нижняя граница")
            }
            val data = FoodData(
                name = draft.name.trim(),
                category = "Рецепт",
                source = FoodSource.RECIPE,
                externalId = null,
                basis = AmountUnit.GRAM,
                nutrients = per100,
                servings = servings,
                dataNotes = notes,
            )
            val currentFood = foodId?.let { foodDao.get(it) }
            if (currentFood == null) {
                foodId = foodDao.insert(newFoodEntity(data, emptyList(), true, now, null, recipeId))
            } else {
                foodDao.update(currentFood.withData(data, true, now, null).copy(recipeId = recipeId))
            }
        } else if (foodId != null) {
            // Без веса готового блюда ценность на 100 г неизвестна — продукт рецепта убирается из поиска.
            foodDao.delete(foodId)
            foodId = null
        }
        dao.update(
            RecipeEntity(
                id = recipeId, name = draft.name.trim(), cookedWeightG = draft.cookedWeightG, servingsCount = draft.servingsCount,
                foodId = foodId, notes = draft.notes?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = existing?.createdAt ?: now, updatedAt = now,
            ),
        )
        recipeId
    }

    /** Обновляет снимки ингредиентов по текущим данным продуктов. */
    suspend fun refreshIngredientData(draft: RecipeDraft): RecipeDraft {
        val updated = draft.ingredients.map { ing ->
            val food = ing.foodId?.let { foodDao.get(it)?.toItem() } ?: return@map ing
            if (food.basis != ing.unit) ing else ing.copy(per100 = food.nutrients)
        }
        return draft.copy(ingredients = updated)
    }

    suspend fun delete(id: Long) = db.withTransaction {
        val r = dao.get(id) ?: return@withTransaction
        r.foodId?.let { foodDao.delete(it) }
        dao.delete(id)
    }
}
