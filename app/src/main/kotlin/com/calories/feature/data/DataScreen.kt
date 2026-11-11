package com.calories.feature.data

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.EventNote
import androidx.compose.material.icons.outlined.PhonelinkSetup
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.TableChart
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.calories.feature.planning.Choice
import com.calories.ui.UiEvent
import com.calories.ui.components.InfoNote
import com.calories.ui.components.SectionCard
import kotlinx.coroutines.launch

private val OPEN_TYPES = arrayOf("application/json", "text/plain", "application/octet-stream")

/** Exportar CSV, backup JSON, restaurar, exportar/importar planos e transferir para outro aparelho. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataRoute(onBack: () -> Unit, viewModel: DataViewModel = hiltViewModel()) {
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var csvDialog by rememberSaveable { mutableStateOf(false) }
    var csvPeriod by rememberSaveable { mutableStateOf(CsvPeriod.ALL) }
    var csvExcel by rememberSaveable { mutableStateOf(true) }
    LaunchedEffect(Unit) { viewModel.events.collect { e -> if (e is UiEvent.Message) launch { snackbar.showSnackbar(e.text) } } }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let { viewModel.exportCsv(it, csvPeriod, csvExcel) } }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportBackup) }
    val plansLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri -> uri?.let(viewModel::exportPlans) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::inspectBackup) }
    val importPlansLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(viewModel::importPlans) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Dados e backup") },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar") } },
                )
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Exportar") {
                Action(Icons.Outlined.TableChart, "Histórico em planilha (CSV)", "Um alimento por linha, com data, refeição, quantidade e nutrientes", !busy) { csvDialog = true }
                HorizontalDivider()
                Action(Icons.Outlined.EventNote, "Exportar planos", "Planos e os alimentos personalizados que eles usam", !busy) { plansLauncher.launch(viewModel.fileName("planos", "json")) }
            }
            Section("Backup") {
                Action(Icons.Outlined.SaveAlt, "Fazer backup completo", "Diário, alimentos personalizados, metas, peso, planos, listas e ajustes", !busy) {
                    backupLauncher.launch(viewModel.fileName("backup", "json"))
                }
                HorizontalDivider()
                Action(Icons.Outlined.Restore, "Restaurar backup", "Substitui os dados deste aparelho pelos do arquivo", !busy) { restoreLauncher.launch(OPEN_TYPES) }
                HorizontalDivider()
                Action(Icons.Outlined.Upload, "Importar planos", "Acrescenta planos de um arquivo de planos ou de backup", !busy) { importPlansLauncher.launch(OPEN_TYPES) }
            }
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhonelinkSetup, contentDescription = null)
                    Text("Levar para outro aparelho", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                }
                InfoNote(
                    "1. Faça um backup completo e salve o arquivo (Downloads, pendrive ou computador).\n" +
                        "2. Instale o Calories no outro aparelho e copie o arquivo para lá (cabo USB, Quick Share).\n" +
                        "3. No aparelho novo: Ajustes → Dados e backup → Restaurar backup.\n" +
                        "A transferência de aparelho do Android (cabo ou Smart Switch) também copia os dados do app.",
                    Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurface,
                )
                InfoNote(
                    "Os arquivos ficam onde você escolher; o app não envia nada pela internet e não faz backup na nuvem. " +
                        "A biblioteca TACO/USDA não vai no backup: ela já vem com o app.",
                    Modifier.padding(top = 8.dp),
                )
            }
        }
    }

    if (csvDialog) {
        AlertDialog(
            onDismissRequest = { csvDialog = false },
            title = { Text("Exportar histórico") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Período", style = MaterialTheme.typography.labelLarge)
                    CsvPeriod.entries.forEach { p -> Choice(csvPeriod == p, p.label) { csvPeriod = p } }
                    Text("Formato", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                    Choice(csvExcel, "Excel em português (; e vírgula decimal)") { csvExcel = true }
                    Choice(!csvExcel, "Padrão internacional (, e ponto decimal)") { csvExcel = false }
                }
            },
            confirmButton = {
                TextButton(onClick = { csvDialog = false; csvLauncher.launch(viewModel.fileName("historico", "csv")) }) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(" Exportar")
                }
            },
            dismissButton = { TextButton(onClick = { csvDialog = false }) { Text("Cancelar") } },
        )
    }

    pending?.let { p ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("Restaurar este backup?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.check.exportedAt?.let { Text("Feito em ${it.take(16).replace('T', ' ')}", style = MaterialTheme.typography.bodyMedium) }
                    p.check.counts.filterValues { it > 0 }.forEach { (label, n) ->
                        Row { Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("$n", style = MaterialTheme.typography.bodySmall) }
                    }
                    Text(
                        "Todos os dados atuais deste aparelho (diário, alimentos personalizados, metas, peso, planos, listas e ajustes) serão substituídos. " +
                            "Se tiver dúvida, faça antes um backup do que está aqui.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("Restaurar", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
    SectionCard(padding = PaddingValues(vertical = 4.dp)) { content() }
}

@Composable
private fun Action(icon: ImageVector, title: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}
