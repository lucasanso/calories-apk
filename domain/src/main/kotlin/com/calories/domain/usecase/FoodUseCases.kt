package com.calories.domain.usecase

import com.calories.domain.model.CustomFoodDraft
import com.calories.domain.model.FoodOrigin
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientValue
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.domain.nutrition.NutritionRules
import com.calories.domain.repository.FoodRepository
import java.time.Clock
import javax.inject.Inject

data class FoodValidation(val errors: List<String>, val warnings: List<String>) {
    val isValid: Boolean get() = errors.isEmpty()
}

/** Validação do cadastro de alimento personalizado (mesmas regras usadas na importação da biblioteca). */
object CustomFoodValidator {
    val REQUIRED = listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT)
    const val MAX_NAME = 80

    fun validate(draft: CustomFoodDraft): FoodValidation {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        if (draft.name.isBlank()) errors += "Informe o nome."
        if (draft.name.trim().length > MAX_NAME) errors += "Nome com no máximo $MAX_NAME caracteres."
        if (!(draft.referenceAmount.isFinite() && draft.referenceAmount > 0.0 && draft.referenceAmount <= NutritionCalculator.MAX_AMOUNT)) {
            errors += "Quantidade de referência deve ser maior que zero."
        }
        for (n in REQUIRED) if (draft.nutrients[n] == null) errors += "${n.labelPt}: obrigatório."
        for ((n, v) in draft.nutrients) if (v != null && (!v.isFinite() || v < 0.0)) errors += "${n.labelPt}: valor inválido."
        if (errors.isEmpty()) {
            for (f in NutritionRules.check(toProfile(draft))) {
                if (f.severity == NutritionRules.Severity.ERROR) errors += f.message else warnings += f.message
            }
        }
        return FoodValidation(errors, warnings)
    }

    fun toProfile(draft: CustomFoodDraft) = NutrientProfile(
        draft.referenceAmount, draft.referenceUnit,
        draft.nutrients.mapNotNull { (n, v) -> v?.let { n to NutrientValue.Measured(it) } }.toMap(),
    )
}

class SaveCustomFood @Inject constructor(private val foods: FoodRepository, private val clock: Clock) {
    /** Cria ([id] nulo) ou atualiza um alimento personalizado. */
    suspend operator fun invoke(id: Long?, draft: CustomFoodDraft): Long {
        val validation = CustomFoodValidator.validate(draft)
        if (!validation.isValid) throw DomainException(validation.errors.joinToString("\n"))
        val clean = draft.copy(name = draft.name.trim(), brand = draft.brand?.trim()?.ifEmpty { null })
        return if (id == null) {
            foods.createCustom(clean, clock.instant())
        } else {
            val existing = foods.get(id) ?: throw DomainException("Alimento não encontrado")
            if (existing.origin != FoodOrigin.CUSTOM) throw DomainException("Alimentos da biblioteca não podem ser editados; duplique como personalizado.")
            foods.updateCustom(id, clean, clock.instant())
            id
        }
    }
}

/** Cria um alimento personalizado a partir de outro (ex.: da biblioteca), sem alterar o original. */
class DuplicateAsCustomFood @Inject constructor(private val foods: FoodRepository, private val clock: Clock) {
    suspend operator fun invoke(foodId: Long): Long {
        val food = foods.get(foodId) ?: throw DomainException("Alimento não encontrado")
        val draft = CustomFoodDraft(
            name = "${food.name} (meu)".take(CustomFoodValidator.MAX_NAME),
            brand = food.brand,
            categoryId = food.categoryId,
            preparation = food.preparation,
            referenceAmount = food.profile.referenceAmount,
            referenceUnit = food.profile.referenceUnit,
            // Traço e "não aplicável" viram 0; "sem dado" continua sem dado.
            nutrients = Nutrient.entries.associateWith { food.profile[it].let { v -> if (v is NutrientValue.NotAvailable) null else (v as? NutrientValue.Measured)?.amount ?: 0.0 } },
            derivedFromFoodId = food.id,
        )
        return foods.createCustom(draft, clock.instant())
    }
}

class DeleteCustomFood @Inject constructor(private val foods: FoodRepository) {
    suspend operator fun invoke(foodId: Long): Boolean {
        val food = foods.get(foodId) ?: return false
        if (food.origin != FoodOrigin.CUSTOM) throw DomainException("Alimentos da biblioteca não podem ser excluídos.")
        return foods.deleteCustom(foodId)
    }
}
