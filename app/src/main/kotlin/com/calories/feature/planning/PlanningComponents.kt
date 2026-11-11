package com.calories.feature.planning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.calories.domain.model.NutritionGoal
import com.calories.domain.model.PlanFoodItem
import com.calories.domain.model.PlannedItem
import com.calories.domain.nutrition.MeasureUnit
import com.calories.domain.nutrition.Nutrient
import com.calories.domain.nutrition.NutrientProfile
import com.calories.domain.nutrition.NutrientTotals
import com.calories.domain.nutrition.NutrientValue
import com.calories.ui.components.MacroBar
import com.calories.ui.components.QuantitySheet
import com.calories.ui.components.SectionCard
import com.calories.ui.components.nutrientColor
import com.calories.ui.format.Fmt
import com.calories.ui.theme.TabularNumbers

/** Item de plano ou de dia planejado, no formato que as telas mostram. */
data class PlanRow(val id: Long, val foodId: Long, val name: String, val amount: Double, val unit: MeasureUnit, val energy: NutrientValue)

fun PlannedItem.toRow() = PlanRow(id, foodId, foodName, amount, unit, consumed[Nutrient.ENERGY] ?: NutrientValue.NotAvailable)
fun PlanFoodItem.toRow() = PlanRow(id, foodId, foodName, amount, unit, consumed[Nutrient.ENERGY] ?: NutrientValue.NotAvailable)

data class MenuAction(val label: String, val enabled: Boolean = true, val onClick: () -> Unit)

/** Cartão de uma refeição com seus alimentos planejados. */
@Composable
fun SlotGroupCard(
    title: String,
    totals: NutrientTotals,
    rows: List<PlanRow>,
    onAdd: () -> Unit,
    onRowClick: (PlanRow) -> Unit,
    menu: List<MenuAction> = emptyList(),
) {
    var open by remember { mutableStateOf(false) }
    SectionCard(padding = PaddingValues(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (rows.isEmpty()) "Nada planejado" else "${Fmt.total(Nutrient.ENERGY, totals[Nutrient.ENERGY], withUnit = false)} kcal",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Adicionar alimento em $title") }
            if (menu.isNotEmpty()) Box {
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "Mais opções de $title") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    menu.forEach { a -> DropdownMenuItem(text = { Text(a.label) }, enabled = a.enabled, onClick = { open = false; a.onClick() }) }
                }
            }
        }
        AnimatedVisibility(rows.isNotEmpty()) {
            Column(Modifier.padding(end = 12.dp)) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                rows.forEach { r ->
                    Row(Modifier.fillMaxWidth().clickable { onRowClick(r) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(Fmt.amount(r.amount, r.unit), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(Fmt.value(Nutrient.ENERGY, r.energy, detail = false), style = MaterialTheme.typography.bodyMedium.merge(TabularNumbers))
                    }
                }
            }
        }
    }
}

/** Energia e macros de um plano ou dia planejado em relação à meta. */
@Composable
fun PlanTotalsCard(totals: NutrientTotals, goal: NutritionGoal?, caption: String) {
    SectionCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(Fmt.total(Nutrient.ENERGY, totals[Nutrient.ENERGY], withUnit = false), style = MaterialTheme.typography.headlineMedium.merge(TabularNumbers))
            Text(" kcal $caption", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
        }
        goal?.let { g ->
            Text(
                "Meta ${Fmt.kcal(g.energyKcal)} kcal · ${Fmt.percent(totals.energyKcal / g.energyKcal)}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(
                Nutrient.PROTEIN to goal?.proteinG, Nutrient.CARBOHYDRATE to goal?.carbohydrateG,
                Nutrient.FAT to goal?.fatG, Nutrient.FIBER to goal?.fiberG,
            ).forEach { (n, g) ->
                val total = totals[n]
                MacroBar(
                    label = n.labelPt.substringBefore(' ').let { if (n == Nutrient.FAT) "Gorduras" else it },
                    consumedText = Fmt.total(n, total, withUnit = g == null),
                    consumed = total.sum, goal = g, goalText = g?.let { Fmt.nutrient(n, it) },
                    color = nutrientColor(n)!!,
                )
            }
        }
    }
}

/** Editar a quantidade de um item planejado (valores atuais do alimento). */
@Composable
fun PlanItemSheet(row: PlanRow, profile: NutrientProfile, onSave: (Double) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    QuantitySheet(
        title = row.name,
        subtitle = "Planejamento usa os valores atuais do alimento; o diário guarda uma cópia quando você registra.",
        profile = profile,
        initialAmount = row.amount,
        confirmLabel = "Salvar quantidade",
        onConfirm = onSave,
        onDismiss = onDismiss,
        extraActions = { TextButton(onClick = onDelete) { Text("Remover", color = MaterialTheme.colorScheme.error) } },
    )
}
