package com.calories.importer.taco

import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.importer.model.ConsistencyInputs
import com.calories.importer.model.SourceFood
import com.calories.importer.model.normalizeSpaces
import java.math.BigDecimal

/** Item da TACO com os valores publicados de cada coluna e o [food] pronto para a curadoria. */
data class NormalizedTacoItem(
    val item: TacoItem,
    val values: Map<TacoField, TacoValue>,
    val food: SourceFood,
)

/**
 * Converte itens da planilha em [SourceFood] com os valores **publicados** pela TACO.
 *
 * @param descriptionCorrections correções documentadas em `curation/source-corrections.csv`.
 * @param alcoholGrams teores alcoólicos da nota †† da Tabela 1 (usados só na verificação E9).
 */
class TacoNormalizer(
    private val descriptionCorrections: Map<Int, String>,
    private val alcoholGrams: Map<Int, BigDecimal>,
) {
    companion object {
        const val SOURCE = "TACO4"

        private val NUTRIENT_FIELDS = mapOf(
            Nutrient.ENERGY to TacoField.ENERGY_KCAL,
            Nutrient.PROTEIN to TacoField.PROTEIN,
            Nutrient.CARBOHYDRATE to TacoField.CARBOHYDRATE,
            Nutrient.FAT to TacoField.FAT,
            Nutrient.SATURATED_FAT to TacoField.SATURATED,
            Nutrient.FIBER to TacoField.FIBER,
            Nutrient.SODIUM to TacoField.SODIUM,
        )

        const val NOTE_NEGATIVE_CARB =
            "Carboidrato calculado por diferença resultou levemente negativo na análise; a TACO publica 0,0 g."
        const val NOTE_SATURATED_TRACE =
            "A TACO não publica ácidos graxos deste item; como os lipídios totais são traço, a gordura saturada também é traço."
        const val NOTE_NO_FATTY_ACIDS = "A TACO não publica a composição de ácidos graxos deste item (gordura saturada sem dado)."
        const val NOTE_UNDER_REVIEW = "A TACO marca valores deste item com * (análises em reavaliação); esses nutrientes ficam sem dado."
    }

    fun normalize(item: TacoItem): NormalizedTacoItem {
        val values = TacoField.entries.associateWith { field ->
            val value = try {
                TacoValue.parse(item.cell(field), field.decimals)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("TACO item ${item.number}, ${field.label}: ${e.message}", e)
            }
            if (value is TacoValue.Number && value.raw.signum() < 0 && field != TacoField.CARBOHYDRATE) {
                throw IllegalArgumentException("E3 TACO item ${item.number}, ${field.label}: valor negativo ${value.raw}")
            }
            value
        }

        val notes = ArrayList<String>()
        val nutrients = NUTRIENT_FIELDS.mapValues { (_, field) -> values.getValue(field).toNutrientValue() }.toMutableMap()

        (values.getValue(TacoField.CARBOHYDRATE) as? TacoValue.Number)?.let {
            if (it.raw.signum() < 0) notes += NOTE_NEGATIVE_CARB
        }

        if (item.fattyAcids == null) {
            when (nutrients.getValue(Nutrient.FAT)) {
                NutrientValue.Trace -> { nutrients[Nutrient.SATURATED_FAT] = NutrientValue.Trace; notes += NOTE_SATURATED_TRACE }
                NutrientValue.NotApplicable -> nutrients[Nutrient.SATURATED_FAT] = NutrientValue.NotApplicable
                else -> notes += NOTE_NO_FATTY_ACIDS
            }
        }
        if (NUTRIENT_FIELDS.values.any { values[it] == TacoValue.UnderReview }) notes += NOTE_UNDER_REVIEW

        fun raw(field: TacoField) = (values.getValue(field) as? TacoValue.Number)?.raw
        val food = SourceFood(
            source = SOURCE,
            code = item.number.toString(),
            description = descriptionCorrections[item.number] ?: normalizeSpaces(item.rawDescription),
            group = normalizeSpaces(item.group),
            referenceAmount = 100.0,
            referenceUnit = MeasureUnit.GRAM,
            nutrients = nutrients,
            notes = notes,
            consistency = ConsistencyInputs(
                energyKcal = raw(TacoField.ENERGY_KCAL),
                energyKj = raw(TacoField.ENERGY_KJ),
                moisture = raw(TacoField.MOISTURE),
                protein = raw(TacoField.PROTEIN),
                fat = raw(TacoField.FAT),
                carbohydrate = raw(TacoField.CARBOHYDRATE),
                ash = raw(TacoField.ASH),
                alcohol = alcoholGrams[item.number],
            ),
        )
        return NormalizedTacoItem(item, values, food)
    }
}
