package com.calories.importer.report

import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.Preparation
import com.calories.importer.SourcesLock
import com.calories.importer.assemble.AssembledFood
import com.calories.importer.assemble.get
import com.calories.importer.curation.Curation
import com.calories.importer.taco.TacoPdfCrossCheck
import com.calories.importer.validate.ConsistencyStats
import com.calories.importer.validate.Issue
import com.calories.importer.validate.Severity
import com.calories.library.format.AssetFood
import com.calories.library.format.AssetNutrient
import com.calories.library.format.LibraryAsset

/** Relatório Markdown determinístico (sem data/hora) de uma execução do importador. */
class ImportReport(
    private val lock: SourcesLock,
    private val curation: Curation,
    private val asset: LibraryAsset,
    private val assetSha256: String,
    private val foods: List<AssembledFood>,
    private val crossCheck: TacoPdfCrossCheck.Result,
    private val issues: List<Issue>,
    private val consistency: List<ConsistencyStats>,
    private val previous: LibraryAsset?,
) {
    fun render(): String = buildString {
        val errors = issues.filter { it.severity == Severity.ERROR }
        val warnings = issues.filter { it.severity == Severity.WARNING }
        val categoryNames = curation.categories.associate { it.code to it.name }

        appendLine("# Relatório de importação da biblioteca nutricional")
        appendLine()
        appendLine("> Gerado por `./gradlew :tools:food-importer:generateFoodLibrary`. Não editar à mão.")
        appendLine()
        appendLine("| Item | Valor |")
        appendLine("|---|---|")
        appendLine("| Versão da biblioteca | ${asset.libraryVersion} |")
        appendLine("| Formato do asset | ${asset.formatVersion} |")
        appendLine("| Alimentos | ${asset.foods.size} |")
        appendLine("| SHA-256 do asset | `$assetSha256` |")
        appendLine("| Erros | ${errors.size} |")
        appendLine("| Avisos | ${warnings.size} |")
        appendLine()

        appendLine("## Fontes e arquivos")
        appendLine()
        for (s in lock.sources) {
            appendLine("**${s.meta.code}** — ${s.meta.name}${s.meta.edition?.let { " ($it)" } ?: ""}. Licença: ${s.meta.license.trimEnd('.')}. Acesso: ${s.meta.accessedAt ?: "—"}.")
            appendLine()
            for (f in s.files) appendLine("- `${f.path}` (${f.role}) — SHA-256 `${f.sha256}`")
            appendLine()
        }

        appendLine("## Composição da biblioteca")
        appendLine()
        appendLine("| Fonte | Alimentos |")
        appendLine("|---|---|")
        asset.foods.groupingBy { it.source }.eachCount().forEach { (k, v) -> appendLine("| $k | $v |") }
        appendLine()
        appendLine("| Categoria | Alimentos |")
        appendLine("|---|---|")
        curation.categories.sortedBy { it.sortOrder }.forEach { c -> appendLine("| ${c.name} | ${asset.foods.count { it.category == c.code }} |") }
        appendLine()
        appendLine("| Preparo | Alimentos | Origem da classificação |")
        appendLine("|---|---|---|")
        Preparation.entries.forEach { p ->
            val group = foods.filter { it.asset.preparation == p.name }
            if (group.isNotEmpty()) {
                val origins = group.groupingBy { it.preparationOrigin.name.lowercase() }.eachCount().toSortedMap().entries.joinToString(", ") { "${it.key}: ${it.value}" }
                appendLine("| ${p.labelPt} | ${group.size} | $origins |")
            }
        }
        appendLine()

        appendLine("## Status dos nutrientes")
        appendLine()
        appendLine("| Nutriente | Medido | Traço (Tr) | Não aplicável (NA) | Sem dado |")
        appendLine("|---|---|---|---|---|")
        Nutrient.entries.forEach { n ->
            val statuses = asset.foods.groupingBy { it.nutrients.get(n).status }.eachCount()
            appendLine("| ${n.labelPt} | ${statuses[AssetNutrient.MEASURED] ?: 0} | ${statuses[AssetNutrient.TRACE] ?: 0} | ${statuses[AssetNutrient.NOT_APPLICABLE] ?: 0} | ${statuses[AssetNutrient.NOT_AVAILABLE] ?: 0} |")
        }
        appendLine()

        appendLine("## Checagem cruzada TACO: planilha × PDF oficial")
        appendLine()
        appendLine("Valores publicados derivados da planilha comparados com o texto do PDF (mesma edição, ambos do NEPA/UNICAMP).")
        appendLine()
        appendLine("| Linhas encontradas no PDF | Quantidade |")
        appendLine("|---|---|")
        crossCheck.rowsFound.forEach { (k, v) -> appendLine("| $k | $v |") }
        appendLine()
        appendLine("| Campo | Valores comparados |")
        appendLine("|---|---|")
        crossCheck.comparedValues.toSortedMap().forEach { (k, v) -> appendLine("| $k | $v |") }
        appendLine("| **Total** | **${crossCheck.totalCompared}** |")
        appendLine()
        appendLine("Divergências de valor: **${crossCheck.mismatches.size}**. Divergências de descrição: **${crossCheck.descriptionDiffs.size}** (todas precisam estar em `curation/taco-pdf-deviations.csv`).")
        appendLine()
        if (crossCheck.mismatches.isNotEmpty() || crossCheck.descriptionDiffs.isNotEmpty()) {
            appendLine("| Item | Campo | Planilha | PDF | Página |")
            appendLine("|---|---|---|---|---|")
            crossCheck.mismatches.forEach { appendLine("| ${it.number} | ${it.field} | ${it.expected} | ${it.pdf} | ${it.page} |") }
            crossCheck.descriptionDiffs.forEach { appendLine("| ${it.number} | descrição | ${it.workbook} | ${it.pdf} | ${it.page} |") }
            appendLine()
        }
        if (crossCheck.uncomparable.isNotEmpty()) {
            appendLine("<details><summary>Linhas não comparáveis automaticamente: ${crossCheck.uncomparable.size}</summary>")
            appendLine()
            appendLine("| Item | Bloco | Motivo | Página |")
            appendLine("|---|---|---|---|")
            crossCheck.uncomparable.forEach { appendLine("| ${it.number} | ${it.block} | ${it.reason} | ${it.page} |") }
            appendLine()
            appendLine("</details>")
            appendLine()
        }

        appendLine("## Verificações de consistência")
        appendLine()
        appendLine("| Verificação | Itens verificados | Maior desvio |")
        appendLine("|---|---|---|")
        consistency.forEach { appendLine("| ${it.code} | ${it.checked} | ${it.maxDeviation.toPlainString()} |") }
        appendLine("| E7 valores de ouro | ${curation.goldenValues.size} | — |")
        appendLine()

        appendLine("## Cobertura da lista inicial do requisito")
        appendLine()
        appendLine("| Pedido | Categoria | Status | Alimentos na biblioteca | Observação |")
        appendLine("|---|---|---|---|---|")
        val byKey = asset.foods.associateBy { it.key }
        curation.requestedFoods.forEach { r ->
            val names = r.keys.joinToString("<br>") { k -> byKey[k]?.let { "`$k` ${it.name}" } ?: "`$k` (ausente)" }
            appendLine("| ${r.request} | ${categoryNames[r.category] ?: r.category} | ${statusLabel(r.status)} | ${names.ifEmpty { "—" }} | ${r.note} |")
        }
        appendLine()

        appendLine("## Erros")
        appendLine()
        if (errors.isEmpty()) appendLine("Nenhum.") else errors.forEach { appendLine("- **${it.code}** ${it.key ?: ""} — ${it.message}") }
        appendLine()
        appendLine("## Avisos")
        appendLine()
        appendLine("Avisos não bloqueiam e **não alteram valores**: os dados são mantidos como publicados.")
        appendLine()
        warnings.groupBy { it.code }.toSortedMap().forEach { (code, list) ->
            appendLine("<details><summary>$code — ${list.size} itens</summary>")
            appendLine()
            list.forEach { w -> appendLine("- ${w.key} ${byKey[w.key]?.name ?: ""}: ${w.message}") }
            appendLine()
            appendLine("</details>")
            appendLine()
        }

        appendLine("## Diferenças em relação ao asset anterior")
        appendLine()
        if (previous == null) {
            appendLine("Primeira geração (não havia asset anterior).")
        } else {
            val old = previous.foods.associateBy { it.key }
            val added = asset.foods.filter { it.key !in old }
            val removed = previous.foods.filter { it.key !in byKey }
            val changed = asset.foods.filter { f -> old[f.key]?.let { it != f } == true }
            appendLine("Versão anterior: ${previous.libraryVersion}. Adicionados: ${added.size}. Removidos: ${removed.size}. Alterados: ${changed.size}.")
            appendLine()
            (added.map { "+ ${it.key} ${it.name}" } + removed.map { "- ${it.key} ${it.name}" } + changed.map { "~ ${it.key} ${describeChange(old.getValue(it.key), it)}" })
                .forEach { appendLine("- $it") }
        }
    }

    private fun statusLabel(status: String) = when (status) {
        "OK" -> "✅ TACO"
        "USDA" -> "🌎 USDA (lacuna na TACO)"
        "LABEL" -> "🏷️ cadastrar pelo rótulo"
        "GENERIC" -> "🔷 genérico → itens específicos"
        else -> status
    }

    private fun describeChange(old: AssetFood, new: AssetFood): String {
        val fields = buildList {
            if (old.name != new.name) add("nome")
            if (old.category != new.category) add("categoria")
            if (old.preparation != new.preparation) add("preparo")
            if (old.nutrients != new.nutrients) add("nutrientes")
            if (old.aliases != new.aliases) add("sinônimos")
            if (old.notes != new.notes) add("observações")
        }
        return "${new.name}: ${fields.joinToString(", ").ifEmpty { "outros campos" }}"
    }
}
