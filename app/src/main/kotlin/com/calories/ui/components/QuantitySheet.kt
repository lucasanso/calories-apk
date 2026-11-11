package com.calories.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutritionCalculator
import com.calories.ui.format.Fmt

/**
 * Folha para informar a quantidade, com prévia ao vivo de energia e macronutrientes.
 * Usada ao adicionar alimento e ao editar um lançamento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuantitySheet(
    title: String,
    subtitle: String?,
    profile: NutrientProfile,
    initialAmount: Double?,
    confirmLabel: String,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
    extraActions: (@Composable () -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var text by rememberSaveable { mutableStateOf(Fmt.input(initialAmount ?: profile.referenceAmount)) }
    val amount = Fmt.parse(text)
    val valid = amount != null && amount > 0 && amount <= NutritionCalculator.MAX_AMOUNT
    val needsConfirm = valid && amount!! > NutritionCalculator.CONFIRM_AMOUNT
    var confirmedLarge by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp).imePadding().navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            NumberField(
                value = text,
                onValueChange = { text = it; confirmedLarge = false },
                label = "Quantidade",
                suffix = profile.referenceUnit.symbol,
                isError = text.isNotEmpty() && !valid,
                supporting = when {
                    text.isNotEmpty() && !valid -> "Informe um valor entre 0 e ${Fmt.number(NutritionCalculator.MAX_AMOUNT, 0)} ${profile.referenceUnit.symbol}"
                    needsConfirm -> "Quantidade alta: confira antes de salvar."
                    else -> "Valores da fonte por ${Fmt.amount(profile.referenceAmount, profile.referenceUnit)}"
                },
                imeAction = ImeAction.Done,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            val consumed = if (valid) NutritionCalculator.consumed(profile, amount!!) else null
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                listOf(Nutrient.ENERGY, Nutrient.PROTEIN, Nutrient.CARBOHYDRATE, Nutrient.FAT).forEach { n ->
                    StatColumn(
                        value = consumed?.get(n)?.let { Fmt.value(n, it, detail = true, withUnit = n != Nutrient.ENERGY) } ?: "—",
                        label = if (n == Nutrient.ENERGY) "kcal" else n.labelPt.substringBefore(' '),
                    )
                }
            }
            extraActions?.invoke()
            Button(
                onClick = {
                    if (needsConfirm && !confirmedLarge) confirmedLarge = true else amount?.let(onConfirm)
                },
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (needsConfirm && !confirmedLarge) "Confirmar ${Fmt.amount(amount!!, profile.referenceUnit)}" else confirmLabel) }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
