package com.calories.importer

import com.calories.importer.assemble.AssembledFood
import com.calories.importer.assemble.FoodAssembler
import com.calories.importer.curation.Curation
import com.calories.importer.io.Hashing
import com.calories.importer.report.ImportReport
import com.calories.importer.taco.NormalizedTacoItem
import com.calories.importer.taco.TacoNormalizer
import com.calories.importer.taco.TacoPdfCrossCheck
import com.calories.importer.taco.TacoWorkbook
import com.calories.importer.usda.FdcSrLegacyReader
import com.calories.importer.validate.Issue
import com.calories.importer.validate.LibraryValidator
import com.calories.importer.validate.Severity
import com.calories.library.format.LibraryAsset
import com.calories.library.format.LibraryAssetCodec
import java.nio.file.Files
import java.nio.file.Path

/** Execução completa: integridade → leitura → normalização → curadoria → validação → asset. */
class ImportPipeline(private val dataDir: Path) {

    data class Output(
        val asset: LibraryAsset,
        val assetText: String,
        val reportText: String,
        val issues: List<Issue>,
        val foods: List<AssembledFood>,
        val tacoItems: List<NormalizedTacoItem>,
        val crossCheck: TacoPdfCrossCheck.Result,
    ) {
        val errors: List<Issue> get() = issues.filter { it.severity == Severity.ERROR }
    }

    class IntegrityException(val problems: List<String>) : Exception(problems.joinToString("\n"))

    fun run(previousAsset: LibraryAsset?): Output {
        val lock = SourcesLock.load(dataDir.resolve("sources.lock.json"))
        lock.verify(dataDir).takeIf { it.isNotEmpty() }?.let { throw IntegrityException(it) }
        val curation = Curation.load(dataDir.resolve("curation"))

        // TACO
        val tacoSource = lock.source(TacoNormalizer.SOURCE)
        val workbook = TacoWorkbook.read(dataDir.resolve(tacoSource.file("workbook").path))
        val normalizer = TacoNormalizer(curation.tacoDescriptionCorrections(), curation.tacoAlcohol)
        val tacoItems = workbook.items.map(normalizer::normalize)
        val crossCheck = TacoPdfCrossCheck(dataDir.resolve(tacoSource.file("pdf").path)).run(tacoItems)

        // USDA (somente lacunas curadas)
        val usdaSource = lock.source(FdcSrLegacyReader.SOURCE)
        val usdaReader = FdcSrLegacyReader(dataDir.resolve(usdaSource.file("csv-zip").path))
        val usdaFoods = usdaReader.read(curation.usdaFoods.map { it.fdcId }.toSet())

        val assembler = FoodAssembler(curation)
        val issues = ArrayList<Issue>()
        val foods = ArrayList<AssembledFood>()
        for (item in tacoItems) {
            assembler.assembleTaco(item.food)
                .onSuccess { foods += it }
                .onFailure { issues += Issue("E6", Severity.ERROR, item.food.key, it.message ?: it.toString()) }
        }
        for (selection in curation.usdaFoods) {
            foods += assembler.assembleUsda(usdaReader.toSourceFood(usdaFoods.getValue(selection.fdcId)), selection)
        }

        val sourceOrder = lock.sources.map { it.meta.code }
        foods.sortWith(compareBy({ sourceOrder.indexOf(it.asset.source) }, { it.asset.sourceCode.toInt() }))

        val validator = LibraryValidator(curation)
        issues += validator.validate(foods, crossCheck)

        val asset = LibraryAsset(
            formatVersion = LibraryAssetCodec.FORMAT_VERSION,
            libraryVersion = curation.libraryVersion,
            sources = lock.sources.map { it.meta },
            categories = curation.categories.sortedBy { it.sortOrder },
            foods = foods.map { it.asset },
        )
        if (previousAsset != null && previousAsset.libraryVersion == asset.libraryVersion &&
            (previousAsset.foods != asset.foods || previousAsset.sources != asset.sources || previousAsset.categories != asset.categories)
        ) {
            issues += Issue(
                "E12", Severity.ERROR, null,
                "O conteúdo da biblioteca mudou mas library-version.txt continua ${asset.libraryVersion}; incremente a versão para o app reimportar.",
            )
        }

        val assetText = LibraryAssetCodec.encode(asset)
        val report = ImportReport(
            lock, curation, asset, Hashing.sha256(assetText), foods, crossCheck, issues, validator.stats, previousAsset,
        ).render()
        return Output(asset, assetText, report, issues, foods, tacoItems, crossCheck)
    }
}
