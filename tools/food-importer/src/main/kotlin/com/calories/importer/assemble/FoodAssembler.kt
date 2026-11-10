package com.calories.importer.assemble

import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.Preparation
import com.calories.importer.classify.PreparationClassifier
import com.calories.importer.curation.Curation
import com.calories.importer.model.SourceFood
import com.calories.library.format.AssetFood
import com.calories.library.format.AssetNutrient
import com.calories.library.format.AssetNutrients
import com.calories.library.format.AssetReference

/** Alimento final do asset, mantendo o [source] normalizado para validações e relatório. */
data class AssembledFood(val asset: AssetFood, val source: SourceFood, val preparationOrigin: PreparationOrigin)

enum class PreparationOrigin { OVERRIDE, DESCRIPTION, GROUP_DEFAULT, CURATED_SELECTION }

/** Aplica as decisões de curadoria (nome, categoria, preparo, sinônimos) a um [SourceFood]. */
class FoodAssembler(private val curation: Curation) {

    companion object {
        const val NOTE_INTERNATIONAL =
            "Referência internacional (USDA, EUA): a composição pode diferir de produtos vendidos no Brasil. " +
                "Para produtos de marca, prefira cadastrar os valores do rótulo."
    }

    /** Aplica `value-exclusions.csv`: o nutriente vira "sem dado" e a observação vai para o app. */
    fun applyExclusions(food: SourceFood): SourceFood {
        val exclusions = curation.valueExclusions.filter { it.key == food.key }
        if (exclusions.isEmpty()) return food
        val nutrients = food.nutrients.toMutableMap()
        exclusions.forEach { nutrients[it.nutrient] = NutrientValue.NotAvailable }
        val dropped = setOf(com.calories.importer.taco.TacoNormalizer.NOTE_NO_FATTY_ACIDS)
        return food.copy(nutrients = nutrients, notes = food.notes.filterNot { it in dropped } + exclusions.map { it.note })
    }

    /** Resultado ou mensagem de erro (E6) quando falta decisão de curadoria. */
    fun assembleTaco(original: SourceFood): Result<AssembledFood> = runCatching {
        val food = applyExclusions(original)
        val key = food.key
        val (preparation, origin) = curation.preparationOverrides[key]?.let { it to PreparationOrigin.OVERRIDE }
            ?: PreparationClassifier.detect(food.description)?.let { it.preparation to PreparationOrigin.DESCRIPTION }
            ?: curation.tacoGroupPreparation[food.group]?.let { it to PreparationOrigin.GROUP_DEFAULT }
            ?: throw IllegalStateException("E6 $key '${food.description}': preparo não identificado; registre em preparation-overrides.csv")
        val category = curation.categoryOverrides[key]
            ?: curation.tacoGroupCategory[food.group]
            ?: throw IllegalStateException("E6 $key: grupo '${food.group}' sem categoria em taco-groups.csv")
        val name = curation.displayNames[key] ?: food.description
        AssembledFood(
            asset = toAsset(food, name, PreparationClassifier.baseName(name), category, preparation, food.notes),
            source = food,
            preparationOrigin = origin,
        )
    }

    fun assembleUsda(original: SourceFood, selection: Curation.UsdaSelection): AssembledFood {
        val food = applyExclusions(original)
        val notes = buildList {
            addAll(food.notes)
            if (selection.note.isNotEmpty()) add(selection.note)
            add(NOTE_INTERNATIONAL)
        }
        return AssembledFood(
            asset = toAsset(food, selection.name, selection.baseName ?: selection.name, selection.category, selection.preparation, notes),
            source = food,
            preparationOrigin = PreparationOrigin.CURATED_SELECTION,
        )
    }

    private fun toAsset(
        food: SourceFood,
        name: String,
        baseName: String,
        category: String,
        preparation: Preparation,
        notes: List<String>,
    ) = AssetFood(
        key = food.key,
        source = food.source,
        sourceCode = food.code,
        sourceDescription = food.description,
        sourceGroup = food.group,
        name = name,
        baseName = baseName,
        aliases = curation.aliases[food.key].orEmpty().map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted(),
        category = category,
        preparation = preparation.name,
        reference = AssetReference(food.referenceAmount, food.referenceUnit.name),
        nutrients = AssetNutrients(
            energyKcal = food.nutrients.asset(Nutrient.ENERGY),
            proteinG = food.nutrients.asset(Nutrient.PROTEIN),
            carbohydrateG = food.nutrients.asset(Nutrient.CARBOHYDRATE),
            fatG = food.nutrients.asset(Nutrient.FAT),
            saturatedFatG = food.nutrients.asset(Nutrient.SATURATED_FAT),
            fiberG = food.nutrients.asset(Nutrient.FIBER),
            sodiumMg = food.nutrients.asset(Nutrient.SODIUM),
        ),
        notes = notes,
    )

    private fun Map<Nutrient, NutrientValue>.asset(nutrient: Nutrient): AssetNutrient =
        when (val v = this[nutrient] ?: NutrientValue.NotAvailable) {
            is NutrientValue.Measured -> AssetNutrient.measured(v.amount)
            NutrientValue.Trace -> AssetNutrient.trace
            NutrientValue.NotApplicable -> AssetNutrient.notApplicable
            NutrientValue.NotAvailable -> AssetNutrient.notAvailable
        }
}

/** Converte de volta um nutriente do asset para o domínio (usado nas validações). */
fun AssetNutrient.toDomain(): NutrientValue = when (status) {
    AssetNutrient.MEASURED -> NutrientValue.Measured(value!!)
    AssetNutrient.TRACE -> NutrientValue.Trace
    AssetNutrient.NOT_APPLICABLE -> NutrientValue.NotApplicable
    else -> NutrientValue.NotAvailable
}

fun AssetNutrients.get(nutrient: Nutrient): AssetNutrient = when (nutrient) {
    Nutrient.ENERGY -> energyKcal
    Nutrient.PROTEIN -> proteinG
    Nutrient.CARBOHYDRATE -> carbohydrateG
    Nutrient.FAT -> fatG
    Nutrient.SATURATED_FAT -> saturatedFatG
    Nutrient.FIBER -> fiberG
    Nutrient.SODIUM -> sodiumMg
}
