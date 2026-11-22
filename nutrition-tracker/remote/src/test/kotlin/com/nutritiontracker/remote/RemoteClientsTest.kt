package com.nutritiontracker.remote

import com.nutritiontracker.core.model.AmountUnit
import com.nutritiontracker.core.model.FoodSource
import com.nutritiontracker.core.model.Nutrient
import com.nutritiontracker.core.model.ServingKind
import com.nutritiontracker.core.voice.CountKind
import com.nutritiontracker.core.voice.ParsedQuantity
import com.nutritiontracker.core.voice.QuantityResolver
import com.nutritiontracker.core.voice.ResolvedAmount
import com.nutritiontracker.remote.off.OpenFoodFactsClient
import com.nutritiontracker.remote.usda.UsdaClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Ответы в fixtures/ — настоящие ответы USDA FoodData Central и Open Food Facts,
 * сохранённые в ноябре 2026 года.
 */
class RemoteClientsTest {
    private lateinit var server: MockWebServer
    private val eps = 1e-6

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResource("fixtures/$name")!!.readText()

    private fun enqueue(name: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(fixture(name)))
    }

    private fun usda(key: String = "") = UsdaClient(RemoteJson.defaultOkHttp(), { key }, server.url("/fdc/v1/").toString())
    private fun off() = OpenFoodFactsClient(RemoteJson.defaultOkHttp(), "NutritionTrackerTest/1.0", server.url("/").toString())

    private fun <T> RemoteResult<T>.ok(): T {
        assertTrue("Ожидался успех, получено $this", this is RemoteResult.Success)
        return (this as RemoteResult.Success).value
    }

    @Test
    fun usdaSearchSendsKeyAndDataTypesAndParsesNutrients() = runTest {
        enqueue("usda_search_chicken.json")
        val foods = usda("MY_KEY").search("chicken breast").ok()
        val req = server.takeRequest()
        assertEquals("MY_KEY", req.requestUrl!!.queryParameter("api_key"))
        assertEquals("Foundation,SR Legacy,Survey (FNDDS)", req.requestUrl!!.queryParameter("dataType"))
        assertEquals(2, foods.size)
        val f = foods[0].food
        assertFalse(foods[0].isFullRecord)
        assertEquals("2646170", f.externalId)
        assertEquals(FoodSource.USDA_FOUNDATION, f.source)
        assertEquals(22.5, f.nutrients[Nutrient.PROTEIN]!!, 0.1)
        assertTrue(f.nutrients.isKnown(Nutrient.ENERGY))
    }

    @Test
    fun usdaFallsBackToDemoKey() = runTest {
        enqueue("usda_search_chicken.json")
        usda("").search("chicken").ok()
        assertEquals("DEMO_KEY", server.takeRequest().requestUrl!!.queryParameter("api_key"))
    }

    @Test
    fun foundationUsesAtwaterEnergyKeepsMissingVitaminsUnknownAndZeroAsZero() = runTest {
        enqueue("usda_food_foundation.json")
        val f = usda().details("2646170").ok().food
        assertEquals("Chicken, breast, boneless, skinless, raw", f.name)
        assertEquals("Poultry Products", f.category)
        assertEquals(112.20227, f.nutrients[Nutrient.ENERGY]!!, eps)
        assertTrue(f.dataNotes.any { it.contains("специфическим коэффициентам") })
        assertEquals(22.525, f.nutrients[Nutrient.PROTEIN]!!, eps)
        assertEquals(0.0, f.nutrients[Nutrient.CARBS]!!, 0.0)
        assertEquals(0.0, f.nutrients[Nutrient.MANGANESE]!!, 0.0)
        assertEquals(72.73, f.nutrients[Nutrient.CHOLESTEROL]!!, eps)
        // В этой записи USDA нет витаминов — они должны быть неизвестны, а не нулями.
        assertNull(f.nutrients[Nutrient.VITAMIN_C])
        assertNull(f.nutrients[Nutrient.VITAMIN_B12])
        assertNull(f.nutrients[Nutrient.VITAMIN_D])
        assertEquals(AmountUnit.GRAM, f.basis)
        val racc = f.servings.single()
        assertEquals(ServingKind.PORTION, racc.kind)
        assertEquals(114.0, racc.amount, eps)
    }

    @Test
    fun srLegacyHasVitaminsAndReferencePieceWeight() = runTest {
        enqueue("usda_food_sr_apple.json")
        val f = usda().details("171688").ok().food
        assertEquals(FoodSource.USDA_SR_LEGACY, f.source)
        assertEquals(52.0, f.nutrients[Nutrient.ENERGY]!!, eps)
        assertTrue(f.dataNotes.none { it.contains("Этуотера") })
        assertEquals(3.0, f.nutrients[Nutrient.VITAMIN_A]!!, eps)
        assertEquals(4.6, f.nutrients[Nutrient.VITAMIN_C]!!, eps)
        assertEquals(3.0, f.nutrients[Nutrient.VITAMIN_B9]!!, eps)
        assertEquals(0.0, f.nutrients[Nutrient.VITAMIN_D]!!, 0.0)
        assertEquals(2.2, f.nutrients[Nutrient.VITAMIN_K]!!, eps)
        assertEquals(0.0, f.nutrients[Nutrient.SELENIUM]!!, 0.0) // «µg» распознан
        assertEquals(0.035, f.nutrients[Nutrient.MANGANESE]!!, eps)
        val piece = f.servings.first { it.kind == ServingKind.PIECE }
        assertEquals(182.0, piece.amount, eps)
        assertTrue(piece.label.contains("medium"))
        val r = QuantityResolver.resolve(ParsedQuantity.Count(1.0, CountKind.PIECE), f.basis, f.servings) as ResolvedAmount.Resolved
        assertEquals(182.0, r.amount, eps)
        assertTrue(r.explanation!!.contains("USDA"))
        assertTrue(f.servings.any { it.kind == ServingKind.HOUSEHOLD && it.label.contains("cup") })
    }

    @Test
    fun brandedVitaminAInIuIsNotConvertedToRae() = runTest {
        enqueue("usda_food_branded.json")
        val f = usda().details("454004").ok().food
        assertEquals(FoodSource.USDA_BRANDED, f.source)
        assertEquals("TREECRISP 2 GO", f.brand)
        assertEquals(52.0, f.nutrients[Nutrient.ENERGY]!!, eps)
        assertNull(f.nutrients[Nutrient.VITAMIN_A])
        assertEquals(3.1, f.nutrients[Nutrient.VITAMIN_C]!!, eps)
        assertEquals(154.0, f.servings.single().amount, eps)
    }

    @Test
    fun invalidUsdaKeyIsRecognised() = runTest {
        enqueue("usda_bad_key.json", 403)
        val r = usda("bad").search("apple")
        assertTrue(r is RemoteResult.Failure)
        assertEquals(RemoteErrorKind.INVALID_KEY, (r as RemoteResult.Failure).error.kind)
    }

    @Test
    fun rateLimitIsRecognised() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setBody("{\"error\":{\"code\":\"OVER_RATE_LIMIT\"}}"))
        val r = usda().search("apple") as RemoteResult.Failure
        assertEquals(RemoteErrorKind.RATE_LIMITED, r.error.kind)
    }

    @Test
    fun noNetworkGivesErrorNotException() = runTest {
        val client = usda()
        server.shutdown()
        val r = client.search("apple")
        assertTrue(r is RemoteResult.Failure)
        assertEquals(RemoteErrorKind.NO_NETWORK, (r as RemoteResult.Failure).error.kind)
    }

    @Test
    fun unexpectedResponseDoesNotCrash() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>maintenance</html>"))
        val r = usda().search("apple") as RemoteResult.Failure
        assertEquals(RemoteErrorKind.BAD_RESPONSE, r.error.kind)
    }

    @Test
    fun openFoodFactsSearchInRussian() = runTest {
        enqueue("off_search_cgi.json")
        val foods = off().search("творог").ok()
        val req = server.takeRequest()
        assertEquals("творог", req.requestUrl!!.queryParameter("search_terms"))
        assertEquals("NutritionTrackerTest/1.0", req.getHeader("User-Agent"))
        val tvorog = foods.map { it.food }.first { it.externalId == "4600605029343" }
        assertEquals("Творог 2%", tvorog.name)
        assertEquals("Простоквашино", tvorog.brand)
        assertEquals(FoodSource.OPEN_FOOD_FACTS, tvorog.source)
        assertEquals(99.0, tvorog.nutrients[Nutrient.ENERGY]!!, eps)
        assertEquals(17.0, tvorog.nutrients[Nutrient.PROTEIN]!!, eps)
        assertEquals(AmountUnit.GRAM, tvorog.basis)
        assertNull(tvorog.nutrients[Nutrient.CALCIUM])
        assertEquals(200.0, tvorog.servings.first { it.kind == ServingKind.PACKAGE }.amount, eps)
    }

    @Test
    fun openFoodFactsGramsAreConvertedToMilligrams() = runTest {
        enqueue("off_product_nutella.json")
        val f = off().product("3017620422003").ok().food
        assertEquals(539.0, f.nutrients[Nutrient.ENERGY]!!, eps)
        assertEquals(42.8, f.nutrients[Nutrient.SODIUM]!!, eps)
        assertEquals(0.0, f.nutrients[Nutrient.FIBER]!!, 0.0)
        assertEquals("Ferrero", f.brand)
    }

    @Test
    fun milkIsPer100MlAndEnergyConvertedFromKj() = runTest {
        enqueue("off_search_milk.json")
        val milk = off().search("молоко").ok().map { it.food }.first { it.externalId == "4607053473544" }
        assertEquals(AmountUnit.MILLILITER, milk.basis)
        assertEquals(225.0 / 4.184, milk.nutrients[Nutrient.ENERGY]!!, eps)
        assertTrue(milk.dataNotes.any { it.contains("кДж") })
        assertTrue(milk.dataNotes.any { it.contains("100 мл") })
    }

    @Test
    fun drinkWithMlPackageIsPer100Ml() = runTest {
        enqueue("off_product_cola.json")
        val cola = off().product("5449000000996").ok().food
        assertEquals(AmountUnit.MILLILITER, cola.basis)
        assertEquals(44.0, cola.nutrients[Nutrient.ENERGY]!!, eps)
        assertEquals(330.0, cola.servings.first { it.kind == ServingKind.PORTION }.amount, eps)
    }

    @Test
    fun missingOpenFoodFactsProduct() = runTest {
        enqueue("off_product_missing.json", 404)
        val r = off().product("0000000000000") as RemoteResult.Failure
        assertEquals(RemoteErrorKind.NOT_FOUND, r.error.kind)
        enqueue("off_product_missing.json", 200)
        val r2 = off().product("0000000000000") as RemoteResult.Failure
        assertEquals(RemoteErrorKind.NOT_FOUND, r2.error.kind)
    }

    @Test
    fun offLitersAreMillilitersAndEnergyUnitIsRespected() {
        val p = com.nutritiontracker.remote.off.OffProduct(
            code = "1", productName = "Вода минеральная",
            productQuantity = kotlinx.serialization.json.JsonPrimitive(1.5), productQuantityUnit = "l",
            nutriments = kotlinx.serialization.json.buildJsonObject {
                put("energy_100g", kotlinx.serialization.json.JsonPrimitive(0))
                put("energy_unit", kotlinx.serialization.json.JsonPrimitive("kcal"))
            },
        )
        val f = com.nutritiontracker.remote.off.OffMapper.map(p)!!
        assertEquals(AmountUnit.MILLILITER, f.basis)
        assertEquals(1500.0, f.servings.single { it.kind == ServingKind.PACKAGE }.amount, eps)
        assertEquals(0.0, f.nutrients[Nutrient.ENERGY]!!, 0.0)
        assertTrue(f.dataNotes.any { it.contains("ккал") })
    }
}
