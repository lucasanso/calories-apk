package com.calories.importer.taco

import com.calories.domain.nutrition.NutrientValue
import com.calories.importer.io.XlsxCell
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * Valor de uma célula da TACO convertido para o valor **publicado** (com as casas decimais do PDF).
 *
 * Símbolos, conforme as notas da Tabela 1 da TACO 4ª ed.:
 * - `Tr`: traço; `NA`: não aplicável; `*`: análise em reavaliação; vazio: análise não solicitada.
 */
sealed interface TacoValue {
    /** [raw] é o valor da planilha (sem arredondar); [published] é o arredondado como no PDF. */
    data class Number(val raw: BigDecimal, val published: BigDecimal) : TacoValue
    data object Trace : TacoValue
    data object NotApplicable : TacoValue
    data object UnderReview : TacoValue
    data object Blank : TacoValue

    /** Forma como o valor aparece no PDF (vírgula decimal), para a checagem cruzada. */
    fun pdfText(): String = when (this) {
        is Number -> published.toPlainString().replace('.', ',')
        Trace -> "Tr"
        NotApplicable -> "NA"
        UnderReview -> "*"
        Blank -> ""
    }

    fun toNutrientValue(): NutrientValue = when (this) {
        is Number -> NutrientValue.Measured(published.toDouble())
        Trace -> NutrientValue.Trace
        NotApplicable -> NutrientValue.NotApplicable
        UnderReview, Blank -> NutrientValue.NotAvailable
    }

    companion object {
        /**
         * Arredondamento usado para reproduzir o PDF: primeiro 12 algarismos significativos
         * (remove ruído de ponto flutuante da planilha, ex.: 0,6499999999999999 → 0,65),
         * depois HALF_UP nas casas publicadas. Verificado contra o PDF oficial na checagem cruzada.
         */
        fun publishedRound(raw: BigDecimal, decimals: Int): BigDecimal =
            raw.round(MathContext(12, RoundingMode.HALF_UP)).setScale(decimals, RoundingMode.HALF_UP)

        fun parse(cell: XlsxCell?, decimals: Int): TacoValue = when (cell) {
            null -> Blank
            is XlsxCell.Number -> number(BigDecimal(cell.raw), decimals)
            is XlsxCell.Text -> when (val text = cell.value.trim()) {
                "" -> Blank
                "Tr" -> Trace
                "NA" -> NotApplicable
                "*" -> UnderReview
                else -> text.replace(',', '.').toBigDecimalOrNull()?.let { number(it, decimals) }
                    ?: throw IllegalArgumentException("E3 símbolo desconhecido na TACO: '$text'")
            }
        }

        private fun number(raw: BigDecimal, decimals: Int): Number {
            var published = publishedRound(raw, decimals)
            // BigDecimal não tem "-0": um negativo que arredonda para zero vira 0 com sinal positivo.
            if (published.signum() == 0) published = published.abs()
            return Number(raw, published)
        }
    }
}
