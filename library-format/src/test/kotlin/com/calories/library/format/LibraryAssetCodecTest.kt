package com.calories.library.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LibraryAssetCodecTest {

    private val food = AssetFood(
        key = "TACO4:410", source = "TACO4", sourceCode = "410",
        sourceDescription = "Frango, peito, sem pele, grelhado", sourceGroup = "Carnes e derivados",
        name = "Frango, peito, sem pele, grelhado", baseName = "Frango, peito, sem pele",
        category = "PROTEINS", preparation = "GRILLED", reference = AssetReference(100.0, "GRAM"),
        nutrients = AssetNutrients(
            energyKcal = AssetNutrient.measured(159.0), proteinG = AssetNutrient.measured(32.0),
            carbohydrateG = AssetNutrient.measured(0.0), fatG = AssetNutrient.measured(2.5),
            saturatedFatG = AssetNutrient.measured(0.9), fiberG = AssetNutrient.notApplicable,
            sodiumMg = AssetNutrient.trace,
        ),
    )

    private val asset = LibraryAsset(
        formatVersion = LibraryAssetCodec.FORMAT_VERSION, libraryVersion = 1,
        sources = listOf(AssetSource(code = "TACO4", name = "TACO", license = "L", citation = "C", carbohydrateBasis = "TOTAL_BY_DIFFERENCE")),
        categories = listOf(AssetCategory("PROTEINS", "Proteínas", 1)),
        foods = listOf(food),
    )

    @Test
    fun `ida e volta preserva o conteudo`() {
        val text = LibraryAssetCodec.encode(asset)
        assertEquals(asset, LibraryAssetCodec.decode(text))
    }

    @Test
    fun `codificacao e deterministica e termina com quebra de linha`() {
        val a = LibraryAssetCodec.encode(asset)
        assertEquals(a, LibraryAssetCodec.encode(asset.copy()))
        assertTrue(a.endsWith("}\n"))
    }

    @Test
    fun `status padrao MEASURED e omitido e status especiais sao explicitos`() {
        val text = LibraryAssetCodec.encode(asset)
        assertTrue("\"energyKcal\": {\n" in text && "\"value\": 159.0" in text)
        assertTrue("\"status\": \"NOT_APPLICABLE\"" in text)
        assertTrue("\"status\": \"TRACE\"" in text)
    }

    @Test
    fun `versao de formato desconhecida e rejeitada`() {
        val text = LibraryAssetCodec.encode(asset.copy(formatVersion = 99))
        assertFailsWith<IllegalArgumentException> { LibraryAssetCodec.decode(text) }
    }

    @Test
    fun `nutriente invalido e rejeitado`() {
        assertFailsWith<IllegalArgumentException> { AssetNutrient(AssetNutrient.MEASURED, null) }
        assertFailsWith<IllegalArgumentException> { AssetNutrient(AssetNutrient.TRACE, 0.0) }
        assertFailsWith<IllegalArgumentException> { AssetNutrient("ZERO", null) }
    }
}
