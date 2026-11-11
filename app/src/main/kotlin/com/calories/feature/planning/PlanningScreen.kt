package com.calories.feature.planning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard

/** Planejamento chega na Fase 4 (calendário, planos A/B/C, lista de compras). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanningRoute() {
    Scaffold(topBar = { TopAppBar(title = { Text("Planejamento") }) }, contentWindowInsets = WindowInsets(0)) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard {
                Icon(Icons.Outlined.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Em breve", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                InfoNote(
                    "Na próxima versão: calendário de refeições planejadas, planos reutilizáveis (ex.: Plano A — dias de treino, " +
                        "Plano B — descanso), copiar dias e gerar lista de compras consolidada.",
                    color = MaterialTheme.colorScheme.onSurface,
                )
                InfoNote("Hoje você já pode salvar refeições favoritas no dia (menu da refeição) e reaplicá-las ao adicionar alimentos.", Modifier.padding(top = 8.dp))
            }
        }
    }
}
