package com.calories.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutrientValue
import com.calories.ui.format.Fmt
import com.calories.ui.theme.LocalNutrientColors
import com.calories.ui.theme.TabularNumbers

@Composable
fun nutrientColor(n: Nutrient) = LocalNutrientColors.current.let {
    when (n) {
        Nutrient.PROTEIN -> it.protein
        Nutrient.CARBOHYDRATE -> it.carbohydrate
        Nutrient.FAT -> it.fat
        Nutrient.FIBER -> it.fiber
        else -> null
    }
}

/** Tabela de valores de um alimento (Tr, NA e "—" explicados na legenda). */
@Composable
fun NutrientTable(values: Map<Nutrient, NutrientValue>, modifier: Modifier = Modifier, detail: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        Nutrient.entries.forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            NutrientRow(n, Fmt.value(n, values[n] ?: NutrientValue.NotAvailable, detail))
        }
        val statuses = values.values.toSet()
        val legend = buildList {
            if (NutrientValue.Trace in statuses) add("Tr = traço (quantidade desprezível)")
            if (NutrientValue.NotApplicable in statuses) add("NA = não se aplica ao alimento")
            if (NutrientValue.NotAvailable in statuses || values.size < Nutrient.entries.size) add("— = sem dado na fonte")
        }
        if (legend.isNotEmpty()) InfoNote(legend.joinToString(" · "), Modifier.padding(top = 8.dp))
    }
}

@Composable
fun NutrientRow(nutrient: Nutrient, value: String, modifier: Modifier = Modifier) {
    val indent = nutrient == Nutrient.SATURATED_FAT
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (indent) Spacer(Modifier.width(16.dp))
        nutrientColor(nutrient)?.let { Dot(it); Spacer(Modifier.width(8.dp)) }
        Text(if (indent) "dos quais saturadas" else nutrient.labelPt, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
    }
}

/** Totais do dia com indicação de dados parciais. */
@Composable
fun TotalsTable(totals: NutrientTotals, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Nutrient.entries.forEachIndexed { i, n ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            NutrientRow(n, Fmt.total(n, totals[n]))
        }
        if (Nutrient.entries.any { totals[it].itemCount > 0 && !totals[it].isComplete && totals[it].hasAnyData }) {
            InfoNote("* parcial: algum alimento do dia não tem esse dado na fonte.", Modifier.padding(top = 8.dp))
        }
    }
}
