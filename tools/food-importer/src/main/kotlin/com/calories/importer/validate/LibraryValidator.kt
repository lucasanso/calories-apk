package com.calories.importer.validate

import com.calories.domain.nutrition.NutritionRules
import com.calories.importer.assemble.AssembledFood
import com.calories.importer.assemble.get
import com.calories.importer.curation.Curation
import com.calories.importer.taco.TacoNormalizer
import com.calories.importer.taco.TacoPdfCrossCheck
import com.calories.library.format.AssetNutrient
import java.math.BigDecimal

enum class Severity { ERROR, WARNING }

data class Issue(val code: String, val severity: Severity, val key: String?, val message: String)

data class ConsistencyStats(val code: String, val checked: Int, val maxDeviation: BigDecimal)

/**
 * Regras de validação da biblioteca (docs/03-biblioteca-nutricional.md §3.7).
 * Erros impedem a geração do asset; avisos vão para o relatório. Nenhum valor é corrigido.
 */
class LibraryValidator(private val curation: Curation) {

    companion object {
        /**
         * kcal × 4,184 deve reproduzir o kJ da planilha (mesmo cálculo da TACO). Diferenças grandes indicam
         * coluna trocada (erro); diferenças pequenas existem na própria fonte (aviso; kcal usada como publicada).
         */
        val KJ_ERROR = BigDecimal("5")
        val KJ_WARNING = BigDecimal("0.01")
        /** Soma centesimal: o carboidrato é por diferença, então a soma fecha em 100 g. */
        val PROXIMATE_TOLERANCE = BigDecimal("0.5")
        val PROXIMATE_TOLERANCE_USDA = BigDecimal("0.5")
        private val KCAL_TO_KJ = BigDecimal("4.184")
        private val HUNDRED = BigDecimal(100)
        val REQUEST_STATUSES = setOf("OK", "USDA", "LABEL", "GENERIC")

        /** EXACT: igualdade numérica. SIG3: |gerado − referência| ≤ meia unidade do 3º algarismo significativo da referência. */
        fun goldenMatches(generated: BigDecimal, golden: BigDecimal, match: String): Boolean {
            if (match == "EXACT" || golden.signum() == 0) return generated.compareTo(golden) == 0
            val exponent = golden.precision() - golden.scale() - 1
            val halfUnit = BigDecimal.ONE.scaleByPowerOfTen(exponent - 2).divide(BigDecimal(2))
            return (generated - golden).abs() <= halfUnit
        }
    }

    val stats = ArrayList<ConsistencyStats>()

