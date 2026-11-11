package com.calories.ui.format

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientTotal
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formatação pt-BR. O arredondamento acontece SÓ aqui (docs/04 §4.5): HALF_UP sobre a
 * representação decimal mais curta do Double (247,5 → "248" no resumo, "247,5" no detalhe).
 */
object Fmt {
    val locale: Locale = Locale.forLanguageTag("pt-BR")
    private val symbols = DecimalFormatSymbols(locale)

    fun number(value: Double, decimals: Int, trimZeros: Boolean = false): String {
        val rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP)
        val pattern = if (decimals == 0) "#,##0" else "#,##0." + (if (trimZeros) "#" else "0").repeat(decimals)
        return DecimalFormat(pattern, symbols).apply { roundingMode = RoundingMode.HALF_UP }.format(rounded)
    }

    /** Casas decimais por nutriente: resumo (kcal inteiro) ou detalhe (kcal com 1 casa). */
    fun decimals(nutrient: Nutrient, detail: Boolean): Int = when (nutrient) {
        Nutrient.ENERGY -> if (detail) 1 else 0
        Nutrient.SODIUM -> 0
        else -> 1
    }

    fun nutrient(nutrient: Nutrient, value: Double, detail: Boolean = false, withUnit: Boolean = true): String {
        val text = number(value, decimals(nutrient, detail), trimZeros = detail && nutrient == Nutrient.ENERGY)
        return if (withUnit) "$text ${nutrient.unit}" else text
    }

    fun value(nutrient: Nutrient, value: NutrientValue, detail: Boolean = true, withUnit: Boolean = true): String = when (value) {
        is NutrientValue.Measured -> nutrient(nutrient, value.amount, detail, withUnit)
        NutrientValue.Trace -> "Tr"
        NutrientValue.NotApplicable -> "NA"
        NutrientValue.NotAvailable -> "—"
    }

    /** Total com indicação de parcial ("12,4 g*"); "—" quando nenhum item tem o dado. */
    fun total(nutrient: Nutrient, total: NutrientTotal, withUnit: Boolean = true): String = when {
        total.itemCount == 0 -> nutrient(nutrient, 0.0, withUnit = withUnit)
        !total.hasAnyData -> "—"
        total.isComplete -> nutrient(nutrient, total.sum, withUnit = withUnit)
        else -> nutrient(nutrient, total.sum, withUnit = withUnit) + "*"
    }

    fun kcal(value: Double): String = number(value, 0)

    fun amount(value: Double, unit: MeasureUnit): String = "${number(value, 1, trimZeros = true)} ${unit.symbol}"

    fun percent(fraction: Double): String = "${number(fraction * 100, 0)}%"

    /** Aceita "150", "150,5", "150.5" e "1.250,5". */
    fun parse(text: String): Double? {
        val t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val normalized = if (t.contains(',')) t.replace(".", "").replace(',', '.') else t
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    /** Texto para preencher um campo numérico sem zeros supérfluos. */
    fun input(value: Double?): String = value?.let { number(it, 2, trimZeros = true).replace(".", "") } ?: ""

    private val weekdayDate = DateTimeFormatter.ofPattern("EEE, d 'de' MMM", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", locale)

    fun relativeDate(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        today.plusDays(1) -> "Amanhã"
        else -> weekdayDate.format(date).replaceFirstChar { it.titlecase(locale) }
    }

    fun fullDate(date: LocalDate): String = fullDate.format(date)

    private val shortDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM 'de' yyyy", locale)

    /** "11/11/2026". */
    fun shortDate(date: LocalDate): String = shortDate.format(date)

    /** "11/11". */
    fun dayMonth(date: LocalDate): String = "%02d/%02d".format(date.dayOfMonth, date.monthValue)

    /** "Novembro de 2026". */
    fun monthYear(month: YearMonth): String = monthYear.format(month.atDay(1)).replaceFirstChar { it.titlecase(locale) }

    /** "seg", "ter"… */
    fun weekdayShort(day: DayOfWeek): String = day.getDisplayName(TextStyle.SHORT, locale).removeSuffix(".").lowercase(locale)

    /** "9 a 15 de nov." ou "28 de out. a 3 de nov."; o ano aparece quando não é o de [today]. */
    fun period(from: LocalDate, to: LocalDate, today: LocalDate): String {
        fun month(d: LocalDate) = d.month.getDisplayName(TextStyle.SHORT, locale).lowercase(locale).let { if (it.endsWith(".")) it else "$it." }
        val year = if (to.year != today.year || from.year != today.year) " de ${to.year}" else ""
        return if (from.month == to.month && from.year == to.year) "${from.dayOfMonth} a ${to.dayOfMonth} de ${month(to)}$year"
        else "${from.dayOfMonth} de ${month(from)} a ${to.dayOfMonth} de ${month(to)}$year"
    }

    /** Quantidade de lista de compras ("400 g", "1,5 kg"); sem quantidade, vazio. */
    fun quantity(quantity: Double?, unit: String?): String =
        quantity?.let { listOfNotNull(number(it, 1, trimZeros = true), unit).joinToString(" ") } ?: ""

    fun time(minutes: Int?): String = minutes?.let { "%02d:%02d".format(it / 60, it % 60) } ?: ""

    fun preparation(p: Preparation): String = p.labelPt

    fun sourceLabel(code: String): String = when (code) {
        "TACO4" -> "TACO"
        "USDA_SR" -> "USDA"
        "USER" -> "Meu"
        else -> code
    }
}
