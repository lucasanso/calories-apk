package com.calories.importer.usda

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.importer.io.CsvReader
import com.calories.importer.model.ConsistencyInputs
import com.calories.importer.model.SourceFood
import java.math.BigDecimal
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Lê o pacote CSV oficial "FoodData Central — SR Legacy (2018-04)" do USDA (domínio público, CC0)
 * diretamente do .zip, só para os `fdc_id` selecionados na curadoria.
 * Valores são usados exatamente como publicados (sem arredondar). Base: por 100 g.
 */
class FdcSrLegacyReader(private val zip: Path) {

    companion object {
        const val SOURCE = "USDA_SR"

        /** Números de nutriente do FDC (tabela nutrient.csv). */
        val NUTRIENT_IDS = mapOf(
            Nutrient.ENERGY to 1008, // Energy (KCAL)
            Nutrient.PROTEIN to 1003, // Protein (G)
            Nutrient.CARBOHYDRATE to 1005, // Carbohydrate, by difference (G)
            Nutrient.FAT to 1004, // Total lipid (fat) (G)
            Nutrient.SATURATED_FAT to 1258, // Fatty acids, total saturated (G)
            Nutrient.FIBER to 1079, // Fiber, total dietary (G)
            Nutrient.SODIUM to 1093, // Sodium, Na (MG)
        )
        const val WATER = 1051
        const val ASH = 1007
        const val ALCOHOL = 1018

        /** Unidades esperadas, conferidas contra nutrient.csv para evitar troca silenciosa de unidade. */
        private val EXPECTED_UNITS = mapOf(1008 to "KCAL", 1003 to "G", 1005 to "G", 1004 to "G", 1258 to "G", 1079 to "G", 1093 to "MG", 1051 to "G", 1007 to "G", 1018 to "G")
    }

    data class FdcFood(val fdcId: Int, val description: String, val category: String, val amounts: Map<Int, BigDecimal>)

    fun read(fdcIds: Set<Int>): Map<Int, FdcFood> = ZipFile(zip.toFile()).use { z ->
        fun records(suffix: String, action: (List<String>) -> Unit) {
            val entry = z.entries().asSequence().firstOrNull { it.name.endsWith("/$suffix") || it.name == suffix }
                ?: throw IllegalArgumentException("$zip: $suffix ausente")
            z.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { CsvReader(it).forEachRecord(action) }
        }

        // Confere nomes de coluna e unidades.
        var header: List<String>? = null
        records("nutrient.csv") { r ->
            if (header == null) { header = r; return@records }
            val id = r[0].toIntOrNull() ?: return@records
            EXPECTED_UNITS[id]?.let { unit -> require(r[2] == unit) { "USDA nutriente $id com unidade ${r[2]}, esperado $unit" } }
        }

        val categories = HashMap<String, String>()
        records("food_category.csv") { r -> if (r[0] != "id") categories[r[0]] = r[2] }

        val foods = HashMap<Int, Pair<String, String>>()
        records("food.csv") { r ->
            if (r[0] == "fdc_id") { require(r[2] == "description" && r[3] == "food_category_id") { "food.csv: cabeçalho inesperado $r" }; return@records }
            val id = r[0].toInt()
            if (id in fdcIds) foods[id] = r[2] to (categories[r[3]] ?: r[3])
        }
        val missing = fdcIds - foods.keys
        require(missing.isEmpty()) { "USDA: fdc_id não encontrados no SR Legacy: $missing" }

        val wanted = (NUTRIENT_IDS.values + listOf(WATER, ASH, ALCOHOL)).toSet()
        val amounts = HashMap<Int, MutableMap<Int, BigDecimal>>()
        records("food_nutrient.csv") { r ->
            if (r[0] == "id") { require(r[1] == "fdc_id" && r[2] == "nutrient_id" && r[3] == "amount") { "food_nutrient.csv: cabeçalho inesperado $r" }; return@records }
            val fdcId = r[1].toInt()
            if (fdcId !in fdcIds) return@records
            val nutrientId = r[2].toInt()
            if (nutrientId !in wanted || r[3].isBlank()) return@records
            val previous = amounts.getOrPut(fdcId) { HashMap() }.put(nutrientId, BigDecimal(r[3]))
            require(previous == null) { "USDA: nutriente $nutrientId duplicado para $fdcId" }
        }

        foods.mapValues { (id, info) -> FdcFood(id, info.first, info.second, amounts[id].orEmpty()) }
    }

    fun toSourceFood(food: FdcFood): SourceFood {
        val nutrients = NUTRIENT_IDS.mapValues { (_, id) ->
            food.amounts[id]?.let { NutrientValue.Measured(it.toDouble()) } ?: NutrientValue.NotAvailable
        }
        return SourceFood(
            source = SOURCE,
            code = food.fdcId.toString(),
            description = food.description,
            group = food.category,
            referenceAmount = 100.0,
            referenceUnit = MeasureUnit.GRAM,
            nutrients = nutrients,
            notes = emptyList(),
            consistency = ConsistencyInputs(
                energyKcal = food.amounts[1008],
                moisture = food.amounts[WATER],
                protein = food.amounts[1003],
                fat = food.amounts[1004],
                carbohydrate = food.amounts[1005],
                ash = food.amounts[ASH],
                alcohol = food.amounts[ALCOHOL],
            ),
        )
    }
}
