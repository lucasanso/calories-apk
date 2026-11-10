package com.calories.importer.curation

import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.Preparation
import com.calories.importer.io.CsvTable
import com.calories.library.format.AssetCategory
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/**
 * Decisões humanas em `nutrition-data/curation/`. Cada arquivo é um CSV revisável,
 * com uma coluna de justificativa sempre que a decisão altera algo da fonte.
 */
data class Curation(
    val libraryVersion: Int,
    val categories: List<AssetCategory>,
    val tacoGroupCategory: Map<String, String>,
    val tacoGroupPreparation: Map<String, Preparation>,
    val categoryOverrides: Map<String, String>,
    val preparationOverrides: Map<String, Preparation>,
    val displayNames: Map<String, String>,
    val aliases: Map<String, List<String>>,
    val sourceCorrections: List<SourceCorrection>,
    val tacoPdfDeviations: List<PdfDeviation>,
    val tacoAlcohol: Map<Int, BigDecimal>,
    val valueExclusions: List<ValueExclusion>,
    val usdaFoods: List<UsdaSelection>,
    val goldenValues: List<GoldenValue>,
    val requestedFoods: List<RequestedFood>,
) {
    /** Valor publicado fisicamente impossível, descartado (vira "sem dado") com justificativa exibida no app. */
    data class ValueExclusion(val key: String, val nutrient: Nutrient, val note: String, val reason: String)
    data class SourceCorrection(val source: String, val code: String, val field: String, val value: String, val reason: String)
    data class PdfDeviation(val number: Int, val field: String, val workbook: String, val pdf: String, val decision: String, val reason: String)
    data class UsdaSelection(
        val fdcId: Int, val name: String, val baseName: String?, val category: String,
        val preparation: Preparation, val note: String,
    )
    /**
     * [status] é MEASURED (com [value]), TRACE, NOT_APPLICABLE ou NOT_AVAILABLE.
     * [match]: EXACT (igualdade) ou SIG3 (a referência tem só 3 algarismos significativos:
     * aceita diferença de até meia unidade nesse algarismo).
     */
    data class GoldenValue(val key: String, val nutrient: Nutrient, val status: String, val value: BigDecimal?, val match: String, val reference: String)
    data class RequestedFood(val request: String, val category: String, val keys: List<String>, val status: String, val note: String)

    fun tacoDescriptionCorrections(): Map<Int, String> = sourceCorrections
        .filter { it.source == "TACO4" && it.field == "description" }
        .associate { it.code.toInt() to it.value }

    companion object {
        fun load(dir: Path): Curation {
            fun table(name: String) = CsvTable.readCuration(dir.resolve(name))
            val categoryCodes = HashSet<String>()

            val categories = table("categories.csv").run {
                requireColumns("code", "name", "sort_order")
                rows.map { r -> AssetCategory(r["code"], r["name"], r["sort_order"].toInt()).also { categoryCodes += it.code } }
            }
            fun category(code: String, where: String): String {
                require(code in categoryCodes) { "$where: categoria desconhecida '$code'" }
                return code
            }
            fun preparation(name: String, where: String): Preparation =
                Preparation.entries.firstOrNull { it.name == name } ?: throw IllegalArgumentException("$where: preparo desconhecido '$name'")

            val groupMap = table("taco-groups.csv").run {
                requireColumns("group", "category", "default_preparation")
                rows.associate { r -> r["group"] to (category(r["category"], "taco-groups.csv") to r["default_preparation"]) }
            }

            return Curation(
                libraryVersion = Files.readString(dir.resolve("library-version.txt")).trim().toInt(),
                categories = categories,
                tacoGroupCategory = groupMap.mapValues { it.value.first },
                tacoGroupPreparation = groupMap.filterValues { it.second.isNotEmpty() }
                    .mapValues { preparation(it.value.second, "taco-groups.csv") },
                categoryOverrides = table("category-overrides.csv").run {
                    requireColumns("key", "category", "reason")
                    rows.associate { r -> r["key"] to category(r["category"], "category-overrides.csv") }
                },
                preparationOverrides = table("preparation-overrides.csv").run {
                    requireColumns("key", "preparation", "reason")
                    rows.associate { r -> r["key"] to preparation(r["preparation"], "preparation-overrides.csv ${r["key"]}") }
                },
                displayNames = table("display-names.csv").run {
                    requireColumns("key", "name", "reason")
                    rows.associate { r -> r["key"] to r["name"] }
                },
                aliases = table("aliases.csv").run {
                    requireColumns("key", "alias")
                    rows.groupBy({ r -> r["key"] }, { r -> r["alias"] })
                },
                sourceCorrections = table("source-corrections.csv").run {
                    requireColumns("source", "code", "field", "value", "reason")
                    rows.map { r -> SourceCorrection(r["source"], r["code"], r["field"], r["value"], r["reason"]) }
                },
                tacoPdfDeviations = table("taco-pdf-deviations.csv").run {
                    requireColumns("number", "field", "workbook", "pdf", "decision", "reason")
                    rows.map { r -> PdfDeviation(r["number"].toInt(), r["field"], r["workbook"], r["pdf"], r["decision"], r["reason"]) }
                },
                tacoAlcohol = table("taco-alcohol.csv").run {
                    requireColumns("number", "alcohol_g", "reference")
                    rows.associate { r -> r["number"].toInt() to BigDecimal(r["alcohol_g"].replace(',', '.')) }
                },
                valueExclusions = table("value-exclusions.csv").run {
                    requireColumns("key", "nutrient", "note", "reason")
                    rows.map { r -> ValueExclusion(r["key"], Nutrient.fromKey(r["nutrient"]), r["note"], r["reason"]) }
                },
                usdaFoods = table("usda-foods.csv").run {
                    requireColumns("fdc_id", "name", "base_name", "category", "preparation", "note")
                    rows.map { r ->
                        UsdaSelection(
                            r["fdc_id"].toInt(), r["name"], r["base_name"].ifEmpty { null },
                            category(r["category"], "usda-foods.csv"), preparation(r["preparation"], "usda-foods.csv"), r["note"],
                        )
                    }
                },
                goldenValues = table("golden-values.csv").run {
                    requireColumns("key", "nutrient", "status", "value", "match", "reference")
                    rows.map { r ->
                        require(r["match"] in setOf("EXACT", "SIG3")) { "golden-values.csv: match inválido '${r["match"]}'" }
                        GoldenValue(
                            r["key"], Nutrient.fromKey(r["nutrient"]), r["status"],
                            r["value"].ifEmpty { null }?.replace(',', '.')?.let(::BigDecimal), r["match"], r["reference"],
                        )
                    }
                },
                requestedFoods = table("requested-foods.csv").run {
                    requireColumns("request", "category", "keys", "status", "note")
                    rows.map { r ->
                        RequestedFood(r["request"], r["category"], r["keys"].split(';').map { it.trim() }.filter { it.isNotEmpty() }, r["status"], r["note"])
                    }
                },
            )
        }
    }
}