    fun validate(foods: List<AssembledFood>, crossCheck: TacoPdfCrossCheck.Result): List<Issue> = buildList {
        // E1: chaves únicas.
        foods.groupBy { it.asset.key }.filterValues { it.size > 1 }.keys.forEach {
            add(Issue("E1", Severity.ERROR, it, "Chave duplicada"))
        }
        val byKey = foods.associateBy { it.asset.key }

        // E4/E5/W1/W2: plausibilidade.
        for (food in foods) {
            for (f in NutritionRules.check(food.source.profile)) {
                add(Issue(f.code, if (f.severity == NutritionRules.Severity.ERROR) Severity.ERROR else Severity.WARNING, food.asset.key, f.message))
            }
        }

        // E8: kcal ↔ kJ (TACO).
        var kjChecked = 0
        var kjMax = BigDecimal.ZERO
        for (food in foods.filter { it.source.source == TacoNormalizer.SOURCE }) {
            val c = food.source.consistency
            val kcal = c.energyKcal ?: continue
            val kj = c.energyKj ?: continue
            val diff = (kcal * KCAL_TO_KJ - kj).abs()
            kjChecked++; kjMax = kjMax.max(diff)
            val detail = "kcal×4,184 = ${(kcal * KCAL_TO_KJ).setScale(2, java.math.RoundingMode.HALF_UP)} kJ, planilha traz ${kj.setScale(2, java.math.RoundingMode.HALF_UP)} kJ"
            when {
                diff > KJ_ERROR -> add(Issue("E8", Severity.ERROR, food.asset.key, detail))
                diff > KJ_WARNING -> add(Issue("W5", Severity.WARNING, food.asset.key, "Pequena inconsistência kcal/kJ na própria TACO ($detail); o app usa as kcal publicadas."))
            }
        }
        stats += ConsistencyStats("E8 kcal↔kJ (TACO)", kjChecked, kjMax.stripTrailingZeros())

        // E9: umidade + proteína + lipídios + carboidrato + cinzas (+ álcool) = 100 g.
        // Só se aplica quando o carboidrato foi de fato calculado por diferença (> 0): em carnes,
        // peixes e ovos as fontes publicam 0 ou NA, e a soma não fecha por construção.
        for (source in listOf(TacoNormalizer.SOURCE, "USDA_SR")) {
            var checked = 0
            var max = BigDecimal.ZERO
            val tolerance = if (source == TacoNormalizer.SOURCE) PROXIMATE_TOLERANCE else PROXIMATE_TOLERANCE_USDA
            for (food in foods.filter { it.source.source == source }) {
                val c = food.source.consistency
                val parts = listOf(c.moisture, c.protein, c.fat, c.carbohydrate, c.ash)
                if (parts.any { it == null } || c.carbohydrate!!.signum() <= 0) continue
                val sum = parts.filterNotNull().fold(c.alcohol ?: BigDecimal.ZERO, BigDecimal::add)
                val diff = (sum - HUNDRED).abs()
                checked++; max = max.max(diff)
                if (diff > tolerance) add(Issue("E9", Severity.ERROR, food.asset.key, "Soma centesimal = $sum g (esperado 100 ± $tolerance)"))
            }
            stats += ConsistencyStats("E9 soma centesimal ($source)", checked, max.stripTrailingZeros())
        }

        // E7: valores de ouro conferidos manualmente na fonte.
        for (g in curation.goldenValues) {
            val food = byKey[g.key]
            if (food == null) { add(Issue("E7", Severity.ERROR, g.key, "Valor de ouro para chave inexistente")); continue }
            val actual = food.asset.nutrients.get(g.nutrient)
            val ok = actual.status == g.status &&
                (g.status != AssetNutrient.MEASURED || (actual.value != null && goldenMatches(BigDecimal(actual.value.toString()), g.value!!, g.match)))
            if (!ok) {
                add(Issue("E7", Severity.ERROR, g.key, "${g.nutrient.key}: gerado ${actual.status} ${actual.value ?: ""}, esperado ${g.status} ${g.value ?: ""} (${g.reference})"))
            }
        }

        // Checagem cruzada planilha × PDF: toda divergência precisa estar documentada.
        val documented = curation.tacoPdfDeviations.map { it.number to it.field }.toSet()
        val observed = HashSet<Pair<Int, String>>()
        for (m in crossCheck.mismatches) {
            observed += m.number to m.field
            if ((m.number to m.field) !in documented) {
                add(Issue("E10", Severity.ERROR, "TACO4:${m.number}", "${m.field}: planilha ${m.expected} ≠ PDF ${m.pdf} (p. ${m.page})"))
            }
        }
        for (d in crossCheck.descriptionDiffs) {
            observed += d.number to "description"
            if ((d.number to "description") !in documented) {
                add(Issue("E10", Severity.ERROR, "TACO4:${d.number}", "Descrição: planilha '${d.workbook}' ≠ PDF '${d.pdf}' (p. ${d.page})"))
            }
        }
        for (dev in curation.tacoPdfDeviations) {
            if ((dev.number to dev.field) !in observed) {
                add(Issue("E11", Severity.ERROR, "TACO4:${dev.number}", "Divergência documentada em taco-pdf-deviations.csv não foi observada (curadoria desatualizada)"))
            }
        }

        // Curadoria que aponta para chaves inexistentes (E11).
        val keys = byKey.keys
        val referenced = curation.categoryOverrides.keys + curation.preparationOverrides.keys +
            curation.displayNames.keys + curation.aliases.keys + curation.valueExclusions.map { it.key }
        (referenced - keys).sorted().forEach { add(Issue("E11", Severity.ERROR, it, "Chave usada na curadoria não existe na biblioteca")) }

        // Cobertura da lista inicial do requisito.
        for (r in curation.requestedFoods) {
            if (r.status !in REQUEST_STATUSES) add(Issue("E11", Severity.ERROR, null, "requested-foods.csv: status '${r.status}' inválido (${r.request})"))
            if (r.status in setOf("OK", "USDA", "GENERIC") && r.keys.isEmpty()) add(Issue("E11", Severity.ERROR, null, "requested-foods.csv: '${r.request}' sem chaves"))
            r.keys.filter { it !in keys }.forEach { add(Issue("E11", Severity.ERROR, it, "requested-foods.csv: '${r.request}' aponta para chave inexistente")) }
        }

        // E13: na busca, variações com o mesmo nome base são escolhidas pelo preparo (Cru · Cozido · …);
        // dois itens com o mesmo nome base e o mesmo preparo tornariam a escolha ambígua.
        foods.groupBy { it.asset.baseName to it.asset.preparation }.filterValues { it.size > 1 }.forEach { (group, list) ->
            add(Issue("E13", Severity.ERROR, list.first().asset.key, "Nome base '${group.first}' com preparo ${group.second} repetido em ${list.map { it.asset.key }}"))
        }
    }
}
