package com.calories.importer

import com.calories.domain.nutrition.Nutrient
import com.calories.importer.assemble.get
import com.calories.library.format.AssetNutrient
import com.calories.library.format.LibraryAssetCodec
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Executa o importador sobre os arquivos oficiais versionados em `nutrition-data/` e confere
 * o resultado contra o asset commitado. Falha se alguém editar o asset à mão ou mudar a
 * curadoria sem regenerar.
 */
class ImportPipelineIntegrationTest {

    private val repoRoot: Path = Path.of(System.getProperty("calories.repoRoot") ?: error("calories.repoRoot não definido"))

    companion object {
        private var cached: ImportPipeline.Output? = null
    }

    private lateinit var output: ImportPipeline.Output

    @BeforeTest
    fun run() {
        output = cached ?: run {
            val committed = repoRoot.resolve("app/src/main/assets/library/food_library.json")
            val previous = LibraryAssetCodec.decode(Files.readString(committed))
            ImportPipeline(repoRoot.resolve("nutrition-data")).run(previous).also { cached = it }
        }
    }

    private fun food(key: String) = output.asset.foods.single { it.key == key }

    @Test
    fun `importacao sem erros`() {
        assertEquals(emptyList(), output.errors.map { "${it.code} ${it.key} ${it.message}" })
    }

    @Test
    fun `asset commitado e exatamente o gerado`() {
        val committed = Files.readString(repoRoot.resolve("app/src/main/assets/library/food_library.json"))
        assertEquals(committed, output.assetText)
    }

    @Test
    fun `geracao e deterministica`() {
        val again = ImportPipeline(repoRoot.resolve("nutrition-data")).run(output.asset)
        assertEquals(output.assetText, again.assetText)
        assertEquals(output.reportText, again.reportText)
    }

    @Test
    fun `TACO completa e lacunas do USDA, sem duplicatas`() {
        val bySource = output.asset.foods.groupingBy { it.source }.eachCount()
        assertEquals(597, bySource["TACO4"])
        assertEquals(17, bySource["USDA_SR"])
        assertEquals(output.asset.foods.size, output.asset.foods.map { it.key }.toSet().size)
    }

    @Test
    fun `planilha e PDF oficiais da TACO concordam em todos os valores usados`() {
        val cc = output.crossCheck
        assertEquals(mapOf("Tabela 1 (composição)" to 597, "Tabela 1 (minerais)" to 597, "Tabela 2 (ácidos graxos)" to 423), cc.rowsFound)
        assertEquals(597 * 9 + 423, cc.totalCompared)
        assertEquals(emptyList(), cc.mismatches)
        assertEquals(emptyList(), cc.uncomparable)
    }

    @Test
    fun `valores publicados de alimentos conhecidos`() {
        // TACO 4ª ed., item 410 (p. 55 do arquivo PDF): 159 kcal; 32,0 g proteína; 2,5 g lipídios; 0,0 g carboidrato; fibra NA; 50 mg Na; 0,9 g saturada.
        val frango = food("TACO4:410").nutrients
        assertEquals(159.0, frango.energyKcal.value)
        assertEquals(32.0, frango.proteinG.value)
        assertEquals(2.5, frango.fatG.value)
        assertEquals(0.0, frango.carbohydrateG.value)
        assertEquals(AssetNutrient.NOT_APPLICABLE, frango.fiberG.status)
        assertEquals(50.0, frango.sodiumMg.value)
        assertEquals(0.9, frango.saturatedFatG.value)
        assertEquals("GRILLED", food("TACO4:410").preparation)
        assertEquals("Frango, peito, sem pele", food("TACO4:410").baseName)
    }

    @Test
    fun `ausencia de dado nunca vira zero`() {
        // Azeite: sódio não analisado pela TACO (célula vazia).
        assertEquals(AssetNutrient.NOT_AVAILABLE, food("TACO4:260").nutrients.sodiumMg.status)
        // Maminha grelhada: saturada inconsistente descartada, com observação.
        val maminha = food("TACO4:368")
        assertEquals(AssetNutrient.NOT_AVAILABLE, maminha.nutrients.saturatedFatG.status)
        assertTrue(maminha.notes.any { "9,7" in it })
    }

    @Test
    fun `preparos diferentes sao registros diferentes`() {
        val peito = output.asset.foods.filter { it.baseName == "Frango, peito, sem pele" }
        assertEquals(setOf("RAW", "COOKED", "GRILLED"), peito.map { it.preparation }.toSet())
        assertEquals(3, peito.map { it.nutrients.energyKcal.value }.toSet().size)
    }

    @Test
    fun `todo alimento tem fonte, categoria e preparo validos`() {
        val sources = output.asset.sources.map { it.code }.toSet()
        val categories = output.asset.categories.map { it.code }.toSet()
        for (f in output.asset.foods) {
            assertTrue(f.source in sources, f.key)
            assertTrue(f.category in categories, f.key)
            assertTrue(com.calories.domain.nutrition.Preparation.entries.any { it.name == f.preparation }, f.key)
            assertEquals(100.0, f.reference.amount, f.key)
            Nutrient.entries.forEach { n -> f.nutrients.get(n) } // todos os nutrientes presentes
        }
    }

    @Test
    fun `itens do USDA sao identificados como referencia internacional`() {
        output.asset.foods.filter { it.source == "USDA_SR" }.forEach { f ->
            assertTrue(f.notes.any { "Referência internacional" in it }, f.key)
        }
    }
}
