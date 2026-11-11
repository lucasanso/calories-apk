package com.calories.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.calories.domain.model.ApplyMode
import com.calories.domain.model.DietPlan
import com.calories.domain.nutrition.Periods
import com.calories.ui.components.DateField
import com.calories.ui.components.InfoNote
import com.calories.ui.format.Fmt
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * Aplicar um plano no calendário. Com [singleDay], só escolhe o plano e o modo para [initialDate];
 * senão, intervalo de datas + dias da semana (nenhum marcado = todos).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApplyPlanDialog(
    plans: List<DietPlan>,
    fixedPlan: DietPlan?,
    initialDate: LocalDate,
    singleDay: Boolean,
    onApply: (planId: Long, dates: List<LocalDate>, mode: ApplyMode) -> Unit,
    onDismiss: () -> Unit,
) {
    var planId by remember { mutableStateOf(fixedPlan?.id ?: plans.firstOrNull()?.id) }
    var from by remember { mutableStateOf(initialDate) }
    var to by remember { mutableStateOf(initialDate.plusDays(6)) }
    var weekdays by remember { mutableStateOf(emptySet<DayOfWeek>()) }
    var mode by remember { mutableStateOf(ApplyMode.REPLACE) }
    val dates = when {
        singleDay -> listOf(initialDate)
        to.isBefore(from) -> emptyList()
        else -> Periods.select(from..to, weekdays)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (fixedPlan != null) "Aplicar “${fixedPlan.name}”" else "Aplicar plano") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (fixedPlan == null) {
                    if (plans.isEmpty()) InfoNote("Nenhum plano ainda. Crie um na aba Planos.", color = MaterialTheme.colorScheme.onSurface)
                    plans.forEach { p ->
                        Choice(planId == p.id, "${p.name} · ${Fmt.kcal(p.totals.energyKcal)} kcal") { planId = p.id }
                    }
                }
                if (singleDay) {
                    InfoNote("Para ${Fmt.fullDate(initialDate)}.", color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DateField("De", from, { from = it; if (to.isBefore(it)) to = it }, Modifier.weight(1f))
                        DateField("Até", to, { to = it }, Modifier.weight(1f))
                    }
                    Text("Dias da semana", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DayOfWeek.entries.forEach { d ->
                            FilterChip(
                                selected = d in weekdays,
                                onClick = { weekdays = if (d in weekdays) weekdays - d else weekdays + d },
                                label = { Text(Fmt.weekdayShort(d)) },
                            )
                        }
                    }
                    InfoNote("Nenhum dia marcado = todos os dias do intervalo.")
                }
                Text("Se já houver algo planejado", style = MaterialTheme.typography.labelLarge)
                Choice(mode == ApplyMode.REPLACE, "Substituir pelo plano") { mode = ApplyMode.REPLACE }
                Choice(mode == ApplyMode.APPEND, "Somar ao que já está planejado") { mode = ApplyMode.APPEND }
                if (!singleDay) {
                    InfoNote(
                        when {
                            dates.isEmpty() -> "Nenhuma data no intervalo escolhido."
                            dates.size > 366 -> "Escolha no máximo um ano."
                            else -> "Será aplicado em ${dates.size} dia(s)."
                        },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { planId?.let { onApply(it, dates, mode) } }, enabled = planId != null && dates.isNotEmpty() && dates.size <= 366) { Text("Aplicar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
internal fun Choice(selected: Boolean, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectable(selected, role = Role.RadioButton, onClick = onClick).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyMedium)
    }
}
